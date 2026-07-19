package com.example.lightsafe.friends;

import com.example.lightsafe.user.ApiResponse;
import com.example.lightsafe.user.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/friends")
public class FriendController {

    private final FriendService friendService;
    private final CurrentUserService currentUserService;

    // 1. 친구 목록 조회
    @GetMapping
    public ApiResponse<Object> getFriendList() {
        try {
            Long loginUserId =
                    currentUserService.getCurrentUserId();

            List<Map<String, Object>> data =
                    friendService.getFriendList(
                            loginUserId
                    );

            return new ApiResponse<>(
                    true,
                    data,
                    "전체 친구 목록 조회 성공"
            );

        } catch (SecurityException e) {
            return new ApiResponse<>(
                    false,
                    "UNAUTHORIZED",
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

    // 2. 친구 요청 보내기
    @PostMapping("/requests")
    public ApiResponse<Object> sendFriendRequest(
            @RequestBody FriendRequestDto requestDto
    ) {
        try {
            Long loginUserId =
                    currentUserService.getCurrentUserId();

            friendService.sendFriendRequest(
                    loginUserId,
                    requestDto.getTargetUserId()
            );

            return new ApiResponse<>(
                    true,
                    Collections.emptyMap(),
                    "친구 요청 전송 성공"
            );

        } catch (SecurityException e) {
            return new ApiResponse<>(
                    false,
                    "UNAUTHORIZED",
                    e.getMessage()
            );

        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(
                    false,
                    "BAD_REQUEST",
                    e.getMessage()
            );

        } catch (IllegalStateException e) {
            return new ApiResponse<>(
                    false,
                    "CONFLICT",
                    e.getMessage()
            );
        }
    }

    // 3. 받은 친구 요청 조회
    @GetMapping("/requests/received")
    public ApiResponse<Object> getReceivedRequests() {
        try {
            Long loginUserId =
                    currentUserService.getCurrentUserId();

            List<Map<String, Object>> data =
                    friendService.getReceivedRequests(
                            loginUserId
                    );

            return new ApiResponse<>(
                    true,
                    data,
                    "받은 요청 목록 조회 성공"
            );

        } catch (SecurityException e) {
            return new ApiResponse<>(
                    false,
                    "UNAUTHORIZED",
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

    // 4. 보낸 친구 요청 조회
    @GetMapping("/requests/sent")
    public ApiResponse<Object> getSentRequests() {
        try {
            Long loginUserId =
                    currentUserService.getCurrentUserId();

            List<Map<String, Object>> data =
                    friendService.getSentRequests(
                            loginUserId
                    );

            return new ApiResponse<>(
                    true,
                    data,
                    "보낸 요청 목록 조회 성공"
            );

        } catch (SecurityException e) {
            return new ApiResponse<>(
                    false,
                    "UNAUTHORIZED",
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

    // 5. 친구 요청 수락
    @PutMapping("/requests/{request_id}/accept")
    public ApiResponse<Object> acceptFriendRequest(
            @PathVariable("request_id") Long requestId
    ) {
        try {
            Long loginUserId =
                    currentUserService.getCurrentUserId();

            friendService.acceptFriendRequest(
                    requestId,
                    loginUserId
            );

            return new ApiResponse<>(
                    true,
                    Collections.emptyMap(),
                    "친구 수락 성공"
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
                    "BAD_REQUEST",
                    e.getMessage()
            );
        }
    }

    // 6. 친구 요청 거절
    @PutMapping("/requests/{request_id}/reject")
    public ApiResponse<Object> rejectFriendRequest(
            @PathVariable("request_id") Long requestId
    ) {
        try {
            Long loginUserId =
                    currentUserService.getCurrentUserId();

            friendService.rejectFriendRequest(
                    requestId,
                    loginUserId
            );

            return new ApiResponse<>(
                    true,
                    Collections.emptyMap(),
                    "친구 요청 거절 성공"
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
                    "BAD_REQUEST",
                    e.getMessage()
            );
        }
    }

    // 7. 친구 요청 취소
    @DeleteMapping("/requests/{request_id}")
    public ApiResponse<Object> cancelFriendRequest(
            @PathVariable("request_id") Long requestId
    ) {
        try {
            Long loginUserId =
                    currentUserService.getCurrentUserId();

            friendService.cancelFriendRequest(
                    requestId,
                    loginUserId
            );

            return new ApiResponse<>(
                    true,
                    Collections.emptyMap(),
                    "친구 요청 취소 성공"
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
                    "BAD_REQUEST",
                    e.getMessage()
            );
        }
    }

    // 8. 친구 삭제
    @DeleteMapping("/{user_id}")
    public ApiResponse<Object> deleteFriend(
            @PathVariable("user_id") Long targetUserId
    ) {
        try {
            Long loginUserId =
                    currentUserService.getCurrentUserId();

            friendService.deleteFriend(
                    targetUserId,
                    loginUserId
            );

            return new ApiResponse<>(
                    true,
                    Collections.emptyMap(),
                    "친구 삭제 성공"
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
                    "BAD_REQUEST",
                    e.getMessage()
            );
        }
    }

    // 9. 긴급 위치 공유 설정 변경
    @PutMapping("/{friends_id}/emergency-allow")
    public ApiResponse<Object> setEmergencyAllow(
            @PathVariable("friends_id") Long friendsId,
            @Valid
            @RequestBody
            EmergencyAllowUpdateRequest request
    ) {
        try {
            Long loginUserId =
                    currentUserService.getCurrentUserId();

            Map<String, Object> data =
                    friendService.setEmergencyAllow(
                            friendsId,
                            loginUserId,
                            request.getIsEmergencyAllowed()
                    );

            return new ApiResponse<>(
                    true,
                    data,
                    "긴급 위치 공유 설정 변경 성공"
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
                    "BAD_REQUEST",
                    e.getMessage()
            );
        }
    }
}