package com.example.lightsafe.safe;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // CORS 허용
public class RouteController {

    private final RouteService routeService;

    @PostMapping("/routes")
    public ResponseEntity<ApiResponse> getRoutes(@RequestBody RouteRequestDto request) {

        List<RouteDto> routeList = new ArrayList<>();

        // 서비스에서 카카오내비 API를 통해 안전 경로 획득
        RouteDto safeRoute = routeService.getSafeRoute(request, 1);

        if (safeRoute != null) {
            routeList.add(safeRoute);
            ApiResponse response = new ApiResponse(true, routeList, "안전 경로 탐색 완료!");
            return ResponseEntity.ok(response);
        } else {
            ApiResponse response = new ApiResponse(false, null, "경로 탐색에 실패했습니다.");
            return ResponseEntity.badRequest().body(response);
        }
    }
}