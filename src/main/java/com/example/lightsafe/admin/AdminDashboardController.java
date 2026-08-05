package com.example.lightsafe.admin;

import com.example.lightsafe.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AdminDashboardService
            adminDashboardService;

    @GetMapping("/summary")
    public ResponseEntity<
            ApiResponse<AdminDashboardSummaryResponse>
            > getDashboardSummary() {

        AdminDashboardSummaryResponse data =
                adminDashboardService.getSummary();

        return ResponseEntity.ok(
                ApiResponse.ok(
                        data,
                        "관리자 대시보드 통계 조회 성공"
                )
        );
    }
}