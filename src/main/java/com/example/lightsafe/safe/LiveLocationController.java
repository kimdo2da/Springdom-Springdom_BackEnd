package com.example.lightsafe.safe;

import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/location")
public class LiveLocationController {

    private LiveLocationResponse latestLocation = null;

    @PostMapping("/update")
    public ApiResponse<LiveLocationResponse> updateLocation(@RequestBody LiveLocationRequest request) {
        if (request.getLatitude() == null || request.getLongitude() == null) {
            return new ApiResponse<>(
                    false,
                    null,
                    "위도와 경도는 필수입니다."
            );
        }

        latestLocation = new LiveLocationResponse(
                request.getLatitude(),
                request.getLongitude(),
                LocalDateTime.now()
        );

        return new ApiResponse<>(
                true,
                latestLocation,
                "위치 업데이트 성공"
        );
    }

    @GetMapping("/latest")
    public ApiResponse<LiveLocationResponse> getLatestLocation() {
        if (latestLocation == null) {
            return new ApiResponse<>(
                    false,
                    null,
                    "아직 수신된 위치 정보가 없습니다."
            );
        }

        return new ApiResponse<>(
                true,
                latestLocation,
                "최신 위치 조회 성공"
        );
    }
}