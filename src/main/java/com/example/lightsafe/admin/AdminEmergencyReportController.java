package com.example.lightsafe.admin;

import com.example.lightsafe.user.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
        try {
            AdminEmergencyReportPageResponse data =
                    adminEmergencyReportService
                            .getAllReports(
                                    page,
                                    size,
                                    status,
                                    isFalseReport,
                                    dangerZoneId,
                                    reporterId,
                                    startDate,
                                    endDate
                            );

            return ResponseEntity.ok(
                    new ApiResponse<>(
                            true,
                            data,
                            "관리자 전체 긴급신고 이력 조회 성공"
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