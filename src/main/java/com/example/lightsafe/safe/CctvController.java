package com.example.lightsafe.safe;

import com.example.lightsafe.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CctvController {

    private final CctvService cctvService;

    public CctvController(
            CctvService cctvService
    ) {
        this.cctvService = cctvService;
    }

    @GetMapping("/cctvs")
    public ApiResponse<List<CctvDto>> getCctvs() {

        List<CctvDto> data =
                cctvService.getCctvData();

        return ApiResponse.ok(
                data,
                "CCTV 전체 조회 성공"
        );
    }
}