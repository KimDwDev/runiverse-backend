package com.runiverse.running_service.application.running.common;

import com.runiverse.running_service.application.running.port.out.RunningTrack;

import java.math.BigDecimal;

// 끝내기 판정(잠금 안)의 결과 — 봉인과 확정은 이것만 보고 움직인다
public sealed interface FinishJudgement {

    // 이미 확정된 참가자 — 기록을 덮어쓰지 않고 ack만 다시 보낸다
    record AlreadyFinished() implements FinishJudgement {
    }

    // 확정 거리가 목표에 못 미쳐 확정하지 않았다
    record Pending(int remainingMeters) implements FinishJudgement {
    }

    // 확정해도 된다. 봉인은 이 스냅숏만 쓴다 — 판정 뒤에 들어온 좌표를 다시 읽으면
    // 올린 원본·분석·저장한 기록이 서로 다른 트랙에서 나온다.
    // 목표 거리는 방이 시작된 뒤 바뀌지 않아 잠금 밖에서 분석해도 결과가 같다(D-82)
    record Reached(RunningTrack track, FilteredTrack filtered, int analysisTargetMeters,
                   BigDecimal weightKg) implements FinishJudgement {
    }
}
