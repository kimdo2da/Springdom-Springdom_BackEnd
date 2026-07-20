package com.example.lightsafe.safe;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.lightsafe.common.exception.BadRequestException;
import com.example.lightsafe.common.response.ApiResponse;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // CORS 허용
public class RouteController {

    private final RouteService routeService;

    @PostMapping("/routes")
    public ResponseEntity<ApiResponse<List<RouteDto>>>
    getRoutes(
            @RequestBody RouteRequestDto request
    ) {
        List<RouteDto> routeList =
                new ArrayList<>();

        RouteDto safeRoute =
                routeService.getSafeRoute(
                        request,
                        1
                );

        if (safeRoute == null) {
            throw new BadRequestException(
                    "경로 탐색에 실패했습니다."
            );
        }

        routeList.add(
                safeRoute
        );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        routeList,
                        "안전 경로 탐색 완료!"
                )
        );
    }
}