package com.runiverse.running_service.support;

import com.runiverse.running_service.application.running.common.PolylineDecoder;
import com.runiverse.running_service.application.running.common.PolylineEncoder;
import com.runiverse.running_service.domain.running.record.RunningRecord;

// 기록의 경로 조각을 이어 한 줄 polyline으로 만든다 — 목록·카드가 내리는 모양이다.
// encoded polyline은 앞 점과의 차로 인코딩돼 문자열을 그대로 이으면 깨지므로 풀었다 다시 인코딩한다
public final class SegmentRoutes {

    private SegmentRoutes() {
    }

    public static String joinedPolyline(RunningRecord record) {
        return PolylineEncoder.encodePoints(record.getSegments().stream()
                .flatMap(segment -> PolylineDecoder.decode(segment.getRoutePolyline().value()).stream())
                .toList());
    }
}
