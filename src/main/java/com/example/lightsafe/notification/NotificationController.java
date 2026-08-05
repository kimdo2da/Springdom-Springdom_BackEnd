package com.example.lightsafe.notification;

import com.example.lightsafe.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    // 내 알림 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<Object>>
    getMyNotifications() {

        List<NotificationResponse> data =
                notificationService.getMyNotifications();

        return ResponseEntity.ok(
                ApiResponse.ok(
                        data,
                        "알림 목록 조회 성공"
                )
        );
    }

    // 읽지 않은 알림 개수 조회
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Object>>
    getUnreadCount() {

        long unreadCount =
                notificationService.getUnreadCount();

        Map<String, Long> data =
                Map.of(
                        "unreadCount",
                        unreadCount
                );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        data,
                        "읽지 않은 알림 개수 조회 성공"
                )
        );
    }

    // 알림 읽음 처리
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Object>>
    markAsRead(
            @PathVariable Long notificationId
    ) {
        NotificationResponse data =
                notificationService.markAsRead(
                        notificationId
                );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        data,
                        "알림 읽음 처리 성공"
                )
        );
    }
}