package com.example.lightsafe.notification;

import com.example.lightsafe.emergency.EmergencyReport;
import com.example.lightsafe.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "notifications",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_notifications_recipient_report_type",
                        columnNames = {
                                "recipient_user_id",
                                "emergency_report_id",
                                "notification_type"
                        }
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    // 알림을 받는 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_user_id", nullable = false)
    private User recipient;

    // 알림의 원인이 된 긴급신고
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emergency_report_id", nullable = false)
    private EmergencyReport emergencyReport;

    @Builder.Default
    @Column(
            name = "notification_type",
            nullable = false,
            length = 30
    )
    private String notificationType = "EMERGENCY_REPORT";

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 255)
    private String message;

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    public void markAsRead() {
        this.read = true;
    }
}