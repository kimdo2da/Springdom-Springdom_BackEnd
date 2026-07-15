package com.example.lightsafe.safe;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class StreetLampController {

    private final StreetLampService streetLampService;

    public StreetLampController(StreetLampService streetLampService) {
        this.streetLampService = streetLampService;
    }

    @GetMapping("/street-lamps")
    public ApiResponse<List<StreetLampDto>> getStreetLamps() {
        List<StreetLampDto> data = streetLampService.getStreetLampData();
        return new ApiResponse<>(true, data, "보안등 전체 조회 성공");
    }
}
