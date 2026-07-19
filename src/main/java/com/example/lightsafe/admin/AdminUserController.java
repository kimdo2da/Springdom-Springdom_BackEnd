package com.example.lightsafe.admin;

import com.example.lightsafe.user.ApiResponse;
import com.example.lightsafe.user.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

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
        try {
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
                    new ApiResponse<>(
                            true,
                            data,
                            message
                    )
            );

        } catch (SecurityException e) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(
                            new ApiResponse<>(
                                    false,
                                    "FORBIDDEN",
                                    e.getMessage()
                            )
                    );

        } catch (NoSuchElementException e) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(
                            new ApiResponse<>(
                                    false,
                                    "NOT_FOUND",
                                    e.getMessage()
                            )
                    );

        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                            new ApiResponse<>(
                                    false,
                                    "BAD_REQUEST",
                                    e.getMessage()
                            )
                    );
        }
    }
}