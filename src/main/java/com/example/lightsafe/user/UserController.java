package com.example.lightsafe.user;

import com.example.lightsafe.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final CurrentUserService currentUserService;

    @PostMapping("/register")
    public ApiResponse<Map<String, Long>> register(
            @RequestBody
            @Valid
            UserRegisterRequest request
    ) {
        Long userId =
                userService.register(request);

        return ApiResponse.ok(
                Map.of(
                        "userId",
                        userId
                ),
                "OK"
        );
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(
            @RequestBody
            @Valid
            UserLoginRequest request
    ) {
        return ApiResponse.ok(
                userService.login(request),
                "로그인 성공"
        );
    }

    @GetMapping("/{userId}")
    public ApiResponse<Map<String, Object>> getProfile(
            @PathVariable Long userId
    ) {
        Long loginUserId =
                currentUserService.getCurrentUserId();

        return ApiResponse.ok(
                userService.getProfile(
                        userId,
                        loginUserId
                ),
                "프로필 조회 성공"
        );
    }

    @PutMapping("/{userId}")
    public ApiResponse<Map<String, Object>> updateProfile(
            @PathVariable Long userId,
            @RequestBody
            @Valid
            UserUpdateRequest request
    ) {
        Long loginUserId =
                currentUserService.getCurrentUserId();

        return ApiResponse.ok(
                userService.updateProfile(
                        userId,
                        loginUserId,
                        request
                ),
                "프로필 수정 성공"
        );
    }

    @DeleteMapping("/{userId}")
    public ApiResponse<Void> deleteUser(
            @PathVariable Long userId
    ) {
        Long loginUserId =
                currentUserService.getCurrentUserId();

        userService.deleteUser(
                userId,
                loginUserId
        );

        return ApiResponse.ok(
                null,
                "회원 탈퇴 성공"
        );
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok(
                null,
                "로그아웃 성공"
        );
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>>
    getAllUsers() {

        Long loginUserId =
                currentUserService.getCurrentUserId();

        return ApiResponse.ok(
                userService.getAllUsers(
                        loginUserId
                ),
                "전체 사용자 조회 성공"
        );
    }

    @GetMapping("/auth-check")
    public ApiResponse<Map<String, Object>> checkAuth() {
        Long userId =
                currentUserService.getCurrentUserId();

        return ApiResponse.ok(
                Map.of(
                        "userId",
                        userId
                ),
                "인증이 유효한 사용자입니다."
        );
    }

    @GetMapping("/fake")
    public ApiResponse<Map<String, Object>>
    getFakeReportCount() {

        Long userId =
                currentUserService.getCurrentUserId();

        return ApiResponse.ok(
                Map.of(
                        "falseReportCount",
                        userService.getFakeReportCount(
                                userId
                        )
                ),
                "허위신고 횟수 조회 성공"
        );
    }

    @GetMapping("/black")
    public ApiResponse<Map<String, Object>>
    getBlacklistStatus() {

        Long userId =
                currentUserService.getCurrentUserId();

        return ApiResponse.ok(
                Map.of(
                        "isBlacklisted",
                        userService.getBlacklistStatus(
                                userId
                        )
                ),
                "블랙리스트 상태 조회 성공"
        );
    }

    @GetMapping("/my/posts")
    public ApiResponse<List<Map<String, Object>>>
    getMyPosts() {

        Long loginUserId =
                currentUserService.getCurrentUserId();

        return ApiResponse.ok(
                userService.getMyPosts(
                        loginUserId
                ),
                "내 커뮤니티 작성 내역 조회 성공"
        );
    }

    @GetMapping("/my/reports")
    public ApiResponse<List<Map<String, Object>>>
    getMyEmergencyReports() {

        Long loginUserId =
                currentUserService.getCurrentUserId();

        return ApiResponse.ok(
                userService.getMyEmergencyReports(
                        loginUserId
                ),
                "내 신고 내역 조회 성공"
        );
    }
}