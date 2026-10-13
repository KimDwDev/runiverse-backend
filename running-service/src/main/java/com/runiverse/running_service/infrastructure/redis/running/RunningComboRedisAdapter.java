package com.runiverse.running_service.infrastructure.redis.running;

import com.runiverse.running_service.application.running.port.out.LoadRunningComboPairsPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningComboSnapshotsPort;
import com.runiverse.running_service.application.running.port.out.MarkRunningComboPausedPort;
import com.runiverse.running_service.application.running.port.out.RunningComboPair;
import com.runiverse.running_service.application.running.port.out.RunningComboSegment;
import com.runiverse.running_service.application.running.port.out.RunningComboSnapshot;
import com.runiverse.running_service.application.running.port.out.SaveRunningComboPairsPort;
import com.runiverse.running_service.application.running.port.out.SaveRunningComboSegmentsPort;
import com.runiverse.running_service.application.running.port.out.SaveRunningComboSnapshotPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.metrics.running.RunningStateMeter;
import com.runiverse.running_service.infrastructure.metrics.running.RunningStateTarget;
import com.runiverse.running_service.infrastructure.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.runiverse.running_service.infrastructure.metrics.running.RunningStatePhase.RUNNING;
import static com.runiverse.running_service.infrastructure.metrics.running.RunningStateStore.REDIS;
import static com.runiverse.running_service.infrastructure.metrics.running.RunningStateTarget.COMBO_PAIR;
import static com.runiverse.running_service.infrastructure.metrics.running.RunningStateTarget.COMBO_SEGMENT;
import static com.runiverse.running_service.infrastructure.metrics.running.RunningStateTarget.COMBO_SNAPSHOT;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunningComboRedisAdapter implements
        LoadRunningComboSnapshotsPort,
        SaveRunningComboSnapshotPort,
        LoadRunningComboPairsPort,
        SaveRunningComboPairsPort,
        SaveRunningComboSegmentsPort,
        MarkRunningComboPausedPort {

    private final StringRedisTemplate redisTemplate;
    // 콤보 상태는 좌표 버퍼와 같은 러닝 하나의 수명을 산다 — 러닝이 끝나면 함께 사라진다
    private final RunningTrackProperties properties;
    private final RunningStateMeter stateMeter;
    // 방마다 해시 셋이다.
    // 스냅샷: userId → 누적거리|기준시각|속도|의심점갱신시각|관측시각, userId:paused → 일시정지시각
    // 관계: first|second → 시작시각|쌓인시각|최고콤보|남은봐주기
    // 구간: first|second|시작시각 → 끝시각|유지횟수
    private static final String SEPARATOR = "|";
    private static final String SEPARATOR_PATTERN = "\\|";
    private static final String NULL = "null";
    // 일시정지 표시는 스냅샷과 같은 해시의 별도 칸이다 — 판정이 HGETALL 한 번으로 함께 읽는다
    private static final String PAUSED_SUFFIX = ":paused";
    private static final int SNAPSHOT_FIELD_COUNT = 5;
    // 갱신·관측 시각을 싣기 전 포맷. 배포 순간 뛰고 있던 러닝이 TTL 안에 남아 있다 —
    // 기준 시각으로 채워 달리는 중으로 본다. 비우면 그 사람들의 콤보가 배포와 함께 모두 보류된다
    private static final int LEGACY_SNAPSHOT_FIELD_COUNT = 3;
    private static final int PAIR_FIELD_COUNT = 4;
    // 쌓인 시각을 싣기 전 포맷 — 형식 불일치로 버리면 살아 있던 콤보와 최고 기록이 처음부터 다시 시작한다
    private static final int LEGACY_PAIR_FIELD_COUNT = 3;
    private static final int PAIR_USER_COUNT = 2;

    @Override
    public List<RunningComboSnapshot> loadSnapshots(Long runningRoomId) {
        Map<String, String> entries = entries(snapshotKey(runningRoomId), COMBO_SNAPSHOT, runningRoomId);
        Set<String> paused = entries.keySet().stream()
                .filter(field -> field.endsWith(PAUSED_SUFFIX))
                .map(field -> field.substring(0, field.length() - PAUSED_SUFFIX.length()))
                .collect(Collectors.toSet());
        // 스냅샷 없이 일시정지 표시만 있는 참가자는 아직 판정에 들어온 적이 없다 — 비교 대상이 아니다
        return entries.entrySet().stream()
                .filter(entry -> !entry.getKey().endsWith(PAUSED_SUFFIX))
                .map(entry -> toSnapshot(entry, paused.contains(entry.getKey())))
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public void saveSnapshot(Long runningRoomId, RunningComboSnapshot snapshot) {
        // 일시정지 여부는 싣지 않는다 — 별도 칸이라 일시정지 이벤트와 같은 값을 고쳐 쓰지 않는다
        String value = String.format(Locale.ROOT, "%.2f|%d|%.3f|%s|%s",
                snapshot.meters(),
                snapshot.recordedAt().toEpochMilli(),
                snapshot.speedMetersPerSecond(),
                nullable(snapshot.movedAt()),
                nullable(snapshot.observedAt()));
        save(snapshotKey(runningRoomId), Map.of(snapshot.userId().value().toString(), value),
                COMBO_SNAPSHOT, runningRoomId);
    }

    // 잡지 않고 던진다 — 부르는 쪽이 로그를 남긴다
    @Override
    public void markPaused(Long runningRoomId, UserId userId) {
        String key = snapshotKey(runningRoomId);
        stateMeter.write(REDIS, COMBO_SNAPSHOT, RUNNING, runningRoomId, () -> {
            redisTemplate.<String, String>opsForHash().put(
                    key, pausedField(userId), String.valueOf(Instant.now().toEpochMilli()));
            redisTemplate.expire(key, properties.ttl());
        });
    }

    // 잡지 않고 던진다 — 판정이 이번 배치를 건너뛰고 다음 배치가 다시 지운다
    @Override
    public void clearPaused(Long runningRoomId, UserId userId) {
        stateMeter.write(REDIS, COMBO_SNAPSHOT, RUNNING, runningRoomId,
                () -> redisTemplate.<String, String>opsForHash().delete(
                        snapshotKey(runningRoomId), pausedField(userId)));
    }

    @Override
    public List<RunningComboPair> loadPairs(Long runningRoomId) {
        return parse(entries(pairKey(runningRoomId), COMBO_PAIR, runningRoomId), this::toPair);
    }

    @Override
    public void savePairs(Long runningRoomId, List<RunningComboPair> pairs) {
        if (pairs.isEmpty()) {
            return;   // 아직 아무와도 겹치지 않은 참가자 — 저장할 상태가 없다
        }
        save(pairKey(runningRoomId),
                pairs.stream().collect(Collectors.toMap(
                        RunningComboRedisAdapter::pairField, RunningComboRedisAdapter::pairValue)),
                COMBO_PAIR, runningRoomId);
    }

    // 잡지 않고 던진다 — 구간을 못 남긴 채 관계가 끊긴 상태로 저장되면 그 구간은 되살릴 수 없다.
    // 판정 쪽이 관계 저장을 건너뛰고 로그를 남긴다
    @Override
    public void saveSegments(Long runningRoomId, List<RunningComboSegment> segments) {
        if (segments.isEmpty()) {
            return;   // 이번 판정에서 끊긴 관계가 없다 — 대부분의 배치가 여기서 끝난다
        }
        String key = segmentKey(runningRoomId);
        Map<String, String> entries = segments.stream().collect(Collectors.toMap(
                RunningComboRedisAdapter::segmentField, RunningComboRedisAdapter::segmentValue,
                // 한 판정에서 같은 관계가 두 번 끊기지는 않지만, 겹쳐도 같은 값이다
                (kept, ignored) -> kept));
        stateMeter.write(REDIS, COMBO_SEGMENT, RUNNING, runningRoomId, () -> {
            redisTemplate.<String, String>opsForHash().putAll(key, entries);
            redisTemplate.expire(key, properties.ttl());
        });
    }

    // 읽기 실패를 빈 목록으로 위장하면 안 된다 — 비교 상대가 사라져
    // 살아 있던 콤보와 최고 기록이 이번 배치의 저장으로 지워진다.
    // 그대로 던지고 로그는 받는 쪽이 남긴다
    private Map<String, String> entries(String key, RunningStateTarget target, Long runningRoomId) {
        return stateMeter.read(REDIS, target, RUNNING, runningRoomId,
                () -> redisTemplate.<String, String>opsForHash().entries(key));
    }

    private <T> List<T> parse(Map<String, String> entries,
                              Function<Map.Entry<String, String>, Optional<T>> mapper) {
        return entries.entrySet().stream()
                .map(mapper)
                .flatMap(Optional::stream)
                .toList();
    }

    private void save(String key, Map<String, String> entries, RunningStateTarget target,
                      Long runningRoomId) {
        try {
            // HSET과 EXPIRE를 한 건으로 잰다 — DB라면 UPDATE 한 번일 일이다
            stateMeter.write(REDIS, target, RUNNING, runningRoomId, () -> {
                redisTemplate.<String, String>opsForHash().putAll(key, entries);
                // HSET은 TTL을 건드리지 않는다 — 쓸 때마다 다시 걸지 않으면
                // 처음 만든 시각으로부터 만료돼 러닝 도중에 방 전체가 통째로 사라진다
                redisTemplate.expire(key, properties.ttl());
            });
        } catch (RuntimeException e) {
            // 저장에 실패하면 다음 배치가 이전 상태에서 이어 판정한다
            log.error("[러닝] 콤보 상태 저장 실패: Redis 오류 - roomId={}, target={}",
                    runningRoomId, target.name().toLowerCase(Locale.ROOT), e);
        }
    }

    private Optional<RunningComboSnapshot> toSnapshot(Map.Entry<String, String> entry, boolean paused) {
        String[] fields = entry.getValue().split(SEPARATOR_PATTERN);
        if (fields.length != SNAPSHOT_FIELD_COUNT && fields.length != LEGACY_SNAPSHOT_FIELD_COUNT) {
            log.warn("[러닝] 콤보 스냅샷 복원 건너뜀: 저장 형식 불일치 - field={}", entry.getKey());
            return Optional.empty();
        }
        try {
            Instant recordedAt = Instant.ofEpochMilli(Long.parseLong(fields[1]));
            boolean legacy = fields.length == LEGACY_SNAPSHOT_FIELD_COUNT;
            return Optional.of(new RunningComboSnapshot(
                    new UserId(UUID.fromString(entry.getKey())),
                    Double.parseDouble(fields[0]),
                    recordedAt,
                    Double.parseDouble(fields[2]),
                    legacy ? recordedAt : toInstant(fields[3]),
                    legacy ? recordedAt : toInstant(fields[4]),
                    paused));
        } catch (IllegalArgumentException e) {
            // 깨진 값은 다시 읽어도 같다 — 이 참가자만 비교에서 빼고 다음 배치가 제 값으로 덮는다
            log.warn("[러닝] 콤보 스냅샷 복원 건너뜀: 저장 값 손상 - field={}", entry.getKey());
            return Optional.empty();
        }
    }

    private Optional<RunningComboPair> toPair(Map.Entry<String, String> entry) {
        String[] users = entry.getKey().split(SEPARATOR_PATTERN);
        String[] fields = entry.getValue().split(SEPARATOR_PATTERN);
        if (users.length != PAIR_USER_COUNT
                || (fields.length != PAIR_FIELD_COUNT && fields.length != LEGACY_PAIR_FIELD_COUNT)) {
            log.warn("[러닝] 콤보 관계 복원 건너뜀: 저장 형식 불일치 - field={}", entry.getKey());
            return Optional.empty();
        }
        // 옛 포맷에는 쌓인 시각 칸이 없다 — 다음 겹침 판정이 채운다
        boolean legacy = fields.length == LEGACY_PAIR_FIELD_COUNT;
        int shift = legacy ? 0 : 1;
        try {
            return Optional.of(new RunningComboPair(
                    new UserId(UUID.fromString(users[0])),
                    new UserId(UUID.fromString(users[1])),
                    toInstant(fields[0]),
                    legacy ? null : toInstant(fields[1]),
                    Integer.parseInt(fields[1 + shift]),
                    Integer.parseInt(fields[2 + shift])));
        } catch (IllegalArgumentException e) {
            // 이 관계만 처음부터 다시 센다 — 거리를 틀리게 세느니 1부터가 낫다
            log.warn("[러닝] 콤보 관계 복원 건너뜀: 저장 값 손상 - field={}", entry.getKey());
            return Optional.empty();
        }
    }

    private static String pausedField(UserId userId) {
        return userId.value() + PAUSED_SUFFIX;
    }

    private static String pairField(RunningComboPair pair) {
        // 정렬은 RunningComboPair가 보장한다 — A-B와 B-A가 같은 칸을 가리킨다
        return pair.first().value() + SEPARATOR + pair.second().value();
    }

    private static String pairValue(RunningComboPair pair) {
        return nullable(pair.startedAt()) + SEPARATOR
                + nullable(pair.accruedAt()) + SEPARATOR
                + pair.maxComboCount() + SEPARATOR
                + pair.missLeft();
    }

    // 시작 시각까지 칸 이름에 넣는다 — 같은 쌍의 두 사람이 다른 서버에서 함께 끊어도 같은 칸을 덮어쓴다
    private static String segmentField(RunningComboSegment segment) {
        return segment.first().value() + SEPARATOR
                + segment.second().value() + SEPARATOR
                + segment.startAt().toEpochMilli();
    }

    private static String segmentValue(RunningComboSegment segment) {
        return segment.endAt().toEpochMilli() + SEPARATOR + segment.comboCount();
    }

    // 끊긴 관계는 시작 시각이 없고, 한 번도 움직이지 않은 참가자는 갱신 시각이 없다
    private static String nullable(Instant instant) {
        return instant == null ? NULL : String.valueOf(instant.toEpochMilli());
    }

    private static Instant toInstant(String value) {
        return NULL.equals(value) ? null : Instant.ofEpochMilli(Long.parseLong(value));
    }

    private String snapshotKey(Long runningRoomId) {
        return RedisKey.RUNNING_COMBO.of(String.valueOf(runningRoomId), "dist");
    }

    private String pairKey(Long runningRoomId) {
        return RedisKey.RUNNING_COMBO.of(String.valueOf(runningRoomId), "pair");
    }

    private String segmentKey(Long runningRoomId) {
        return RedisKey.RUNNING_COMBO.of(String.valueOf(runningRoomId), "seg");
    }
}
