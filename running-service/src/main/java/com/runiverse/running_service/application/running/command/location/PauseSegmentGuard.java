package com.runiverse.running_service.application.running.command.location;

import com.runiverse.running_service.application.running.exception.PauseSegmentDecreasedException;
import com.runiverse.running_service.application.running.port.out.RunningDistance;
import com.runiverse.running_service.application.running.port.out.TrackPoint;

import java.util.Comparator;
import java.util.List;

// 일시정지 구간 번호는 순번을 따라 줄지 않는다 — 줄면 일시정지가 아닌 칸이 일시정지로 빠지거나,
// 일시정지 칸이 거리로 들어간다. 클라 로컬 트랙이 깨졌다는 뜻이라 배치 전체를 거부한다
public final class PauseSegmentGuard {

    private PauseSegmentGuard() {
    }

    public static void validate(RunningDistance stored, List<TrackPoint> points) {
        List<TrackPoint> ordered = points.stream()
                .sorted(Comparator.comparingLong(TrackPoint::sequence))
                .toList();
        // 배치 안에서 순번 순으로 놓았을 때 줄면 안 된다
        for (int i = 1; i < ordered.size(); i++) {
            if (ordered.get(i).pauseSegment() < ordered.get(i - 1).pauseSegment()) {
                throw new PauseSegmentDecreasedException();
            }
        }
        // 마지막으로 반영한 좌표보다 뒤 순번이 그 좌표의 번호보다 작으면 안 된다.
        // 그 앞을 메우는 재전송분은 앞뒤 이웃이 이미 반영돼 비교할 기준이 없다
        for (TrackPoint point : ordered) {
            if (point.sequence() > stored.lastSequence()
                    && point.pauseSegment() < stored.lastPauseSegment()) {
                throw new PauseSegmentDecreasedException();
            }
        }
    }
}
