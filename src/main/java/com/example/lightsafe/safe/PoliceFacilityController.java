package com.example.lightsafe.safe;

import com.example.lightsafe.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PoliceFacilityController {

    private final PoliceFacilityService policeFacilityService;

    @GetMapping("/police-facilities")
    public ResponseEntity<ApiResponse<List<PoliceFacilityResponse>>> getPoliceFacilities(
            @RequestParam double minLat,
            @RequestParam double maxLat,
            @RequestParam double minLng,
            @RequestParam double maxLng
    ) {
        return ResponseEntity.ok(
                ApiResponse.ok(
                        policeFacilityService.getFacilitiesInBounds(
                                minLat,
                                maxLat,
                                minLng,
                                maxLng
                        ),
                        "치안시설 조회 성공"
                )
        );
    }
}