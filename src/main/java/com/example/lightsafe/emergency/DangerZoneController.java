package com.example.lightsafe.emergency;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.lightsafe.common.response.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/danger-zones")
public class DangerZoneController {

    private final DangerZoneService dangerZoneService;
    private final EmergencyReportService2 emergencyReportService;

    public DangerZoneController(
            DangerZoneService dangerZoneService,
            EmergencyReportService2 emergencyReportService
    ) {
        this.dangerZoneService = dangerZoneService;
        this.emergencyReportService = emergencyReportService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PublicDangerZoneResponse>>> getDangerZones() {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        dangerZoneService.getDangerZones()
                )
        );
    }

    @GetMapping("/{dangerZoneId}")
    public ResponseEntity<ApiResponse<PublicDangerZoneResponse>> getDangerZoneDetail(
            @PathVariable Long dangerZoneId
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        dangerZoneService.getDangerZoneDetail(dangerZoneId)
                )
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{dangerZoneId}/level")
    public ResponseEntity<ApiResponse<DangerZoneResponse>> updateDangerLevel(
            @PathVariable Long dangerZoneId,
            @RequestBody @Valid DangerLevelUpdateRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        dangerZoneService.updateDangerLevel(dangerZoneId, request),
                        "DANGER_LEVEL_UPDATED"
                )
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{dangerZoneId}/deactivate")
    public ResponseEntity<ApiResponse<DangerZoneResponse>> deactivateDangerZone(
            @PathVariable Long dangerZoneId
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        dangerZoneService.deactivateDangerZone(dangerZoneId),
                        "DANGER_ZONE_DEACTIVATED"
                )
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{dangerZoneId}/reports")
    public ResponseEntity<ApiResponse<List<EmergencyReportResponse>>> getReportsByDangerZone(
            @PathVariable Long dangerZoneId
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        emergencyReportService.getReportsByDangerZone(dangerZoneId)
                )
        );
    }
}