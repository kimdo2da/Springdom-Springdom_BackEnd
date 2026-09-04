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
}