package com.example.lightsafe.admin;

import com.example.lightsafe.user.User;
import com.example.lightsafe.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

    private static final Set<String> ALLOWED_ROLES =
            Set.of(
                    "USER",
                    "ADMIN"
            );

    private final UserRepository userRepository;

    public AdminUserStatusResponse updateUserStatus(
            Long targetUserId,
            Long adminUserId,
            AdminUserStatusUpdateRequest request
    ) {
        if (targetUserId == null) {
            throw new IllegalArgumentException(
                    "대상 사용자 ID는 필수입니다."
            );
        }

        if (request == null) {
            throw new IllegalArgumentException(
                    "요청 본문은 필수입니다."
            );
        }

        /*
         * 관리자가 실수로 자기 역할을 USER로 바꾸거나
         * 자기 계정을 블랙리스트 처리하는 것을 막습니다.
         */
        if (targetUserId.equals(adminUserId)) {
            throw new SecurityException(
                    "관리자는 자신의 역할 또는 블랙리스트 상태를 변경할 수 없습니다."
            );
        }

        if (request.role() == null
                && request.isBlacklisted() == null) {

            throw new IllegalArgumentException(
                    "role 또는 isBlacklisted 중 하나 이상을 입력해야 합니다."
            );
        }

        User targetUser =
                userRepository.findById(targetUserId)
                        .orElseThrow(() ->
                                new NoSuchElementException(
                                        "존재하지 않는 사용자입니다."
                                )
                        );

        boolean roleChanged = false;

        /*
         * role이 요청에 포함된 경우에만 변경합니다.
         */
        if (request.role() != null) {
            String normalizedRole =
                    request.role()
                            .trim()
                            .toUpperCase(Locale.ROOT);

            if (!ALLOWED_ROLES.contains(normalizedRole)) {
                throw new IllegalArgumentException(
                        "role은 USER 또는 ADMIN만 사용할 수 있습니다."
                );
            }

            if (!normalizedRole.equals(targetUser.getRole())) {
                targetUser.setRole(normalizedRole);
                roleChanged = true;
            }
        }

        /*
         * isBlacklisted가 요청에 포함된 경우에만 변경합니다.
         *
         * 블랙리스트를 해제해도 허위신고 횟수는 유지합니다.
         */
        if (request.isBlacklisted() != null) {
            targetUser.setBlacklisted(
                    request.isBlacklisted()
            );
        }

        User savedUser =
                userRepository.save(targetUser);

        return new AdminUserStatusResponse(
                savedUser.getUserId(),
                savedUser.getUsername(),
                savedUser.getNickname(),
                savedUser.getRole(),
                savedUser.isBlacklisted(),
                savedUser.getFalseReportCount(),
                roleChanged
        );
    }
}