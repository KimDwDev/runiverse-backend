package com.runiverse.running_service.application.running.command.combo;

// 판정 한 번에 보낸 참가자가 넘기는 것 — 누적 거리와, 참가자 상태를 정할 재료다
public record RunningComboSignal(
        double meters,
        // 정확도 상한 이하의 새 좌표가 있었나
        boolean observed,
        // 의심점이 옮겨졌나
        boolean moved
) {

    // RUNNING_START의 첫 판정 — 좌표 없이 기준만 세운다. 관측도 이동도 아니다
    public static RunningComboSignal baseline(double meters) {
        return new RunningComboSignal(meters, false, false);
    }
}
