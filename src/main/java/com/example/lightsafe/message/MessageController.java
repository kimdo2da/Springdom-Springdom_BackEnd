package com.example.lightsafe.message;

import com.example.lightsafe.friends.FriendMessageDto;
import com.example.lightsafe.friends.FriendService;
import com.example.lightsafe.user.ApiResponse;
import com.example.lightsafe.user.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/messages")
public class MessageController {

    private final MessageService messageService;
    private final CurrentUserService currentUserService;
    private final FriendService friendService;

    // 1. 쪽지 보내기
    @PostMapping("/{receiverId}")
    public ApiResponse<Object> sendMessage(
            @PathVariable("receiverId") Long receiverId,
            @RequestBody FriendMessageDto messageDto
    ) {
        try {
            Long loginUserId =
                    currentUserService.getCurrentUserId();

            // 현재 정책: 친구끼리만 쪽지 전송 가능
            friendService.validateFriendship(
                    receiverId,
                    loginUserId
            );

            messageService.sendMessage(
                    loginUserId,
                    receiverId,
                    messageDto.getContent()
            );

            return new ApiResponse<>(
                    true,
                    Collections.emptyMap(),
                    "쪽지를 성공적으로 보냈습니다."
            );

        } catch (SecurityException e) {
            return new ApiResponse<>(
                    false,
                    "FORBIDDEN",
                    e.getMessage()
            );

        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(
                    false,
                    "NOT_FOUND",
                    e.getMessage()
            );
        }
    }

    // 2. 받은 쪽지함 조회
    @GetMapping("/received")
    public ApiResponse<List<Map<String, Object>>>
    getReceivedMessages() {
        try {
            Long loginUserId =
                    currentUserService.getCurrentUserId();

            List<Map<String, Object>> data =
                    messageService.getReceivedMessages(
                            loginUserId
                    );

            return new ApiResponse<>(
                    true,
                    data,
                    "받은 쪽지함 조회 성공"
            );

        } catch (SecurityException e) {
            return new ApiResponse<>(
                    false,
                    "UNAUTHORIZED",
                    e.getMessage()
            );
        }
    }

    // 3. 보낸 쪽지함 조회
    @GetMapping("/sent")
    public ApiResponse<List<Map<String, Object>>>
    getSentMessages() {
        try {
            Long loginUserId =
                    currentUserService.getCurrentUserId();

            List<Map<String, Object>> data =
                    messageService.getSentMessages(
                            loginUserId
                    );

            return new ApiResponse<>(
                    true,
                    data,
                    "보낸 쪽지함 조회 성공"
            );

        } catch (SecurityException e) {
            return new ApiResponse<>(
                    false,
                    "UNAUTHORIZED",
                    e.getMessage()
            );
        }
    }

    // 4. 쪽지 단건 상세 조회 및 읽음 처리
    @GetMapping("/{messageId}")
    public ApiResponse<Map<String, Object>>
    getMessageDetail(
            @PathVariable("messageId") Long messageId
    ) {
        try {
            Long loginUserId =
                    currentUserService.getCurrentUserId();

            Map<String, Object> data =
                    messageService.getMessageDetail(
                            messageId,
                            loginUserId
                    );

            return new ApiResponse<>(
                    true,
                    data,
                    "쪽지 상세 조회 및 읽음 처리 성공"
            );

        } catch (SecurityException e) {
            return new ApiResponse<>(
                    false,
                    "FORBIDDEN",
                    e.getMessage()
            );

        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(
                    false,
                    "NOT_FOUND",
                    e.getMessage()
            );
        }
    }

    // 5. 안 읽은 쪽지 개수 조회
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>>
    getUnreadMessageCount() {
        try {
            Long loginUserId =
                    currentUserService.getCurrentUserId();

            long count =
                    messageService.getUnreadMessageCount(
                            loginUserId
                    );

            Map<String, Long> data =
                    Map.of(
                            "unreadCount",
                            count
                    );

            return new ApiResponse<>(
                    true,
                    data,
                    "안 읽은 쪽지 개수 조회 성공"
            );

        } catch (SecurityException e) {
            return new ApiResponse<>(
                    false,
                    "UNAUTHORIZED",
                    e.getMessage()
            );
        }
    }

    // 6. 쪽지 삭제
    @DeleteMapping("/{messageId}")
    public ApiResponse<Object> deleteMessage(
            @PathVariable("messageId") Long messageId
    ) {
        try {
            Long loginUserId =
                    currentUserService.getCurrentUserId();

            messageService.deleteMessage(
                    messageId,
                    loginUserId
            );

            return new ApiResponse<>(
                    true,
                    Collections.emptyMap(),
                    "쪽지 삭제 성공"
            );

        } catch (SecurityException e) {
            return new ApiResponse<>(
                    false,
                    "FORBIDDEN",
                    e.getMessage()
            );

        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(
                    false,
                    "NOT_FOUND",
                    e.getMessage()
            );
        }
    }
}