package com.runiverse.running_service.application.running.port.out;

import java.util.List;
import java.util.UUID;

// 한 참가자의 확정 기록 경로와, 경계점마다 시각을 붙일 구간 시각들
public record RecordedRoute(
        UUID userId,
        String routePolyline,
        // 구간 번호 순이다
        List<RecordedSplitTime> splits
) {

}
