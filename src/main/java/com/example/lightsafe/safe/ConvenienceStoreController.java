package com.example.lightsafe.safe;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ConvenienceStoreController {

    private final ConvenienceStoreService convenienceStoreService;

    public ConvenienceStoreController(ConvenienceStoreService convenienceStoreService) {
        this.convenienceStoreService = convenienceStoreService;
    }

    // 예: GET /safe-places?lat=37.5665&lng=126.9780&radius=1000
    @GetMapping("/safe-places")
    public ApiResponse<List<ConvenienceStoreDto>> getNearbyConvenienceStores(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "1000") int radius
    ) {
        List<ConvenienceStoreDto> data = convenienceStoreService.searchNearby(lat, lng, radius);
        return new ApiResponse<>(true, data, "주변 편의점 조회 성공");
    }
}
