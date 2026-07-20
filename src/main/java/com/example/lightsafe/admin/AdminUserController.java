package com.example.lightsafe.admin;

import com.example.lightsafe.common.response.ApiResponse;
import com.example.lightsafe.user.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final CurrentUserService currentUserService;

    /*
     * PATCH /admin/users/{userId}/status
     *
     * 관리자가 사용자의 역할 또는
     * 블랙리스트 상태를 변경합니다.
     */
    @PatchMapping("/{userId}/status")
    public ResponseEntity<
            ApiResponse<AdminUserStatusResponse>
            > updateUserStatus(
            @PathVariable Long userId,
            @RequestBody AdminUserStatusUpdateRequest request
    ) {
        Long adminUserId =
                currentUserService.getCurrentUserId();

        AdminUserStatusResponse data =
                adminUserService.updateUserStatus(
                        userId,
                        adminUserId,
                        request
                );

        String message;

        if (data.requiresRelogin()) {
            message =
                    "사용자 상태 변경 성공. "
                            + "역할 변경은 대상 사용자가 다시 로그인한 뒤 JWT에 반영됩니다.";
        } else {
            message =
                    "사용자 상태 변경 성공";
        }

        return ResponseEntity.ok(
                ApiResponse.ok(
                        data,
                        message
                )
        );
    }
}