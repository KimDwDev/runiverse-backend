package com.runiverse.e2e.user;

import com.runiverse.e2e.E2eTestSupport;
import com.runiverse.e2e.SseStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("배포 이미지 대상 유저 스트림 E2E 테스트")
class UserStreamE2eTest extends E2eTestSupport {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");   // 호스트 시간대로 만들면 슬롯이 어긋난다
    // 다른 매칭 테스트와 한 방에 합쳐지지 않게 그쪽이 쓰지 않는 슬롯·거리를 쓴다
    private static final LocalDateTime SLOT = LocalDate.now(APP_ZONE).plusDays(1).atTime(21, 30);
    private static final int DISTANCE = 5000;

    @Test
    @DisplayName("활성 신청이 없어도 연결되고, 연결 직후 2-2와 같은 IDLE 상태를 받는다")
    void idleUserReceivesStatusSnapshot() {
        // given
        TestUser user = signUpAndOnboard();

        // when
        try (SseStream stream = openUserStream(user.accessToken())) {
            // then -> 매칭 스트림과 달리 거절하지 않는다
            assertThat(stream.status()).isEqualTo(200);
            Map<String, Object> snapshot = stream.await("USER_STATUS");
            assertThat(snapshot.get("status")).isEqualTo("IDLE");
            assertThat(snapshot.get("runningRoomId")).isNull();
        }
        assertThat(get("/users/me/status", user.accessToken()).text("status")).isEqualTo("IDLE");
    }

    @Test
    @DisplayName("매칭 대기 중이면 방 값을 실은 WAITING을 받고, 매칭 스트림과 함께 열려 있다")
    void waitingUserKeepsBothStreams() {
        // given
        TestUser user = signUpAndOnboard();
        Response applied = post("/match-rooms", Map.of(
                "scheduledStartAt", SLOT.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "targetDistanceMeters", DISTANCE
        ), user.accessToken());
        assertThat(applied.status()).isEqualTo(201);
        int runningRoomId = applied.number("runningRoomId");

        try (SseStream userStream = openUserStream(user.accessToken());
             SseStream matchStream = openMatchStream(user.accessToken())) {
            // when
            Map<String, Object> snapshot = userStream.await("USER_STATUS");

            // then -> 2-2와 같은 판정이다
            assertThat(snapshot.get("status")).isEqualTo("WAITING");
            assertThat(snapshot.get("type")).isEqualTo("MATCH");
            assertThat(((Number) snapshot.get("runningRoomId")).intValue()).isEqualTo(runningRoomId);
            assertThat(snapshot.get("targetDistanceMeters")).isEqualTo(DISTANCE);
            // 레지스트리가 따로라 한쪽 연결이 다른 쪽을 밀어내지 않는다
            assertThat(((Number) matchStream.await("MATCH_ROOM_UPDATED").get("runningRoomId")).intValue())
                    .isEqualTo(runningRoomId);
        } finally {
            assertThat(post("/match-rooms/leave", null, user.accessToken()).status()).isEqualTo(204);
        }
    }

    @Test
    @DisplayName("같은 사용자가 다시 연결하면 이전 스트림은 서버가 닫고 새 스트림만 남는다")
    void reconnectClosesPreviousStream() {
        // given -> 앱 재시작처럼 옛 연결이 끊긴 줄 모르고 남아 있다
        TestUser user = signUpAndOnboard();

        try (SseStream first = openUserStream(user.accessToken())) {
            first.await("USER_STATUS");

            // when
            try (SseStream second = openUserStream(user.accessToken())) {
                // then -> 새 연결은 스냅샷을 받고, 옛 연결은 끊긴다
                assertThat(second.await("USER_STATUS").get("status")).isEqualTo("IDLE");
                first.awaitClosed();
            }
        }
    }
}
