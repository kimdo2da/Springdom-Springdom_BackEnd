package com.example.lightsafe.notification;

import com.example.lightsafe.user.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
        try {
            List<NotificationResponse> data =
                    notificationService
                            .getMyNotifications();

            return ResponseEntity.ok(
                    new ApiResponse<>(
                            true,
                            data,
                            "알림 목록 조회 성공"
                    )
            );

        } catch (SecurityException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(
                            new ApiResponse<>(
                                    false,
                                    "UNAUTHORIZED",
                                    e.getMessage()
                            )
                    );
        }
    }

    // 읽지 않은 알림 개수 조회
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Object>>
    getUnreadCount() {
        try {
            long unreadCount =
                    notificationService.getUnreadCount();

            Map<String, Long> data =
                    Map.of(
                            "unreadCount",
                            unreadCount
                    );

            return ResponseEntity.ok(
                    new ApiResponse<>(
                            true,
                            data,
                            "읽지 않은 알림 개수 조회 성공"
                    )
            );

        } catch (SecurityException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(
                            new ApiResponse<>(
                                    false,
                                    "UNAUTHORIZED",
                                    e.getMessage()
                            )
                    );
        }
    }

    // 알림 읽음 처리
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Object>>
    markAsRead(
            @PathVariable Long notificationId
    ) {
        try {
            NotificationResponse data =
                    notificationService
                            .markAsRead(notificationId);

            return ResponseEntity.ok(
                    new ApiResponse<>(
                            true,
                            data,
                            "알림 읽음 처리 성공"
                    )
            );

        } catch (SecurityException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(
                            new ApiResponse<>(
                                    false,
                                    "UNAUTHORIZED",
                                    e.getMessage()
                            )
                    );

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(
                            new ApiResponse<>(
                                    false,
                                    "NOT_FOUND",
                                    e.getMessage()
                            )
                    );
        }
    }
}