package com.example.lightsafe.safe;

import com.example.lightsafe.common.exception.BadRequestException;
import com.example.lightsafe.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class SecurityLightController {

    private final SecurityLightService securityLightService;

    @GetMapping("/security-lights")
    public ApiResponse<List<LocationDto>> getSecurityLights(
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double maxLat,
            @RequestParam(required = false) Double minLng,
            @RequestParam(required = false) Double maxLng
    ) {

        if (minLat == null
                || maxLat == null
                || minLng == null
                || maxLng == null) {

            throw new BadRequestException(
                    "minLat, maxLat, minLng, maxLng를 모두 입력해야 합니다."
            );
        }

        return ApiResponse.ok(
                securityLightService.getSecurityLightsInBounds(
                        minLat,
                        maxLat,
                        minLng,
                        maxLng
                ),
                "보안등 범위 조회 성공"
        );
    }
}