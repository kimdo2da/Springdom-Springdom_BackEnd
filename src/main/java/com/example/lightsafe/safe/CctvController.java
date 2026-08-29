package com.example.lightsafe.safe;

import com.example.lightsafe.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CctvController {

    private final CctvService cctvService;

    /**
     * 지도에 보이는 범위의 CCTV 를 조회합니다.
     *
     * 전국 25만 건이라 전체 조회는 제공하지 않습니다.
     * 범위를 지정하지 않으면 400 을 돌려줍니다.
     *
     * GET /cctvs?minLatitude=&maxLatitude=&minLongitude=&maxLongitude=
     */
    @GetMapping("/cctvs")
    public ApiResponse<List<CctvDto>> getCctvs(
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

        List<CctvDto> data =
                cctvService.getCctvsInBounds(bounds);

        return ApiResponse.ok(
                data,
                "CCTV 조회 성공"
        );
    }
}
