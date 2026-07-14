package com.example.lightsafe.emergency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public record PublicDangerZoneResponse(
        Long dangerZoneId,
        Double centerLatitude,
        Double centerLongitude,
        Integer radius,
        String dangerLevel,
        Integer reportCount,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime expiredAt
) {

    private static final int PUBLIC_COORDINATE_SCALE = 3;

    public static PublicDangerZoneResponse from(DangerZone zone) {
        return new PublicDangerZoneResponse(
                zone.getDangerZoneId(),
                toPublicCoordinate(zone.getCenterLatitude()),
                toPublicCoordinate(zone.getCenterLongitude()),
                zone.getRadius(),
                zone.getDangerLevel(),
                zone.getReportCount(),
                zone.getIsActive(),
                zone.getCreatedAt(),
                zone.getExpiredAt()
        );
    }

    private static Double toPublicCoordinate(BigDecimal coordinate) {
        if (coordinate == null) {
            return null;
        }

        return coordinate
                .setScale(PUBLIC_COORDINATE_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }
}