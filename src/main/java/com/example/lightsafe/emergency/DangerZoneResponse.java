package com.example.lightsafe.emergency;

import java.time.LocalDateTime;

public record DangerZoneResponse(
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
    public static DangerZoneResponse from(DangerZone zone) {
        return new DangerZoneResponse(
                zone.getDangerZoneId(),
                zone.getCenterLatitude().doubleValue(),
                zone.getCenterLongitude().doubleValue(),
                zone.getRadius(),
                zone.getDangerLevel().name(),
                zone.getReportCount(),
                zone.getIsActive(),
                zone.getCreatedAt(),
                zone.getExpiredAt()
        );
    }
}