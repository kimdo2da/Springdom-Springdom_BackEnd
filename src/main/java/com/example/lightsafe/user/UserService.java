package com.example.lightsafe.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional // 에러 발생 시 DB 롤백 방어막
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    // ==========================================
    // [공통 기능] 타 도메인(커뮤니티 등) 연동용 메서드
    // ==========================================
    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다. 유저 ID: " + userId));
    }

    @Transactional(readOnly = true)
    public boolean existsUser(Long userId) {
        return userRepository.existsById(userId);
    }

    // ==========================================
    // [비즈니스 로직] 유저 도메인 핵심 기능
    // ==========================================

    // 1. 회원가입
    public Long register(UserRegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalStateException("이미 존재하는 아이디입니다.");
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User newUser = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(encodedPassword)
                .nickname(request.getNickname())
                .falseReportCount(0)
                .isBlacklisted(false)
                .role("USER")
                .build();

        return userRepository.save(newUser).getUserId();
    }

    // 2. 로그인
    @Transactional(readOnly = true)
    public Map<String, Object> login(UserLoginRequest request) {
        User user = userRepository.findByUsername(request.getUsernameOrEmail())
                .orElseThrow(() -> new SecurityException("아이디 또는 비밀번호가 일치하지 않습니다."));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new SecurityException("아이디 또는 비밀번호가 일치하지 않습니다.");
        }

        String realToken = jwtUtil.generateToken(user.getUserId(), user.getRole());

        Map<String, Object> data = new HashMap<>();
        data.put("accessToken", realToken);
        data.put("userId", user.getUserId());
        data.put("username", user.getUsername());
        return data;
    }

    // 3. 프로필 조회
    @Transactional(readOnly = true)
    public Map<String, Object> getProfile(Long targetUserId, Long loginUserId) {
        User loginUser = getUserById(loginUserId);

        if (!loginUserId.equals(targetUserId) && !"ADMIN".equals(loginUser.getRole())) {
            throw new SecurityException("타인의 프로필을 조회할 권한이 없습니다.");
        }

        User targetUser = getUserById(targetUserId);

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
        return data;
    }

    // 4. 프로필 수정
    public Map<String, Object> updateProfile(Long targetUserId, Long loginUserId, UserUpdateRequest request) {
        User loginUser = getUserById(loginUserId);

        if (!loginUserId.equals(targetUserId) && !"ADMIN".equals(loginUser.getRole())) {
            throw new SecurityException("본인 또는 관리자만 수정할 수 있습니다.");
        }

        User user = getUserById(targetUserId);

        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            user.setNickname(request.getNickname());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
            if (!request.getEmail().matches(emailRegex)) {
                throw new IllegalArgumentException("올바른 이메일 형식이 아닙니다.");
            }
            if (!user.getEmail().equals(request.getEmail())) {
                if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                    throw new IllegalStateException("이미 사용 중인 이메일입니다.");
                }
                user.setEmail(request.getEmail());
            }
        }

        if (request.getPhone() != null && !request.getPhone().isBlank()) {
            String phoneRegex = "^010-\\d{4}-\\d{4}$";
            if (!request.getPhone().matches(phoneRegex)) {
                throw new IllegalArgumentException("전화번호 형식이 올바르지 않습니다. (예: 010-1234-5678)");
            }
            user.setPhone(request.getPhone());
        }

        userRepository.save(user);

        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getUserId());
        data.put("nickname", user.getNickname());
        data.put("email", user.getEmail());
        data.put("phone", user.getPhone());
        return data;
    }

    // 5. 삭제
    public void deleteUser(Long targetUserId, Long loginUserId) {
        User loginUser = getUserById(loginUserId);

        if (!loginUserId.equals(targetUserId) && !"ADMIN".equals(loginUser.getRole())) {
            throw new SecurityException("본인 또는 관리자만 탈퇴 처리할 수 있습니다.");
        }

        if (!userRepository.existsById(targetUserId)) {
            throw new IllegalArgumentException("존재하지 않는 유저입니다.");
        }

        userRepository.deleteById(targetUserId);
    }

    // 7. 전체 조회 (관리자용)
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllUsers(Long loginUserId) {
        User loginUser = getUserById(loginUserId);

        if (!"ADMIN".equals(loginUser.getRole())) {
            throw new SecurityException("관리자 권한이 필요합니다.");
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
        return data;
    }

    // 9. 내 허위신고 조회
    @Transactional(readOnly = true)
    public int getFakeReportCount(Long loginUserId) {
        User user = getUserById(loginUserId);
        return user.getFalseReportCount();
    }

    // 10. 내 블랙리스트 상태 조회
    @Transactional(readOnly = true)
    public boolean getBlacklistStatus(Long loginUserId) {
        User user = getUserById(loginUserId);
        return user.isBlacklisted();
    }

    // ==========================================
    // [공통 기능] 현재 로그인한 사용자 정보 가져오기 (게시판 연동용)
    // ==========================================
    @Transactional(readOnly = true)
    public User getCurrentUser() {
        // 1. 스프링 시큐리티 컨텍스트에서 현재 인증(토큰) 정보를 가져옵니다.
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 2. 인증 정보가 없거나 익명 사용자(비로그인)인 경우 null 반환
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }

        // 3. 토큰에서 추출된 principal(여기서는 userId 문자열 또는 Long 타입으로 가정)을 가져옵니다.
        // JwtFilter에서 principal을 어떻게 세팅했는지에 따라 형변환 로직은 조금 달라질 수 있습니다.
        Long loginUserId = Long.valueOf(authentication.getName());

        // 4. DB에서 해당 유저 엔티티를 찾아서 반환합니다.
        return userRepository.findById(loginUserId)
                .orElseThrow(() -> new IllegalArgumentException("토큰에 해당하는 유저를 찾을 수 없습니다."));
    }

}