package com.example.lightsafe.notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        String notificationType,
        String title,
        String message,
        Boolean isRead,
        Long reportId,
        Long reporterUserId,
        String reporterNickname,
        LocalDateTime createdAt
) {

    public static NotificationResponse from(
            Notification notification
    ) {
        return new NotificationResponse(
                notification.getNotificationId(),
                notification.getNotificationType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.isRead(),
                notification.getEmergencyReport().getReportId(),
                notification.getEmergencyReport()
                        .getUser()
                        .getUserId(),
                notification.getEmergencyReport()
                        .getUser()
                        .getNickname(),
                notification.getCreatedAt()
        );
    }
}