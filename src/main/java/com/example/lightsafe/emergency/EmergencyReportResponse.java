package com.example.lightsafe.emergency;

import java.time.LocalDateTime;

public record EmergencyReportResponse(
        Long reportId,
        Long userId,
        String nickname,
        Double latitude,
        Double longitude,
        String reportStatus,
        String description,
        Boolean isFalseReport,
        Long dangerZoneId,
        String dangerLevel,
        CctvResponse nearestCctv,
        LocalDateTime reportedAt
) {
    public static EmergencyReportResponse from(EmergencyReport report) {
        return new EmergencyReportResponse(
                report.getReportId(),
                report.getUser().getUserId(),
                report.getUser().getNickname(),
                report.getLatitude().doubleValue(),
                report.getLongitude().doubleValue(),
                report.getReportStatus(),
                report.getDescription(),
                report.getIsFalseReport(),
                report.getDangerZone().getDangerZoneId(),
                report.getDangerZone().getDangerLevel(),
                CctvResponse.from(report.getNearestCctv()),
                report.getReportedAt()
        );
    }
}