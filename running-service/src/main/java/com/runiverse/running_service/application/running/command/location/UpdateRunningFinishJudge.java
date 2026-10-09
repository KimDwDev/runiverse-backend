package com.runiverse.running_service.application.running.command.location;

import com.runiverse.running_service.application.running.common.RunningFinisher;
import com.runiverse.running_service.domain.common.vo.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpdateRunningFinishJudge {

    private final RunningFinisher runningFinisher;

    // 위치 배치마다 목표 도달을 판정하고, 채웠으면 RUNNING_FINISH를 기다리지 않고 끝낸다.
    // 콤보 판정과 달리 실패를 삼키지 않는다 — 클라가 ERROR를 받아야 하고,
    // 누적이 목표 위에 있는 한 다음 배치가 다시 시도한다
    public boolean judge(Long runningRoomId, UserId userId, Integer targetDistanceMeters, double meters) {
        // 솔로 방은 목표가 없다 — 사용자가 끝내야 끝난다
        if (targetDistanceMeters == null || meters < targetDistanceMeters) {
            return false;
        }
        // 누적이 목표에 닿아도 확정 거리로 다시 확인한다 — 못 미치면 아무것도 보내지 않고 다음 배치가 다시 본다.
        // 이미 끝났으면 종료가 멱등이라 트랙만 정리하고 true다
        return runningFinisher.finishOnGoal(runningRoomId, userId.value());
    }
}
