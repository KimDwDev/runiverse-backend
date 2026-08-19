package com.runiverse.running_service.domain.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RunningRecordErrorCode implements ErrorCode {
    INVALID_RUNNING_RECORD_ID("INVALID_RUNNING_RECORD_ID", "러닝 기록 ID가 올바르지 않습니다."),
    INVALID_SPLIT_NUMBER("INVALID_SPLIT_NUMBER", "구간 번호는 1 이상이어야 합니다."),
    ROUTE_POLYLINE_REQUIRED("ROUTE_POLYLINE_REQUIRED", "경로 데이터는 필수입니다."),
    GPS_TRACK_KEY_REQUIRED("GPS_TRACK_KEY_REQUIRED", "GPS 트랙 키는 필수입니다."),
    GPS_TRACK_KEY_TOO_LONG("GPS_TRACK_KEY_TOO_LONG", "GPS 트랙 키는 255자를 초과할 수 없습니다."),
    INVALID_ROUTE_RANGE("INVALID_ROUTE_RANGE", "구간 경로 범위가 올바르지 않습니다.");
    private final String code;
    private final String message;
}
