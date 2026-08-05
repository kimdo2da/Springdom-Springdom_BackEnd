package com.example.lightsafe.routehistory;

import com.example.lightsafe.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class RouteHistoryController {

    private final RouteHistoryService routeHistoryService;

    /**
     * 최근 경로 명시적 저장
     * 프론트가 /routes 성공 후 따로 저장하고 싶을 때 사용 가능
     */
    @PostMapping("/recent-routes")
    public ResponseEntity<ApiResponse<RouteHistoryResponse>> saveRecentRoute(
            @RequestBody
            @Valid
            RouteHistoryCreateRequest request
    ) {
        RouteHistoryResponse data =
                routeHistoryService.saveRouteHistory(
                        request
                );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        data,
                        "최근 경로가 저장되었습니다."
                )
        );
    }

    /**
     * 내 최근 경로 조회
     */
    @GetMapping("/recent-routes")
    public ResponseEntity<ApiResponse<List<RouteHistoryResponse>>> getMyRecentRoutes() {
        List<RouteHistoryResponse> data =
                routeHistoryService.getMyRouteHistories();

        return ResponseEntity.ok(
                ApiResponse.ok(
                        data,
                        "최근 경로 조회 성공"
                )
        );
    }

    /**
     * 내 최근 경로 개별 삭제
     */
    @DeleteMapping("/recent-routes/{routeHistoryId}")
    public ResponseEntity<ApiResponse<Void>> deleteMyRecentRoute(
            @PathVariable Long routeHistoryId
    ) {
        routeHistoryService.deleteMyRouteHistory(
                routeHistoryId
        );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        null,
                        "최근 경로가 삭제되었습니다."
                )
        );
    }

    /**
     * 내 최근 경로 전체 삭제
     */
    @DeleteMapping("/recent-routes/all")
    public ResponseEntity<ApiResponse<Void>> deleteAllMyRecentRoutes() {
        routeHistoryService.deleteAllMyRouteHistories();

        return ResponseEntity.ok(
                ApiResponse.ok(
                        null,
                        "모든 최근 경로가 삭제되었습니다."
                )
        );
    }
}