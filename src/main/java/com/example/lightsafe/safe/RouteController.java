package com.example.lightsafe.safe;

import com.example.lightsafe.common.exception.BadRequestException;
import com.example.lightsafe.common.response.ApiResponse;
import com.example.lightsafe.routehistory.RouteHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;
    private final RouteHistoryService routeHistoryService;

    @PostMapping("/routes")
    public ResponseEntity<ApiResponse<List<RouteDto>>> getRoutes(
            @RequestBody RouteRequestDto request
    ) {
        List<RouteDto> routeList =
                routeService.getTop3SafeRoutes(
                        request
                );

        if (routeList == null
                || routeList.isEmpty()) {

            throw new BadRequestException(
                    "경로 탐색에 실패했습니다."
            );
        }

        routeHistoryService.saveFromRouteSearchIfAuthenticated(
                request
        );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        routeList,
                        "안전 경로 3개 탐색 완료"
                )
        );
    }
}