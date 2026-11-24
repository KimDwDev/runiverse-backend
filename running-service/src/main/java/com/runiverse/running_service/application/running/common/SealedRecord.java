package com.runiverse.running_service.application.running.common;

import com.runiverse.running_service.application.running.port.out.Weather;

import java.math.BigDecimal;

// 봉인(잠금 밖)이 확정에 넘기는 기록 재료 — 분석과, 잠금 밖에서 끝낸 외부 호출(S3 원본·날씨)의 결과
public record SealedRecord(TrackAnalysis analysis, String gpsTrackKey, Weather weather, BigDecimal weightKg) {
}
