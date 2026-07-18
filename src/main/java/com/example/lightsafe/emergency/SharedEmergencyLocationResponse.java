package com.example.lightsafe.emergency;

import java.time.LocalDateTime;

public record SharedEmergencyLocationResponse(
        Long reportId,
        Long reporterUserId,
        String reporterNickname,
        Double latitude,
        Double longitude,
        String description,
        String reportStatus,
        Long dangerZoneId,
        String dangerLevel,
        LocalDateTime reportedAt
) {

    public static SharedEmergencyLocationResponse from(
            EmergencyReport report
    ) {
        return new SharedEmergencyLocationResponse(
                report.getReportId(),
                report.getUser().getUserId(),
                report.getUser().getNickname(),
                report.getLatitude().doubleValue(),
                report.getLongitude().doubleValue(),
                report.getDescription(),
                report.getReportStatus(),
                report.getDangerZone().getDangerZoneId(),
                report.getDangerZone().getDangerLevel(),
                report.getReportedAt()
        );
    }
}