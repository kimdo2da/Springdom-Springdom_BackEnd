package com.example.lightsafe.notification;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    @EntityGraph(
            attributePaths = {
                    "emergencyReport",
                    "emergencyReport.user"
            }
    )
    List<Notification>
    findByRecipient_UserIdOrderByCreatedAtDesc(
            Long recipientUserId
    );

    @EntityGraph(
            attributePaths = {
                    "emergencyReport",
                    "emergencyReport.user"
            }
    )
    Optional<Notification>
    findByNotificationIdAndRecipient_UserId(
            Long notificationId,
            Long recipientUserId
    );

    long countByRecipient_UserIdAndReadFalse(
            Long recipientUserId
    );

    boolean existsByRecipient_UserIdAndEmergencyReport_ReportIdAndNotificationType(
            Long recipientUserId,
            Long reportId,
            String notificationType
    );
    @Modifying(flushAutomatically = true)
    @Query("""
        DELETE FROM Notification notification
         WHERE notification.recipient.userId = :userId
        """)
    int deleteAllReceivedByUserId(
            @Param("userId")
            Long userId
    );
}