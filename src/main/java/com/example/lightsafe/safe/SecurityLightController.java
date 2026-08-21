package com.example.lightsafe.safe;

import com.example.lightsafe.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SecurityLightController {

    private final SecurityLightService securityLightService;

    public SecurityLightController(
            SecurityLightService securityLightService
    ) {
        this.securityLightService = securityLightService;
    }

    @GetMapping("/security-lights")
    public ApiResponse<List<LocationDto>> getSecurityLights() {
        return ApiResponse.ok(
                securityLightService.getSecurityLightData(),
                "보안등 전체 조회 성공"
        );
    }
}