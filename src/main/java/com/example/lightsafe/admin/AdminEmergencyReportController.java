package com.example.lightsafe.admin;

import com.example.lightsafe.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/emergency-reports")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEmergencyReportController {

    private final AdminEmergencyReportService
            adminEmergencyReportService;

    /*
     * GET /admin/emergency-reports
     *
     * 위험구역 활성 여부와 관계없이
     * 전체 긴급신고 이력을 최신순으로 조회합니다.
     */
    @GetMapping
    public ResponseEntity<
            ApiResponse<AdminEmergencyReportPageResponse>
            > getAllEmergencyReports(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size,

            @RequestParam(required = false)
            String status,

            @RequestParam(required = false)
            Boolean isFalseReport,

            @RequestParam(required = false)
            Long dangerZoneId,

            @RequestParam(required = false)
            Long reporterId,

            @RequestParam(required = false)
            String keyword,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME
            )
            LocalDateTime startDate,

            @RequestParam(required = false)
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE_TIME
            )
            LocalDateTime endDate
    ) {
        AdminEmergencyReportPageResponse data =
                adminEmergencyReportService.getAllReports(
                        page,
                        size,
                        status,
                        isFalseReport,
                        dangerZoneId,
                        reporterId,
                        keyword,
                        startDate,
                        endDate
                );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        data,
                        "관리자 전체 긴급신고 이력 조회 성공"
                )
        );
    }
}