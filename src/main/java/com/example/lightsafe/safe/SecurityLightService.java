package com.example.lightsafe.safe;

import com.example.lightsafe.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SecurityLightService {

    private static final double MAX_BBOX_RANGE = 0.1;

    private final StreetLampRepository streetLampRepository;

    /**
     * 지도 화면용 보안등 조회
     * 동일 좌표 데이터는 하나만 반환합니다.
     */
    public List<LocationDto> getSecurityLightsInBounds(
            double minLat,
            double maxLat,
            double minLng,
            double maxLng
    ) {
        validateBounds(
                minLat,
                maxLat,
                minLng,
                maxLng
        );

        List<StreetLamp> lamps =
                streetLampRepository.findInBounds(
                        BigDecimal.valueOf(minLat),
                        BigDecimal.valueOf(maxLat),
                        BigDecimal.valueOf(minLng),
                        BigDecimal.valueOf(maxLng)
                );

        List<LocationDto> result =
                new ArrayList<>();

        Set<String> uniqueLocations =
                new HashSet<>();

        for (StreetLamp lamp : lamps) {

            if (lamp.getLatitude() == null
                    || lamp.getLongitude() == null) {
                continue;
            }

            double latitude =
                    lamp.getLatitude().doubleValue();

            double longitude =
                    lamp.getLongitude().doubleValue();

            String locationKey =
                    latitude + "_" + longitude;

            /*
             * 같은 좌표에 여러 API 행이 존재해도
             * 지도에서는 마커 하나만 반환합니다.
             */
            if (uniqueLocations.add(locationKey)) {
                result.add(
                        new LocationDto(
                                latitude,
                                longitude
                        )
                );
            }
        }

        return result;
    }

    /**
     * RouteService 내부 조회용.
     *
     * 경로에서 만든 bbox로 DB 후보만 조회합니다.
     * 실제 50m 판정과 좌표 중복 제거는 RouteService에서 수행합니다.
     */
    public List<StreetLamp> findInBounds(
            double minLat,
            double maxLat,
            double minLng,
            double maxLng
    ) {
        validateCoordinateBounds(
                minLat,
                maxLat,
                minLng,
                maxLng
        );

        return streetLampRepository.findInBounds(
                BigDecimal.valueOf(minLat),
                BigDecimal.valueOf(maxLat),
                BigDecimal.valueOf(minLng),
                BigDecimal.valueOf(maxLng)
        );
    }

    private void validateBounds(
            double minLat,
            double maxLat,
            double minLng,
            double maxLng
    ) {
        validateCoordinateBounds(
                minLat,
                maxLat,
                minLng,
                maxLng
        );

        if (maxLat - minLat > MAX_BBOX_RANGE
                || maxLng - minLng > MAX_BBOX_RANGE) {

            throw new BadRequestException(
                    "조회 범위가 너무 큽니다."
            );
        }
    }

    private void validateCoordinateBounds(
            double minLat,
            double maxLat,
            double minLng,
            double maxLng
    ) {
        if (minLat > maxLat
                || minLng > maxLng) {

            throw new BadRequestException(
                    "지도 범위 값이 올바르지 않습니다."
            );
        }

        if (minLat < -90
                || maxLat > 90
                || minLng < -180
                || maxLng > 180) {

            throw new BadRequestException(
                    "위도 또는 경도 값이 올바르지 않습니다."
            );
        }
    }
}