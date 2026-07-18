package com.example.lightsafe.notification;

import com.example.lightsafe.emergency.EmergencyReport;
import com.example.lightsafe.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {

    private static final String EMERGENCY_REPORT_TYPE =
            "EMERGENCY_REPORT";

    private final NotificationRepository notificationRepository;

    /**
     * 긴급신고가 발생했을 때 위치 공유가 허용된 친구들에게
     * 알림 데이터를 생성합니다.
     */
    public void createEmergencyReportNotifications(
            EmergencyReport report,
            List<User> recipients
    ) {
        if (report == null
                || recipients == null
                || recipients.isEmpty()) {
            return;
        }

        List<Notification> notifications = new ArrayList<>();

        for (User recipient : recipients) {
            if (recipient == null) {
                continue;
            }

            // 신고자 본인에게는 친구 알림을 만들지 않음
            if (Objects.equals(
                    recipient.getUserId(),
                    report.getUser().getUserId()
            )) {
                continue;
            }

            // 같은 신고에 같은 알림이 중복 저장되는 것을 방지
            boolean alreadyExists =
                    notificationRepository
                            .existsByRecipient_UserIdAndEmergencyReport_ReportIdAndNotificationType(
                                    recipient.getUserId(),
                                    report.getReportId(),
                                    EMERGENCY_REPORT_TYPE
                            );

            if (alreadyExists) {
                continue;
            }

            String reporterNickname =
                    report.getUser().getNickname();

            Notification notification =
                    Notification.builder()
                            .recipient(recipient)
                            .emergencyReport(report)
                            .notificationType(
                                    EMERGENCY_REPORT_TYPE
                            )
                            .title("긴급 위치 공유 알림")
                            .message(
                                    reporterNickname
                                            + "님이 긴급신고를 보냈습니다. "
                                            + "정확한 위치를 확인해 주세요."
                            )
                            .read(false)
                            .build();

            notifications.add(notification);
        }

        if (!notifications.isEmpty()) {
            notificationRepository.saveAll(notifications);
        }
    }

    /**
     * 현재 로그인 사용자가 받은 알림 목록을 조회합니다.
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotifications() {
        Long currentUserId = getCurrentUserId();

        return notificationRepository
                .findByRecipient_UserIdOrderByCreatedAtDesc(
                        currentUserId
                )
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    /**
     * 현재 로그인 사용자의 읽지 않은 알림 개수입니다.
     */
    @Transactional(readOnly = true)
    public long getUnreadCount() {
        Long currentUserId = getCurrentUserId();

        return notificationRepository
                .countByRecipient_UserIdAndReadFalse(
                        currentUserId
                );
    }

    /**
     * 본인에게 도착한 알림만 읽음 처리합니다.
     */
    public NotificationResponse markAsRead(
            Long notificationId
    ) {
        Long currentUserId = getCurrentUserId();

        Notification notification =
                notificationRepository
                        .findByNotificationIdAndRecipient_UserId(
                                notificationId,
                                currentUserId
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "존재하지 않는 알림입니다."
                                )
                        );

        notification.markAsRead();

        return NotificationResponse.from(notification);
    }

    private Long getCurrentUserId() {
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(
                authentication.getPrincipal()
        )) {
            throw new SecurityException(
                    "로그인이 필요합니다."
            );
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Long userId) {
            return userId;
        }

        try {
            return Long.valueOf(authentication.getName());

        } catch (NumberFormatException e) {
            throw new SecurityException(
                    "로그인 사용자 정보를 확인할 수 없습니다."
            );
        }
    }
}