package com.runiverse.running_service.application.running.query.record;

import com.runiverse.running_service.application.running.common.PolylineDecoder;
import com.runiverse.running_service.application.running.common.PolylineEncoder;
import com.runiverse.running_service.application.running.port.in.GetMyRunningRecordsUsecase;
import com.runiverse.running_service.application.running.port.out.LoadMyRunningRecordsPort;
import com.runiverse.running_service.application.running.port.out.RunningRecordRow;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetMyRunningRecordsHandler implements GetMyRunningRecordsUsecase {

    private final LoadMyRunningRecordsPort loadMyRunningRecordsPort;

    @Override
    public GetMyRunningRecordsResult handle(GetMyRunningRecordsQuery query) {
        LocalDateTime startInclusive = query.from().atStartOfDay();
        LocalDateTime endExclusive = query.to().plusDays(1).atStartOfDay();

        List<RunningRecordRow> rows = loadMyRunningRecordsPort.loadByPeriod(
                new UserId(query.userId()), startInclusive, endExclusive);

        return new GetMyRunningRecordsResult(rows.stream()
                .map(this::toRunningRecord)
                .toList());
    }

    private GetMyRunningRecordsResult.RunningRecord toRunningRecord(RunningRecordRow row) {
        return new GetMyRunningRecordsResult.RunningRecord(
                row.runningRecordId(),
                row.runningRoomId(),
                row.type(),
                row.playerCount(),
                row.startedAt(),
                row.totalDistanceMeters(),
                row.totalDurationSeconds(),
                row.averagePaceSecondsPerKm(),
                row.totalElevationGainMeters(),
                joinedPolyline(row.segmentPolylines()));
    }

    // 카드는 한 줄 미리보기다 — 조각을 이어 다시 인코딩한다.
    // encoded polyline은 앞 점과의 차로 인코딩돼 문자열을 그대로 이어 붙이면 깨진다
    private static String joinedPolyline(List<String> segmentPolylines) {
        return PolylineEncoder.encodePoints(segmentPolylines.stream()
                .flatMap(segment -> PolylineDecoder.decode(segment).stream())
                .toList());
    }
}
