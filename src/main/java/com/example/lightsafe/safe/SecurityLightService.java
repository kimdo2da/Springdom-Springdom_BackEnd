package com.example.lightsafe.safe;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 보안등(가로등) 조회.
 *
 * 예전에는 자치구별 CSV 24개를 서버 기동 때 메모리에 올려 두고 전건을 내려줬습니다.
 * 전국 데이터로 바꾸면서 180만 건이 되어 그 방식은 쓸 수 없게 됐고,
 * 지금은 표에 저장해 두고 화면 범위만큼만 잘라서 꺼냅니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityLightService {

    /**
     * 한 번에 내려줄 수 있는 최대 개수.
     *
     * 도심에서 범위를 넓게 잡으면 수만 개가 잡혀 응답이 무거워집니다.
     * 지도에 점을 찍는 용도라 이 정도면 화면이 이미 가득 찹니다.
     */
    private static final int MAX_RESULT_SIZE = 20000;

    private final SecurityLightPointRepository securityLightPointRepository;

    @Transactional(readOnly = true)
    public List<LocationDto> getSecurityLightsInBounds(
            MapBounds bounds
    ) {
        return securityLightPointRepository.findLocationsInBounds(
                bounds.minLatitude(),
                bounds.maxLatitude(),
                bounds.minLongitude(),
                bounds.maxLongitude(),
                PageRequest.of(0, MAX_RESULT_SIZE)
        );
    }

    /**
     * 경로 주변 탐색용. 화면 조회보다 넓은 상한을 씁니다.
     */
    @Transactional(readOnly = true)
    public List<LocationDto> getSecurityLightsForRoute(
            MapBounds bounds
    ) {
        return securityLightPointRepository.findLocationsInBounds(
                bounds.minLatitude(),
                bounds.maxLatitude(),
                bounds.minLongitude(),
                bounds.maxLongitude(),
                PageRequest.of(0, MAX_RESULT_SIZE * 5)
        );
    }

    @Transactional(readOnly = true)
    public long countAll() {
        return securityLightPointRepository.count();
    }
}
