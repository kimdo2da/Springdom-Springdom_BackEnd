package com.example.lightsafe.message;

import com.example.lightsafe.common.response.ApiResponse;
import com.example.lightsafe.friends.FriendMessageDto;
import com.example.lightsafe.friends.FriendService;
import com.example.lightsafe.user.CurrentUserService;
import jakarta.validation.Valid;
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

    @PostMapping("/{receiverId}")
    public ApiResponse<Object> sendMessage(
            @PathVariable Long receiverId,

            @RequestBody
            @Valid
            FriendMessageDto messageDto
    ) {
        Long loginUserId =
                currentUserService.getCurrentUserId();

        friendService.validateFriendship(
                receiverId,
                loginUserId
        );

        messageService.sendMessage(
                loginUserId,
                receiverId,
                messageDto.getContent()
        );

        return ApiResponse.ok(
                Collections.emptyMap(),
                "쪽지를 성공적으로 보냈습니다."
        );
    }

    @GetMapping("/received")
    public ApiResponse<List<Map<String, Object>>>
    getReceivedMessages() {

        Long loginUserId =
                currentUserService.getCurrentUserId();

        return ApiResponse.ok(
                messageService.getReceivedMessages(
                        loginUserId
                ),
                "받은 쪽지함 조회 성공"
        );
    }

    @GetMapping("/sent")
    public ApiResponse<List<Map<String, Object>>>
    getSentMessages() {

        Long loginUserId =
                currentUserService.getCurrentUserId();

        return ApiResponse.ok(
                messageService.getSentMessages(
                        loginUserId
                ),
                "보낸 쪽지함 조회 성공"
        );
    }

    @GetMapping("/{messageId}")
    public ApiResponse<Map<String, Object>>
    getMessageDetail(
            @PathVariable Long messageId
    ) {
        Long loginUserId =
                currentUserService.getCurrentUserId();

        return ApiResponse.ok(
                messageService.getMessageDetail(
                        messageId,
                        loginUserId
                ),
                "쪽지 상세 조회 및 읽음 처리 성공"
        );
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>>
    getUnreadMessageCount() {

        Long loginUserId =
                currentUserService.getCurrentUserId();

        long count =
                messageService.getUnreadMessageCount(
                        loginUserId
                );

        return ApiResponse.ok(
                Map.of(
                        "unreadCount",
                        count
                ),
                "안 읽은 쪽지 개수 조회 성공"
        );
    }

    @DeleteMapping("/{messageId}")
    public ApiResponse<Object> deleteMessage(
            @PathVariable Long messageId
    ) {
        Long loginUserId =
                currentUserService.getCurrentUserId();

        messageService.deleteMessage(
                messageId,
                loginUserId
        );

        return ApiResponse.ok(
                Collections.emptyMap(),
                "쪽지 삭제 성공"
        );
    }
}