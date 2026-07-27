package com.example.lightsafe.emergency;

import java.time.LocalDateTime;
import java.util.List;

public record DangerZoneDetailResponse(
        Long dangerZoneId,
        Double centerLatitude,
        Double centerLongitude,
        Integer radius,
        String dangerLevel,
        Integer reportCount,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime expiredAt,
        List<EmergencyReportResponse> reports
) {
    public static DangerZoneDetailResponse of(DangerZone zone, List<EmergencyReportResponse> reports) {
        return new DangerZoneDetailResponse(
                zone.getDangerZoneId(),
                zone.getCenterLatitude().doubleValue(),
                zone.getCenterLongitude().doubleValue(),
                zone.getRadius(),
                zone.getDangerLevel().name(),
                zone.getReportCount(),
                zone.getIsActive(),
                zone.getCreatedAt(),
                zone.getExpiredAt(),
                reports
        );
    }
}