package com.example.lightsafe.friends.message;

import com.example.lightsafe.friends.FriendMessageDto;
import com.example.lightsafe.friends.FriendService;
import com.example.lightsafe.user.ApiResponse;
import com.example.lightsafe.user.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/messages") // 기본 주소: /messages
public class MessageController {

    private final MessageService messageService;
    private final JwtUtil jwtUtil;
    private final FriendService friendService;

    // [POST /messages/{receiverId}] 쪽지 보내기
    @PostMapping("/{receiverId}")
    public ApiResponse<Object> sendMessage(
            @PathVariable("receiverId") Long receiverId,
            @RequestBody FriendMessageDto messageDto,
            @RequestHeader("Authorization") String token) {

        Long loginUserId = extractUserId(token);
        try {
            // 1. 권한 검증: 현재 기획상 '친구'끼리만 쪽지를 보낼 수 있으므로 사전 검증 진행
            // (추후 친구 외 사용자에게 발송 가능하도록 정책 변경 시 해당 라인 삭제)
            friendService.validateFriendship(receiverId, loginUserId);

            // 2. 쪽지 저장
            messageService.sendMessage(loginUserId, receiverId, messageDto.getContent());

            return new ApiResponse<>(true, Collections.emptyMap(), "쪽지를 성공적으로 보냈습니다.");
        } catch (SecurityException e) {
            return new ApiResponse<>(false, "FORBIDDEN", e.getMessage());
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "NOT_FOUND", e.getMessage());
        }
    }

    // [GET /messages/received] 받은 쪽지함 조회
    @GetMapping("/received")
    public ApiResponse<List<Map<String, Object>>> getReceivedMessages(
            @RequestHeader("Authorization") String token) {

        Long loginUserId = extractUserId(token);
        List<Map<String, Object>> data = messageService.getReceivedMessages(loginUserId);

        return new ApiResponse<>(true, data, "받은 쪽지함 조회 성공");
    }

    // [GET /messages/sent] 보낸 쪽지함 조회
    @GetMapping("/sent")
    public ApiResponse<List<Map<String, Object>>> getSentMessages(
            @RequestHeader("Authorization") String token) {

        Long loginUserId = extractUserId(token);
        List<Map<String, Object>> data = messageService.getSentMessages(loginUserId);

        return new ApiResponse<>(true, data, "보낸 쪽지함 조회 성공");
    }

    // [GET /messages/{messageId}] 쪽지 단건 상세 조회 및 읽음 처리
    @GetMapping("/{messageId}")
    public ApiResponse<Map<String, Object>> getMessageDetail(
            @PathVariable("messageId") Long messageId,
            @RequestHeader("Authorization") String token) {

        Long loginUserId = extractUserId(token);
        try {
            Map<String, Object> data = messageService.getMessageDetail(messageId, loginUserId);
            return new ApiResponse<>(true, data, "쪽지 상세 조회 및 읽음 처리 성공");
        } catch (SecurityException e) {
            return new ApiResponse<>(false, "FORBIDDEN", e.getMessage());
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "NOT_FOUND", e.getMessage());
        }
    }

    // [GET /messages/unread-count] 안 읽은 쪽지 개수 조회
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> getUnreadMessageCount(
            @RequestHeader("Authorization") String token) {

        Long loginUserId = extractUserId(token);
        long count = messageService.getUnreadMessageCount(loginUserId);

        // 클라이언트 응답 편의를 위해 Map 형태로 감싸서 반환
        Map<String, Long> data = Map.of("unreadCount", count);

        return new ApiResponse<>(true, data, "안 읽은 쪽지 개수 조회 성공");
    }

    // [DELETE /messages/{messageId}] 쪽지 삭제
    @DeleteMapping("/{messageId}")
    public ApiResponse<Object> deleteMessage(
            @PathVariable("messageId") Long messageId,
            @RequestHeader("Authorization") String token) {

        Long loginUserId = extractUserId(token);
        try {
            messageService.deleteMessage(messageId, loginUserId);
            return new ApiResponse<>(true, Collections.emptyMap(), "쪽지 삭제 성공");
        } catch (SecurityException e) {
            return new ApiResponse<>(false, "FORBIDDEN", e.getMessage());
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "NOT_FOUND", e.getMessage());
        }
    }

    // [유틸 메서드] 토큰에서 회원 ID 추출
    private Long extractUserId(String token) {
        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        return jwtUtil.getUserIdFromToken(jwt);
    }
}