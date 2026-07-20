package com.example.lightsafe.friends;

import com.example.lightsafe.common.response.ApiResponse;
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

    @GetMapping
    public ApiResponse<Object> getFriendList() {
        Long loginUserId =
                currentUserService.getCurrentUserId();

        List<Map<String, Object>> data =
                friendService.getFriendList(
                        loginUserId
                );

        return ApiResponse.ok(
                data,
                "전체 친구 목록 조회 성공"
        );
    }

    @PostMapping("/requests")
    public ApiResponse<Object> sendFriendRequest(
            @RequestBody
            @Valid
            FriendRequestDto requestDto
    ) {
        Long loginUserId =
                currentUserService.getCurrentUserId();

        friendService.sendFriendRequest(
                loginUserId,
                requestDto.getTargetUserId()
        );

        return ApiResponse.ok(
                Collections.emptyMap(),
                "친구 요청 전송 성공"
        );
    }

    @GetMapping("/requests/received")
    public ApiResponse<Object> getReceivedRequests() {
        Long loginUserId =
                currentUserService.getCurrentUserId();

        return ApiResponse.ok(
                friendService.getReceivedRequests(
                        loginUserId
                ),
                "받은 요청 목록 조회 성공"
        );
    }

    @GetMapping("/requests/sent")
    public ApiResponse<Object> getSentRequests() {
        Long loginUserId =
                currentUserService.getCurrentUserId();

        return ApiResponse.ok(
                friendService.getSentRequests(
                        loginUserId
                ),
                "보낸 요청 목록 조회 성공"
        );
    }

    @PutMapping("/requests/{request_id}/accept")
    public ApiResponse<Object> acceptFriendRequest(
            @PathVariable("request_id")
            Long requestId
    ) {
        Long loginUserId =
                currentUserService.getCurrentUserId();

        friendService.acceptFriendRequest(
                requestId,
                loginUserId
        );

        return ApiResponse.ok(
                Collections.emptyMap(),
                "친구 수락 성공"
        );
    }

    @PutMapping("/requests/{request_id}/reject")
    public ApiResponse<Object> rejectFriendRequest(
            @PathVariable("request_id")
            Long requestId
    ) {
        Long loginUserId =
                currentUserService.getCurrentUserId();

        friendService.rejectFriendRequest(
                requestId,
                loginUserId
        );

        return ApiResponse.ok(
                Collections.emptyMap(),
                "친구 요청 거절 성공"
        );
    }

    @DeleteMapping("/requests/{request_id}")
    public ApiResponse<Object> cancelFriendRequest(
            @PathVariable("request_id")
            Long requestId
    ) {
        Long loginUserId =
                currentUserService.getCurrentUserId();

        friendService.cancelFriendRequest(
                requestId,
                loginUserId
        );

        return ApiResponse.ok(
                Collections.emptyMap(),
                "친구 요청 취소 성공"
        );
    }

    @DeleteMapping("/{user_id}")
    public ApiResponse<Object> deleteFriend(
            @PathVariable("user_id")
            Long targetUserId
    ) {
        Long loginUserId =
                currentUserService.getCurrentUserId();

        friendService.deleteFriend(
                targetUserId,
                loginUserId
        );

        return ApiResponse.ok(
                Collections.emptyMap(),
                "친구 삭제 성공"
        );
    }

    @PutMapping("/{friends_id}/emergency-allow")
    public ApiResponse<Object> setEmergencyAllow(
            @PathVariable("friends_id")
            Long friendsId,

            @RequestBody
            @Valid
            EmergencyAllowUpdateRequest request
    ) {
        Long loginUserId =
                currentUserService.getCurrentUserId();

        return ApiResponse.ok(
                friendService.setEmergencyAllow(
                        friendsId,
                        loginUserId,
                        request.getIsEmergencyAllowed()
                ),
                "긴급 위치 공유 설정 변경 성공"
        );
    }
}