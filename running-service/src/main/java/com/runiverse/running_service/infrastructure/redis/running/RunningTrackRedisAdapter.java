package com.runiverse.running_service.infrastructure.redis.running;

import com.runiverse.running_service.application.running.port.out.AppendRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class RunningTrackRedisAdapter implements AppendRunningTrackPort {

    // 커서를 읽고 쓰는 사이에 재연결 배치가 끼면 중복이 샌다 - 한 덩어리로 실행한다.
    // 좌표 값은 건드리지 않고 순번만 숫자로 읽어 정밀도가 흔들릴 여지를 없앤다
    private static final RedisScript<Long> APPEND = RedisScript.of("""
            local last = tonumber(redis.call('GET', KEYS[2]) or '-1')
            local fresh = {}
            for i = 2, #ARGV, 2 do
                local sequence = tonumber(ARGV[i])
                if sequence > last then
                    fresh[#fresh + 1] = ARGV[i + 1]
                    last = sequence
                end
            end
            if #fresh == 0 then
                return 0
            end
            redis.call('XADD', KEYS[1], '*', 'points', '[' .. table.concat(fresh, ',') .. ']')
            redis.call('SET', KEYS[2], tostring(last))
            redis.call('EXPIRE', KEYS[1], ARGV[1])
            redis.call('EXPIRE', KEYS[2], ARGV[1])
            return #fresh
            """, Long.class);
    private final StringRedisTemplate redisTemplate;
    private final RunningTrackProperties properties;

    @Override
    public int append(Long runningRoomId, UserId userId, List<TrackPoint> points) {
        List<String> args = new ArrayList<>(points.size() * 2 + 1);
        args.add(String.valueOf(properties.ttl().toSeconds()));
        // 순번 오름차순이어야 Lua가 마지막 값 하나로 커서를 옮길 수 있다
        points.stream()
                .sorted(Comparator.comparingLong(TrackPoint::sequence))
                .forEach(point -> {
                    args.add(String.valueOf(point.sequence()));
                    args.add(compact(point));
                });
        Long appended = redisTemplate.execute(
                APPEND,
                List.of(trackKey(runningRoomId, userId), sequenceKey(runningRoomId, userId)),
                args.toArray());
        return appended == null ? 0 : appended.intValue();
    }

    // 필드명을 빼고 배열로 적는다 - 좌표 하나가 230B에서 60B가 된다.
    // 좌표는 소수점 5자리(약 1m)로 자른다: GPS 실측 오차가 수 m라 그 아래는 노이즈고,
    // route_polyline도 precision 5라 다운샘플 때 정밀도가 어긋나지 않는다(erd.md)
    private String compact(TrackPoint point) {
        return String.format(
                Locale.ROOT,
                "[%d,%.5f,%.5f,%s,%.1f,%s,%s,%s,%s,%d]",
                point.sequence(),
                point.latitude(),
                point.longitude(),
                nullable(point.altitudeMeters(), "%.1f"),
                point.accuracyMeters(),
                nullable(point.speedMetersPerSecond(), "%.2f"),
                nullable(point.headingDegrees(), "%.1f"),
                nullable(point.cadenceSpm(), "%d"),
                nullable(point.currentPaceSecondsPerKm(), "%d"),
                point.recordedAt().atZone(ZoneId.systemDefault()).toEpochSecond());
    }

    // 못 잰 값은 JSON null로 적어 자리를 지킨다 — 배열이라 칸을 비우면 뒤가 밀린다.
    // %.2f에 null을 넘기면 "null"이 정밀도에 잘려 "nu"가 되므로 %s로 받는다
    private static String nullable(Number value, String format) {
        return value == null ? "null" : String.format(Locale.ROOT, format, value);
    }

    private String trackKey(Long runningRoomId, UserId userId) {
        return RedisKey.RUNNING_TRACK.of(String.valueOf(runningRoomId), userId.value().toString());
    }

    private String sequenceKey(Long runningRoomId, UserId userId) {
        return RedisKey.RUNNING_TRACK.of(
                String.valueOf(runningRoomId), userId.value().toString(), "seq");
    }
}
