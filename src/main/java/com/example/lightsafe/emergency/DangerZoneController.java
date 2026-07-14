package com.example.lightsafe.emergency;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;


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
    public ResponseEntity<EmergencyApiResponse<List<PublicDangerZoneResponse>>> getDangerZones() {
        return ResponseEntity.ok(
                EmergencyApiResponse.ok(
                        dangerZoneService.getDangerZones()
                )
        );
    }

    @GetMapping("/{dangerZoneId}")
    public ResponseEntity<EmergencyApiResponse<PublicDangerZoneResponse>> getDangerZoneDetail(
            @PathVariable Long dangerZoneId
    ) {
        return ResponseEntity.ok(
                EmergencyApiResponse.ok(
                        dangerZoneService.getDangerZoneDetail(dangerZoneId)
                )
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{dangerZoneId}/level")
    public ResponseEntity<EmergencyApiResponse<DangerZoneResponse>> updateDangerLevel(
            @PathVariable Long dangerZoneId,
            @RequestBody @Valid DangerLevelUpdateRequest request
    ) {
        return ResponseEntity.ok(
                EmergencyApiResponse.ok(
                        dangerZoneService.updateDangerLevel(dangerZoneId, request),
                        "DANGER_LEVEL_UPDATED"
                )
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{dangerZoneId}/deactivate")
    public ResponseEntity<EmergencyApiResponse<DangerZoneResponse>> deactivateDangerZone(
            @PathVariable Long dangerZoneId
    ) {
        return ResponseEntity.ok(
                EmergencyApiResponse.ok(
                        dangerZoneService.deactivateDangerZone(dangerZoneId),
                        "DANGER_ZONE_DEACTIVATED"
                )
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{dangerZoneId}/reports")
    public ResponseEntity<EmergencyApiResponse<List<EmergencyReportResponse>>> getReportsByDangerZone(
            @PathVariable Long dangerZoneId
    ) {
        return ResponseEntity.ok(
                EmergencyApiResponse.ok(
                        emergencyReportService.getReportsByDangerZone(dangerZoneId)
                )
        );
    }
}