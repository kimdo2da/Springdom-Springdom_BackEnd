package com.example.lightsafe.safe;

import com.example.lightsafe.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CctvController {

    private final CctvService cctvService;

    public CctvController(
            CctvService cctvService
    ) {
        this.cctvService =
                cctvService;
    }

    @GetMapping("/cctvs")
    public ApiResponse<List<CctvDto>> getCctvs(
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double maxLat,
            @RequestParam(required = false) Double minLng,
            @RequestParam(required = false) Double maxLng
    ) {
        if (minLat == null
                || maxLat == null
                || minLng == null
                || maxLng == null) {

            return ApiResponse.ok(
                    cctvService.getCctvData(),
                    "CCTV 전체 조회 성공"
            );
        }

        return ApiResponse.ok(
                cctvService.getCctvsInBounds(
                        minLat,
                        maxLat,
                        minLng,
                        maxLng
                ),
                "CCTV 범위 조회 성공"
        );
    }
}