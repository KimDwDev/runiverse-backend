package com.runiverse.running_service.application.running.command.combo;

import com.runiverse.running_service.application.running.port.out.RunningComboPair;
import com.runiverse.running_service.application.running.port.out.RunningComboUpdate;

import java.util.List;

// 판정 한 번의 산물 — 저장할 관계 상태와 내보낼 통이다
public record RunningComboEvaluation(
        List<RunningComboPair> pairs,
        RunningComboUpdate update
) {

}
