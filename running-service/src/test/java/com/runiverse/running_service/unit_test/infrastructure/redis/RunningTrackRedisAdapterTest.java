package com.runiverse.running_service.unit_test.infrastructure.redis;

import com.github.f4b6a3.uuid.UuidCreator;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.running.RunningTrackProperties;
import com.runiverse.running_service.infrastructure.redis.running.RunningTrackRedisAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.Invocation;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;

@ExtendWith(MockitoExtension.class)
@DisplayName("러닝 트랙 Redis 어댑터 단위 테스트")
class RunningTrackRedisAdapterTest {

    private static final long ROOM_ID = 125L;
    private static final Duration TTL = Duration.ofHours(6);
    private static final LocalDateTime RECORDED_AT = LocalDateTime.of(2026, 7, 25, 19, 10, 30);
    // 저장 포맷은 시각을 epoch 초로 적는다 — 구현과 같은 기준으로 계산해 둔다
    private static final long EPOCH_SECOND =
            RECORDED_AT.atZone(ZoneId.systemDefault()).toEpochSecond();

    @Mock
    private StringRedisTemplate redisTemplate;

    private RunningTrackRedisAdapter adapter;
    private UserId userId;

    @BeforeEach
    void setUp() {
        adapter = new RunningTrackRedisAdapter(redisTemplate, new RunningTrackProperties(TTL));
        userId = new UserId(UuidCreator.getTimeOrderedEpoch());
    }

    // 단말이 모두 측정한 좌표
    private static TrackPoint point(long sequence) {
        return new TrackPoint(
                sequence, 35.17955, 129.07564, 6.2, 18.4, 2.8, 85.3, 165, 345, RECORDED_AT);
    }

    // 고도·속도·방위·케이던스·페이스를 못 잰 좌표 — Location.isValid()가 막지 않는 조합이다
    private static TrackPoint pointWithoutOptionalFields(long sequence) {
        return new TrackPoint(
                sequence, 35.17955, 129.07564, 6.2, null, null, null, null, null, RECORDED_AT);
    }

    // execute(script, keys, args...)는 가변 인자라 매처로 잡기 까다롭다 — 실제 호출을 직접 읽는다
    private Invocation execution() {
        return mockingDetails(redisTemplate).getInvocations().stream()
                .filter(invocation -> "execute".equals(invocation.getMethod().getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("execute가 호출되지 않았다"));
    }

    @SuppressWarnings("unchecked")
    private List<String> scriptKeys() {
        return (List<String>) execution().getRawArguments()[1];
    }

    // [TTL, 순번, 좌표, 순번, 좌표, ...] 순으로 실린다
    private Object[] scriptArgs() {
        return (Object[]) execution().getRawArguments()[2];
    }

    @Test
    @DisplayName("좌표 본체 키와 커서 키를 함께 넘긴다")
    void usesTrackAndCursorKeys() {
        // when
        adapter.append(ROOM_ID, userId, List.of(point(0)));

        // then -> 스크립트가 커서를 읽고 쓰려면 두 키가 다 있어야 한다
        assertThat(scriptKeys()).containsExactly(
                "running:track:" + ROOM_ID + ":" + userId.value(),
                "running:track:" + ROOM_ID + ":" + userId.value() + ":seq");
    }

    @Test
    @DisplayName("첫 인자로 TTL 초를 넘긴다")
    void passesTtlSecondsFirst() {
        // when
        adapter.append(ROOM_ID, userId, List.of(point(0)));

        // then -> 스크립트가 두 키의 EXPIRE에 쓴다
        assertThat(scriptArgs()[0]).isEqualTo("21600");
    }

    @Test
    @DisplayName("좌표를 필드명 없는 배열 문자열로 압축한다")
    void compactsPointIntoArray() {
        // when
        adapter.append(ROOM_ID, userId, List.of(point(7)));

        // then -> [순번,위도,경도,고도,정확도,속도,방위,케이던스,페이스,시각]
        assertThat(scriptArgs()[1]).isEqualTo("7");
        assertThat(scriptArgs()[2]).isEqualTo(
                "[7,35.17955,129.07564,18.4,6.2,2.80,85.3,165,345,%d]".formatted(EPOCH_SECOND));
    }

    @Test
    @DisplayName("단말이 못 잰 값은 잘리지 않은 null로 적어 자리를 지킨다")
    void writesNullForMissingValues() {
        // given -> %.2f에 null을 넘기면 "null"이 정밀도에 잘려 "nu"가 된다.
        // 그러면 저장 문자열이 JSON이 아니게 되고 뒤 값의 자리도 밀린다

        // when
        adapter.append(ROOM_ID, userId, List.of(pointWithoutOptionalFields(0)));

        // then -> 값이 없다는 사실이 남아야 읽는 쪽이 표본에서 제외할 수 있다(erd.md avg_cadence)
        assertThat(scriptArgs()[2]).isEqualTo(
                "[0,35.17955,129.07564,null,6.2,null,null,null,null,%d]".formatted(EPOCH_SECOND));
    }

    @Test
    @DisplayName("순번이 뒤섞여 들어와도 오름차순으로 실어 보낸다")
    void sortsPointsBySequence() {
        // given -> 스크립트가 커서를 앞으로만 밀기 때문에 역순이면 뒤 좌표가 버려진다

        // when
        adapter.append(ROOM_ID, userId, List.of(point(2), point(0), point(1)));

        // then
        Object[] args = scriptArgs();
        assertThat(List.of(args[1], args[3], args[5])).containsExactly("0", "1", "2");
    }

    @Test
    @DisplayName("스크립트가 아무것도 돌려주지 않으면 0으로 본다")
    void returnsZeroWhenScriptReturnsNothing() {
        // given -> 전부 중복이라 스크립트가 일찍 빠져나온 경우

        // when
        int appended = adapter.append(ROOM_ID, userId, List.of(point(0)));

        // then
        assertThat(appended).isZero();
    }
}
