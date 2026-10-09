package com.runiverse.running_service.application.running.command.location;

// remainingMeters는 러닝 중 누적이 목표를 넘었는데 확정 거리가 모자랄 때만 있다
public record UpdateRunningLocationResult(boolean finished, Integer remainingMeters) {

    // 팩토리 이름에 of를 붙인다 — finished()는 record가 만드는 접근자 이름이라 겹치면 컴파일되지 않는다
    public static UpdateRunningLocationResult ofRunning() {
        return new UpdateRunningLocationResult(false, null);
    }

    public static UpdateRunningLocationResult ofFinished() {
        return new UpdateRunningLocationResult(true, null);
    }

    public static UpdateRunningLocationResult ofGoalPending(int remainingMeters) {
        return new UpdateRunningLocationResult(false, remainingMeters);
    }
}
