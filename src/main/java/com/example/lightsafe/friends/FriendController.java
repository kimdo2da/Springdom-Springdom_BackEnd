package com.example.lightsafe.friends;

import com.example.lightsafe.message.MessageService;
import com.example.lightsafe.user.JwtUtil;
import com.example.lightsafe.user.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/friends")
public class FriendController {

    private final FriendService friendService;
    private final JwtUtil jwtUtil;
    private final MessageService messageService;

    // 1. 친구 목록 조회 - GET /friends
    @GetMapping("")
    public ApiResponse<Object> getFriendList(@RequestHeader("Authorization") String token) {
        Long loginUserId = extractUserId(token);
        try {
            List<Map<String, Object>> data = friendService.getFriendList(loginUserId);
            return new ApiResponse<>(true, data, "전체 친구 목록 조회 성공");
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "NOT_FOUND", e.getMessage());
        }
    }

    // 2. 친구 요청 보내기 - POST /friends/requests
    @PostMapping("/requests")
    public ApiResponse<Object> sendFriendRequest(
            @RequestBody FriendRequestDto requestDto,
            @RequestHeader("Authorization") String token) {

        Long loginUserId = extractUserId(token);
        try {
            friendService.sendFriendRequest(loginUserId, requestDto.getTargetUserId());
            return new ApiResponse<>(true, Collections.emptyMap(), "친구 요청 전송 성공");
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "BAD_REQUEST", e.getMessage());
        } catch (IllegalStateException e) {
            return new ApiResponse<>(false, "CONFLICT", e.getMessage());
        }
    }

    // 3. 받은 요청 조회 - GET /friends/requests/received
    @GetMapping("/requests/received")
    public ApiResponse<Object> getReceivedRequests(@RequestHeader("Authorization") String token) {
        Long loginUserId = extractUserId(token);
        try {
            List<Map<String, Object>> data = friendService.getReceivedRequests(loginUserId);
            return new ApiResponse<>(true, data, "받은 요청 목록 조회 성공");
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "NOT_FOUND", e.getMessage());
        }
    }

    // 4. 보낸 요청 조회 - GET /friends/requests/sent
    @GetMapping("/requests/sent")
    public ApiResponse<Object> getSentRequests(@RequestHeader("Authorization") String token) {
        Long loginUserId = extractUserId(token);
        try {
            List<Map<String, Object>> data = friendService.getSentRequests(loginUserId);
            return new ApiResponse<>(true, data, "보낸 요청 목록 조회 성공");
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "NOT_FOUND", e.getMessage());
        }
    }

    // 5. 친구 수락 - PUT /friends/requests/{request_id}/accept
    @PutMapping("/requests/{request_id}/accept")
    public ApiResponse<Object> acceptFriendRequest(
            @PathVariable("request_id") Long requestId,
            @RequestHeader("Authorization") String token) {

        Long loginUserId = extractUserId(token);
        try {
            friendService.acceptFriendRequest(requestId, loginUserId);
            return new ApiResponse<>(true, Collections.emptyMap(), "친구 수락 성공");
        } catch (SecurityException e) {
            return new ApiResponse<>(false, "FORBIDDEN", e.getMessage());
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "BAD_REQUEST", e.getMessage());
        }
    }

    // 6. 요청 거절 - PUT /friends/requests/{request_id}/reject
    @PutMapping("/requests/{request_id}/reject")
    public ApiResponse<Object> rejectFriendRequest(
            @PathVariable("request_id") Long requestId,
            @RequestHeader("Authorization") String token) {

        Long loginUserId = extractUserId(token);
        try {
            friendService.rejectFriendRequest(requestId, loginUserId);
            return new ApiResponse<>(true, Collections.emptyMap(), "친구 요청 거절 성공");
        } catch (SecurityException e) {
            return new ApiResponse<>(false, "FORBIDDEN", e.getMessage());
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "BAD_REQUEST", e.getMessage());
        }
    }

    // 7. 요청 취소 (보낸 사람이 취소) - DELETE /friends/requests/{request_id}
    @DeleteMapping("/requests/{request_id}")
    public ApiResponse<Object> cancelFriendRequest(
            @PathVariable("request_id") Long requestId,
            @RequestHeader("Authorization") String token) {

        Long loginUserId = extractUserId(token);
        try {
            friendService.cancelFriendRequest(requestId, loginUserId);
            return new ApiResponse<>(true, Collections.emptyMap(), "친구 요청 취소 성공");
        } catch (SecurityException e) {
            return new ApiResponse<>(false, "FORBIDDEN", e.getMessage());
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "BAD_REQUEST", e.getMessage());
        }
    }

    // 8. 친구 삭제 - DELETE /friends/{user_id}
    @DeleteMapping("/{user_id}")
    public ApiResponse<Object> deleteFriend(
            @PathVariable("user_id") Long targetUserId,
            @RequestHeader("Authorization") String token) {

        Long loginUserId = extractUserId(token);
        try {
            friendService.deleteFriend(targetUserId, loginUserId);
            return new ApiResponse<>(true, Collections.emptyMap(), "친구 삭제 성공");
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "BAD_REQUEST", e.getMessage());
        }
    }


    // 10. 긴급 위치 공유 허용 여부 변경 - PUT /friends/{friends_id}/emergency-allow
    @PutMapping("/{friends_id}/emergency-allow")
    public ApiResponse<Object> toggleEmergencyAllow(
            @PathVariable("friends_id") Long friendsId,
            @RequestHeader("Authorization") String token) {

        Long loginUserId = extractUserId(token);
        try {
            String statusMsg = friendService.toggleEmergencyAllow(friendsId, loginUserId);
            return new ApiResponse<>(true, Collections.emptyMap(), "긴급 설정 변경 성공 (" + statusMsg + ")");
        } catch (SecurityException e) {
            return new ApiResponse<>(false, "FORBIDDEN", e.getMessage());
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "BAD_REQUEST", e.getMessage());
        }
    }

    // 공통 유틸 메서드: 토큰에서 회원 ID 추출
    private Long extractUserId(String token) {
        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        return jwtUtil.getUserIdFromToken(jwt);
    }
}