package com.example.lightsafe.safe;

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

    /**
     * 지도에 보이는 범위의 보안등(가로등)을 조회합니다.
     *
     * 전국 180만 건이라 전체 조회는 제공하지 않습니다.
     * 범위를 지정하지 않으면 400 을 돌려줍니다.
     *
     * GET /security-lights?minLatitude=&maxLatitude=&minLongitude=&maxLongitude=
     */
    @GetMapping("/security-lights")
    public ApiResponse<List<LocationDto>> getSecurityLights(
            @RequestParam(required = false) Double minLatitude,
            @RequestParam(required = false) Double maxLatitude,
            @RequestParam(required = false) Double minLongitude,
            @RequestParam(required = false) Double maxLongitude
    ) {
        MapBounds bounds =
                MapBounds.of(
                        minLatitude,
                        maxLatitude,
                        minLongitude,
                        maxLongitude
                );

        List<LocationDto> data =
                securityLightService.getSecurityLightsInBounds(bounds);

        return ApiResponse.ok(
                data,
                "보안등 조회 성공"
        );
    }
}
