package com.example.lightsafe.sync;

import com.example.lightsafe.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
// import com.example.lightsafe.geocoding.VWorldGeocodingService;

/* import java.util.LinkedHashMap;
import java.util.Map; */

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/sync")
public class SyncController {

    private final PoliceFacilitySyncService policeFacilitySyncService;
    private final CctvSyncService cctvSyncService;
    private final SecurityLightSyncService securityLightSyncService;
    // private final VWorldGeocodingService vWorldGeocodingService;

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

   /* @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/geocoding-test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testGeocoding(
            @RequestParam String roadAddress,
            @RequestParam(required = false, defaultValue = "") String lotAddress
    ) {

        VWorldGeocodingService.GeocodingSession session =
                vWorldGeocodingService.newSession(
                        "TEST"
                );

        VWorldGeocodingService.GeocodingResult result =
                vWorldGeocodingService.geocode(
                        roadAddress,
                        lotAddress,
                        session
                );

        Map<String, Object> data =
                new LinkedHashMap<>();

        data.put(
                "success",
                result != null
        );

        if (result != null) {

            data.put(
                    "latitude",
                    result.latitude()
            );

            data.put(
                    "longitude",
                    result.longitude()
            );

            data.put(
                    "source",
                    result.source()
            );
        }

        data.put(
                "apiCalls",
                session.apiCalls()
        );

        data.put(
                "cacheHits",
                session.cacheHits()
        );

        data.put(
                "resolvedCount",
                session.resolvedCount()
        );

        data.put(
                "failureCount",
                session.failureCount()
        );

        data.put(
                "limitSkippedCount",
                session.limitSkippedCount()
        );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        data,
                        "VWorld 지오코딩 테스트 완료"
                )
        );
    }*/
}