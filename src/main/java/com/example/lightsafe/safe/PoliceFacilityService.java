package com.example.lightsafe.safe;

import com.example.lightsafe.common.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PoliceFacilityService {

    private static final double MAX_BBOX_RANGE =
            0.1;

    private final PoliceFacilityRepository policeFacilityRepository;

    @Transactional(readOnly = true)
    public List<PoliceFacilityResponse> getFacilitiesInBounds(
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

        return findInBounds(
                minLat,
                maxLat,
                minLng,
                maxLng
        )
                .stream()
                .map(PoliceFacilityResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PoliceFacility> findInBounds(
            double minLat,
            double maxLat,
            double minLng,
            double maxLng
    ) {
        return policeFacilityRepository.findInBounds(
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
        if (minLat > maxLat || minLng > maxLng) {
            throw new BadRequestException(
                    "지도 범위 값이 올바르지 않습니다."
            );
        }

        if (maxLat - minLat > MAX_BBOX_RANGE
                || maxLng - minLng > MAX_BBOX_RANGE) {

            throw new BadRequestException(
                    "조회 범위가 너무 넓습니다."
            );
        }

        if (!isValidLatitude(minLat)
                || !isValidLatitude(maxLat)
                || !isValidLongitude(minLng)
                || !isValidLongitude(maxLng)) {

            throw new BadRequestException(
                    "위도 또는 경도 값이 올바르지 않습니다."
            );
        }
    }

    private boolean isValidLatitude(
            double latitude
    ) {
        return latitude >= -90 && latitude <= 90;
    }

    private boolean isValidLongitude(
            double longitude
    ) {
        return longitude >= -180 && longitude <= 180;
    }
}