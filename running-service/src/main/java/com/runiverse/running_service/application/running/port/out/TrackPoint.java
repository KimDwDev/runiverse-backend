package com.runiverse.running_service.application.running.port.out;

import java.time.LocalDateTime;

public record TrackPoint(
        // 아래 넷은 Location.isValid()가 null을 막아준다
        long sequence,
        double latitude,
        double longitude,
        double accuracyMeters,
        // 단말이 못 잴 수 있다 — 배치를 통째로 버리지 않으려고 null을 그대로 받는다
        Double altitudeMeters,
        Double speedMetersPerSecond,
        Double headingDegrees,
        Integer cadenceSpm,
        Integer currentPaceSecondsPerKm,
        LocalDateTime recordedAt
) {

}
