package com.runiverse.running_service.application.running.common;

// 확정 거리로 낸 종료 판정 — 끝냈는지, 못 끝냈으면 남은 거리.
// 남은 거리는 사용자 종료(forced=false)만 RUNNING_GOAL_PENDING으로 알린다. 자동 종료는 알리지 않는다
public record GoalCheck(boolean finished, int remainingMeters) {

    public static GoalCheck reached() {
        return new GoalCheck(true, 0);
    }

    public static GoalCheck pending(int remainingMeters) {
        return new GoalCheck(false, remainingMeters);
    }

    // 다른 연결이 이 러닝을 끝내는 중이라 판정하지 않았다 — 자동 종료만 받는다(알리지 않고 다음 배치가 다시 본다).
    // 사용자 종료는 이 값 대신 RunningFinishInProgressException으로 재전송을 받는다
    public static GoalCheck inProgress() {
        return new GoalCheck(false, 0);
    }
}
