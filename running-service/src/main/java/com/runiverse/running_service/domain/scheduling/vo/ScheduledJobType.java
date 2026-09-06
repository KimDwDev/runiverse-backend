package com.runiverse.running_service.domain.scheduling.vo;

// 예약된 그 시각에 무엇을 할 것인가.
// 러닝 강제 종료·시작 리마인더가 붙으면 여기에 값을 더한다(erd §6)
public enum ScheduledJobType {
    MATCH_CLOSE
}
