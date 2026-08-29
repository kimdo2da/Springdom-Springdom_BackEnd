package com.example.lightsafe.publicdata;

import com.example.lightsafe.common.exception.BadRequestException;
import com.example.lightsafe.common.response.ApiResponse;
import com.example.lightsafe.safe.CctvService;
import com.example.lightsafe.safe.SecurityLightService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 공공데이터 수집 현황 확인과 수동 실행.
 *
 * 정기 수집은 매월 5일 새벽에 저절로 돌지만, 데이터를 처음 채울 때나
 * 갱신이 제대로 됐는지 확인할 때 관리자가 직접 돌릴 수 있어야 합니다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/public-data")
@PreAuthorize("hasRole('ADMIN')")
public class PublicDataController {

    private final PublicDataProperties properties;

    private final PublicDataSyncService publicDataSyncService;

    private final PublicDataSyncHistoryRepository historyRepository;

    private final GeocodingService geocodingService;

    private final CctvService cctvService;

    private final SecurityLightService securityLightService;

    private final com.example.lightsafe.safe.SecurityLightPointRepository
            securityLightPointRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<PublicDataStatusResponse>> getStatus() {

        List<PublicDataStatusResponse.SyncHistoryItem> histories =
                historyRepository.findTop20ByOrderByStartedAtDesc()
                        .stream()
                        .map(PublicDataStatusResponse.SyncHistoryItem::from)
                        .toList();

        PublicDataStatusResponse data =
                new PublicDataStatusResponse(
                        cctvService.countAll(),
                        securityLightService.countAll(),
                        securityLightPointRepository.countByGeocodedTrue(),
                        geocodingService.countPendingAddresses(),
                        publicDataSyncService.isRunning(),
                        properties.getSyncCron(),
                        histories
                );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        data,
                        "공공데이터 현황 조회 성공"
                )
        );
    }

    /**
     * 수동 수집을 시작시킵니다.
     *
     * POST /admin/public-data/sync/cctv
     * POST /admin/public-data/sync/security-light
     * POST /admin/public-data/sync/geocoding
     *
     * 보안등 전체 수집은 API 를 1900번 가까이 불러 20분이 넘게 걸립니다.
     * 그동안 응답을 붙잡고 있으면 먼저 끊기므로 **시작만 시키고 바로 돌아옵니다.**
     * 진행 상황은 GET /admin/public-data 로 확인하세요.
     */
    @PostMapping("/sync/{source}")
    public ResponseEntity<ApiResponse<String>> sync(
            @PathVariable String source
    ) {
        PublicDataSource target =
                switch (source) {
                    case "cctv" -> PublicDataSource.CCTV;
                    case "security-light" -> PublicDataSource.SECURITY_LIGHT;
                    case "geocoding" -> PublicDataSource.GEOCODING;
                    default -> throw new BadRequestException(
                            "수집 대상은 cctv, security-light, geocoding 중 하나여야 합니다."
                    );
                };

        if (!publicDataSyncService.startAsync(target)) {
            throw new BadRequestException(
                    "이미 다른 수집 작업이 진행 중입니다. 끝난 뒤에 다시 시도해 주세요."
            );
        }

        return ResponseEntity.accepted().body(
                ApiResponse.ok(
                        source,
                        "공공데이터 수집을 시작했습니다. 진행 상황은 GET /admin/public-data 에서 확인하세요."
                )
        );
    }
}
