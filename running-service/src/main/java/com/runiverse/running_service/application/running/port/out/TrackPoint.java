package com.runiverse.running_service.application.running.port.out;

import java.time.LocalDateTime;

public record TrackPoint(
        long sequence,
        double latitude,
        double longitude,
        Double altitudeMeters,
        double accuracyMeters,
        double speedMetersPerSecond,
        double headingDegrees,
        int cadenceSpm,
        int currentPaceSecondsPerKm,
        LocalDateTime recordedAt
) {

}
