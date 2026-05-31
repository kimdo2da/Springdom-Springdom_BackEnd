package com.example.lightsafe.user;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    // 1. 회원가입
    @PostMapping("/register")
    public ApiResponse<Map<String, Long>> register(@RequestBody UserRegisterRequest request) {
        try {
            Long userId = userService.register(request);
            Map<String, Long> data = new HashMap<>();
            data.put("userId", userId);
            return new ApiResponse<>(true, data, "OK");
        } catch (IllegalStateException e) {
            return new ApiResponse<>(false, "CONFLICT", e.getMessage());
        }
    }

    // 2. 로그인
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody UserLoginRequest request) {
        try {
            Map<String, Object> data = userService.login(request);
            return new ApiResponse<>(true, data, "로그인 성공");
        } catch (SecurityException e) {
            return new ApiResponse<>(false, "UNAUTHORIZED", e.getMessage());
        }
    }

    // 3. 특정 사용자 조회
    @GetMapping("/{userId}")
    public ApiResponse<Map<String, Object>> getProfile(
            @PathVariable Long userId,
            @RequestHeader("Authorization") String token) {
        Long loginUserId = extractUserId(token);
        try {
            Map<String, Object> data = userService.getProfile(userId, loginUserId);
            return new ApiResponse<>(true, data, "프로필 조회 성공");
        } catch (SecurityException e) {
            return new ApiResponse<>(false, "FORBIDDEN", e.getMessage());
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "NOT_FOUND", e.getMessage());
        }
    }

    // 4. 사용자 정보 수정
    @PutMapping("/{userId}")
    public ApiResponse<Map<String, Object>> updateProfile(
            @PathVariable Long userId,
            @RequestBody UserUpdateRequest request,
            @RequestHeader("Authorization") String token) {
        Long loginUserId = extractUserId(token);
        try {
            Map<String, Object> data = userService.updateProfile(userId, loginUserId, request);
            return new ApiResponse<>(true, data, "프로필 수정 성공");
        } catch (SecurityException e) {
            return new ApiResponse<>(false, "FORBIDDEN", e.getMessage());
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "BAD_REQUEST", e.getMessage());
        } catch (IllegalStateException e) {
            return new ApiResponse<>(false, "CONFLICT", e.getMessage());
        }
    }

    // 5. 특정 사용자 삭제
    @DeleteMapping("/{userId}")
    public ApiResponse<String> deleteUser(
            @PathVariable Long userId,
            @RequestHeader("Authorization") String token) {
        Long loginUserId = extractUserId(token);
        try {
            userService.deleteUser(userId, loginUserId);
            return new ApiResponse<>(true, null, "회원 탈퇴 성공");
        } catch (SecurityException e) {
            return new ApiResponse<>(false, "FORBIDDEN", e.getMessage());
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "NOT_FOUND", e.getMessage());
        }
    }

    // 6. 로그아웃
    @PostMapping("/logout")
    public ApiResponse<String> logout() {
        return new ApiResponse<>(true, null, "로그아웃 성공");
    }

    // 7. 모든 사용자 조회
    @GetMapping("")
    public ApiResponse<Object> getAllUsers(@RequestHeader("Authorization") String token) {
        Long loginUserId = extractUserId(token);
        try {
            List<Map<String, Object>> data = userService.getAllUsers(loginUserId);
            return new ApiResponse<>(true, data, "전체 사용자 조회 성공");
        } catch (SecurityException e) {
            return new ApiResponse<>(false, "FORBIDDEN", e.getMessage());
        }
    }

    // 8. 인증 상태 확인
    @GetMapping("/auth-check")
    public ApiResponse<Map<String, Object>> checkAuth(@RequestHeader("Authorization") String token) {
        Long userId = extractUserId(token);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        return new ApiResponse<>(true, data, "인증이 유효한 사용자입니다.");
    }

    // 9. 내 허위신고 횟수 조회
    @GetMapping("/fake")
    public ApiResponse<Map<String, Object>> getFakeReportCount(@RequestHeader("Authorization") String token) {
        Long userId = extractUserId(token);
        try {
            int count = userService.getFakeReportCount(userId);
            Map<String, Object> data = new HashMap<>();
            data.put("falseReportCount", count);
            return new ApiResponse<>(true, data, "허위신고 횟수 조회 성공");
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "NOT_FOUND", e.getMessage());
        }
    }

    // 10. 내 블랙리스트 여부 조회
    @GetMapping("/black")
    public ApiResponse<Map<String, Object>> getBlacklistStatus(@RequestHeader("Authorization") String token) {
        Long userId = extractUserId(token);
        try {
            boolean status = userService.getBlacklistStatus(userId);
            Map<String, Object> data = new HashMap<>();
            data.put("isBlacklisted", status);
            return new ApiResponse<>(true, data, "블랙리스트 상태 조회 성공");
        } catch (IllegalArgumentException e) {
            return new ApiResponse<>(false, "NOT_FOUND", e.getMessage());
        }
    }

    // 공통 유틸 메서드
    private Long extractUserId(String token) {
        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        return jwtUtil.getUserIdFromToken(jwt);
    }
}