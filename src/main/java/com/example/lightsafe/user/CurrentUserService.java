package com.example.lightsafe.user;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;

    /**
     * JwtFilter가 SecurityContext principal에 저장한
     * 로그인 사용자 ID를 반환합니다.
     */
    public Long getCurrentUserId() {
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new SecurityException(
                    "로그인이 필요합니다."
            );
        }

        Object principal =
                authentication.getPrincipal();

        if (principal == null
                || "anonymousUser".equals(principal)) {

            throw new SecurityException(
                    "로그인이 필요합니다."
            );
        }

        /*
         * 현재 JwtFilter는 principal에
         * Long userId를 직접 저장합니다.
         */
        if (principal instanceof Long userId) {
            return userId;
        }

        /*
         * 추후 Security 설정 변경으로 principal이
         * 숫자 문자열이 되는 경우도 안전하게 처리합니다.
         */
        if (principal instanceof String value) {
            try {
                return Long.valueOf(value);

            } catch (NumberFormatException e) {
                throw new SecurityException(
                        "로그인 사용자 정보를 확인할 수 없습니다."
                );
            }
        }

        throw new SecurityException(
                "로그인 사용자 정보를 확인할 수 없습니다."
        );
    }

    /**
     * 현재 로그인한 User 엔티티를 반환합니다.
     */
    public User getCurrentUser() {
        Long userId = getCurrentUserId();

        return userRepository
                .findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "로그인 사용자 정보가 존재하지 않습니다."
                        )
                );
    }
}