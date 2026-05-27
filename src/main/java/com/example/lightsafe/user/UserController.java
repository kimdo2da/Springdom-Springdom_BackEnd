package com.example.lightsafe.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // 1. 회원가입 - POST /users/register
    @PostMapping("/register")
    public ApiResponse<Map<String, Long>> register(@RequestBody UserRegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            return new ApiResponse<>(false, "CONFLICT", "이미 존재하는 아이디입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User newUser = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(encodedPassword)
                .nickname(request.getNickname())
                .falseReportCount(0)
                .isBlacklisted(false)
                .role("USER") // 회원가입 시 기본 권한을 USER로 명확히 할당
                .build();

        User savedUser = userRepository.save(newUser);
        Map<String, Long> data = new HashMap<>();
        data.put("userId", savedUser.getUserId());
        return new ApiResponse<>(true, data, "OK");
    }

    // 2. 로그인 - POST /users/login
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody UserLoginRequest request) {
        Optional<User> userOptional = userRepository.findByUsername(request.getUsernameOrEmail());

        if (userOptional.isEmpty() || !passwordEncoder.matches(request.getPassword(), userOptional.get().getPassword())) {
            return new ApiResponse<>(false, "UNAUTHORIZED", "아이디 또는 비밀번호가 일치하지 않습니다.");
        }

        User user = userOptional.get();
        Map<String, Object> data = new HashMap<>();
        String realToken = jwtUtil.generateToken(user.getUserId(), user.getUsername());

        data.put("accessToken", realToken);
        data.put("userId", user.getUserId());
        data.put("username", user.getUsername());
        return new ApiResponse<>(true, data, "로그인 성공");
    }

    // 3. 특정 사용자 조회 - GET /users/{userId}
    @GetMapping("/{userId}")
    public ApiResponse<Map<String, Object>> getProfile(
            @PathVariable Long userId,
            @RequestHeader("Authorization") String token) {

        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        Long loginUserId = jwtUtil.getUserIdFromToken(jwt);

        Optional<User> loginUserOptional = userRepository.findById(loginUserId);
        if (loginUserOptional.isEmpty()) {
            return new ApiResponse<>(false, "UNAUTHORIZED", "인증에 실패했습니다.");
        }

        // 보안 검증: 본인 정보이거나, 요청자가 관리자(ADMIN)인 경우에만 조회 허용
        User loginUser = loginUserOptional.get();
        if (!loginUserId.equals(userId) && !"ADMIN".equals(loginUser.getRole())) {
            return new ApiResponse<>(false, "FORBIDDEN", "타인의 프로필을 조회할 권한이 없습니다.");
        }

        Optional<User> targetOptional = userRepository.findById(userId);
        if (targetOptional.isEmpty()) {
            return new ApiResponse<>(false, "NOT_FOUND", "존재하지 않는 유저입니다.");
        }

        User targetUser = targetOptional.get();
        Map<String, Object> data = new HashMap<>();
        data.put("userId", targetUser.getUserId());
        data.put("username", targetUser.getUsername());
        data.put("email", targetUser.getEmail());
        data.put("nickname", targetUser.getNickname());
        data.put("role", targetUser.getRole());
        data.put("phone", targetUser.getPhone());
        data.put("falseReportCount", targetUser.getFalseReportCount());
        data.put("isBlacklisted", targetUser.isBlacklisted());
        data.put("createdAt", targetUser.getCreatedAt());
        return new ApiResponse<>(true, data, "프로필 조회 성공");
    }

    // 4. 사용자 정보 수정 - PUT /users/{userId}
    @PutMapping("/{userId}")
    public ApiResponse<Map<String, Object>> updateProfile(
            @PathVariable Long userId,
            @RequestBody UserUpdateRequest request,
            @RequestHeader("Authorization") String token) {

        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        Long loginUserId = jwtUtil.getUserIdFromToken(jwt);

        // 본인 또는 관리자 검증을 위한 로그인 유저 조회
        Optional<User> loginUserOptional = userRepository.findById(loginUserId);
        if (loginUserOptional.isEmpty()) {
            return new ApiResponse<>(false, "UNAUTHORIZED", "인증에 실패했습니다.");
        }

        // 보안 검증: 본인 정보이거나, 요청자가 관리자(ADMIN)인 경우에만 수정 허용
        User loginUser = loginUserOptional.get();
        if (!loginUserId.equals(userId) && !"ADMIN".equals(loginUser.getRole())) {
            return new ApiResponse<>(false, "FORBIDDEN", "본인 또는 관리자만 수정할 수 있습니다.");
        }

        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return new ApiResponse<>(false, "NOT_FOUND", "존재하지 않는 유저입니다.");
        }

        User user = userOptional.get();
        // 유저 닉네임 수정
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.setNickname(request.getNickname());
        }
        // 유저 비밀번호 수정
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        // 이메일 변경
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            // 이메일 형식이 맞는지 검사 (예: test@naver.com)
            String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
            if (!request.getEmail().matches(emailRegex)) {
                return new ApiResponse<>(false, "BAD_REQUEST", "올바른 이메일 형식이 아닙니다.");
            }
            // 이메일 중복 검사
            if (!user.getEmail().equals(request.getEmail())) {
                if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                    return new ApiResponse<>(false, "CONFLICT", "이미 사용 중인 이메일입니다.");
                }
                user.setEmail(request.getEmail());
            }
        }

        // 전화번호 변경
        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            // 전화번호 형식이 맞는지 검사 (반드시 010-숫자4자리-숫자4자리 구조여야 함)
            String phoneRegex = "^010-\\d{4}-\\d{4}$";
            if (!request.getPhone().matches(phoneRegex)) {
                return new ApiResponse<>(false, "BAD_REQUEST", "전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678)");
            }
            user.setPhone(request.getPhone());
        }

        userRepository.save(user);

        // 변경된 데이터를 프론트엔드로 다시 보내주기
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getUserId());
        data.put("nickname", user.getNickname());
        data.put("email", user.getEmail());
        data.put("phone", user.getPhone());
        return new ApiResponse<>(true, data, "프로필 수정 성공");
    }

    // 5. 특정 사용자 삭제 - DELETE /users/{userId}
    @DeleteMapping("/{userId}")
    public ApiResponse<String> deleteUser(
            @PathVariable Long userId,
            @RequestHeader("Authorization") String token) {

        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        Long loginUserId = jwtUtil.getUserIdFromToken(jwt);

        // 본인 또는 관리자 검증을 위한 로그인 유저 조회
        Optional<User> loginUserOptional = userRepository.findById(loginUserId);
        if (loginUserOptional.isEmpty()) {
            return new ApiResponse<>(false, "UNAUTHORIZED", "인증에 실패했습니다.");
        }

        // 보안 검증: 본인 정보이거나, 요청자가 관리자(ADMIN)인 경우에만 삭제 허용
        User loginUser = loginUserOptional.get();
        if (!loginUserId.equals(userId) && !"ADMIN".equals(loginUser.getRole())) {
            return new ApiResponse<>(false, "FORBIDDEN", "본인 또는 관리자만 탈퇴 처리할 수 있습니다.");
        }

        if (!userRepository.existsById(userId)) {
            return new ApiResponse<>(false, "NOT_FOUND", "존재하지 않는 유저입니다.");
        }

        userRepository.deleteById(userId);
        return new ApiResponse<>(true, null, "회원 탈퇴 성공");
    }
    // 6. 로그아웃 - POST /users/logout
    @PostMapping("/logout")
    public ApiResponse<String> logout() {
        return new ApiResponse<>(true, null, "로그아웃 성공");
    }

    // 7. 모든 사용자 조회 - GET /users
    @GetMapping("")
    public ApiResponse<Object> getAllUsers(@RequestHeader("Authorization") String token) {

        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        Long loginUserId = jwtUtil.getUserIdFromToken(jwt);

        Optional<User> loginUserOptional = userRepository.findById(loginUserId);
        if (loginUserOptional.isEmpty() || !"ADMIN".equals(loginUserOptional.get().getRole())) {
            return new ApiResponse<>(false, "FORBIDDEN", "관리자 권한이 필요합니다.");
        }

        List<User> users = userRepository.findAll();
        List<Map<String, Object>> data = new ArrayList<>();

        for (User user : users) {
            Map<String, Object> uMap = new HashMap<>();
            uMap.put("userId", user.getUserId());
            uMap.put("username", user.getUsername());
            uMap.put("email", user.getEmail());
            uMap.put("nickname", user.getNickname());
            uMap.put("phone", user.getPhone());
            uMap.put("falseReportCount", user.getFalseReportCount());
            uMap.put("isBlacklisted", user.isBlacklisted());
            uMap.put("role", user.getRole());
            uMap.put("createdAt", user.getCreatedAt());
            data.add(uMap);
        }

        return new ApiResponse<>(true, data, "전체 사용자 조회 성공");
    }

    // 8. 인증 상태 확인 - GET /users/auth-check
    @GetMapping("/auth-check")
    public ApiResponse<Map<String, Object>> checkAuth(@RequestHeader("Authorization") String token) {
        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        Long userId = jwtUtil.getUserIdFromToken(jwt);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        return new ApiResponse<>(true, data, "인증이 유효한 사용자입니다.");
    }

    // 9. 내 허위신고 횟수 조회 - GET /users/fake
    @GetMapping("/fake")
    public ApiResponse<Map<String, Object>> getFakeReportCount(@RequestHeader("Authorization") String token) {
        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        Long userId = jwtUtil.getUserIdFromToken(jwt);

        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return new ApiResponse<>(false, "NOT_FOUND", "존재하지 않는 유저입니다.");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("falseReportCount", userOptional.get().getFalseReportCount());
        return new ApiResponse<>(true, data, "허위신고 횟수 조회 성공");
    }

    // 10. 내 블랙리스트 여부 조회 - GET /users/black
    @GetMapping("/black")
    public ApiResponse<Map<String, Object>> getBlacklistStatus(@RequestHeader("Authorization") String token) {
        String jwt = token.startsWith("Bearer ") ? token.substring(7) : token;
        Long userId = jwtUtil.getUserIdFromToken(jwt);

        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            return new ApiResponse<>(false, "NOT_FOUND", "존재하지 않는 유저입니다.");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("isBlacklisted", userOptional.get().isBlacklisted());
        return new ApiResponse<>(true, data, "블랙리스트 상태 조회 성공");
    }
}