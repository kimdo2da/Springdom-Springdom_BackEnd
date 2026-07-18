package com.example.lightsafe.emergency;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/emergency-reports")
public class EmergencyReportController {

    private final EmergencyReportService2 emergencyReportService;

    public EmergencyReportController(EmergencyReportService2 emergencyReportService) {
        this.emergencyReportService = emergencyReportService;
    }

    @PostMapping
    public ResponseEntity<EmergencyApiResponse<EmergencyReportResponse>> createReport(
            @RequestBody @Valid EmergencyReportCreateRequest request
    ) {
        EmergencyReportResponse response = emergencyReportService.createReport(request);

        return ResponseEntity.ok(
                EmergencyApiResponse.ok(
                        response,
                        "EMERGENCY_REPORT_CREATED"
                )
        );
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<EmergencyApiResponse<EmergencyReportResponse>> getReport(
            @PathVariable Long reportId
    ) {
        return ResponseEntity.ok(
                EmergencyApiResponse.ok(
                        emergencyReportService.getReport(reportId)
                )
        );
    }

    @GetMapping("/my")
    public ResponseEntity<EmergencyApiResponse<List<EmergencyReportResponse>>> getMyReports() {
        return ResponseEntity.ok(
                EmergencyApiResponse.ok(
                        emergencyReportService.getMyReports()
                )
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{reportId}/false-report")
    public ResponseEntity<EmergencyApiResponse<EmergencyReportResponse>> markFalseReport(
            @PathVariable Long reportId
    ) {
        return ResponseEntity.ok(
                EmergencyApiResponse.ok(
                        emergencyReportService.markFalseReport(reportId),
                        "FALSE_REPORT_UPDATED"
                )
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{reportId}/status")
    public ResponseEntity<EmergencyApiResponse<EmergencyReportResponse>> updateReportStatus(
            @PathVariable Long reportId,
            @RequestBody @Valid EmergencyReportStatusUpdateRequest request
    ) {
        return ResponseEntity.ok(
                EmergencyApiResponse.ok(
                        emergencyReportService.updateReportStatus(reportId, request),
                        "REPORT_STATUS_UPDATED"
                )
        );
    }
    // 위치 공유가 허용된 친구용 정확한 긴급 위치 조회
    @GetMapping("/{reportId}/shared-location")
    public ResponseEntity<
            EmergencyApiResponse<SharedEmergencyLocationResponse>
            > getSharedLocation(
            @PathVariable Long reportId
    ) {
        return ResponseEntity.ok(
                EmergencyApiResponse.ok(
                        emergencyReportService.getSharedLocation(reportId),
                        "SHARED_EMERGENCY_LOCATION"
                )
        );
    }
}