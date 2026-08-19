package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RunningMetricErrorCode implements ErrorCode {
    DISTANCE_OUT_OF_RANGE("DISTANCE_OUT_OF_RANGE", "거리는 1m 이상 500km 이하여야 합니다."),
    PACE_OUT_OF_RANGE("PACE_OUT_OF_RANGE", "페이스는 1km당 120초 이상 3600초 이하여야 합니다."),
    ELAPSED_TIME_OUT_OF_RANGE("ELAPSED_TIME_OUT_OF_RANGE", "소요 시간은 1초 이상 24시간 이하여야 합니다."),
    RUNNING_PERIOD_REQUIRED("RUNNING_PERIOD_REQUIRED", "시작·종료 시각은 필수입니다."),
    INVALID_RUNNING_PERIOD("INVALID_RUNNING_PERIOD", "종료 시각은 시작 시각보다 뒤여야 합니다.");
    private final String code;
    private final String message;
}
