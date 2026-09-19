package com.example.lightsafe.sync;

import com.example.lightsafe.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/sync")
public class SyncController {

    private final PoliceFacilitySyncService policeFacilitySyncService;
    private final CctvSyncService cctvSyncService;
    private final SecurityLightSyncService securityLightSyncService;

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/police-facilities")
    public ResponseEntity<ApiResponse<SyncResultResponse>> syncPoliceFacilities() {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        policeFacilitySyncService.syncPoliceFacilities(),
                        "치안시설 수집 완료"
                )
        );
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/cctvs")
    public ResponseEntity<ApiResponse<SyncResultResponse>> syncCctvs() {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        cctvSyncService.syncCctvs(),
                        "CCTV 수집 완료"
                )
        );
    }
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/security-lights")
    public ResponseEntity<ApiResponse<SyncResultResponse>> syncSecurityLights() {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        securityLightSyncService.syncSecurityLights(),
                        "보안등 수집 완료"
                )
        );
    }
}