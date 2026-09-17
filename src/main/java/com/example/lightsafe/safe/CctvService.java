package com.example.lightsafe.safe;

import com.example.lightsafe.common.exception.BadRequestException;
import com.example.lightsafe.emergency.Cctv;
import com.example.lightsafe.emergency.CctvRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CctvService {

    private static final double MAX_BBOX_RANGE =
            0.1;

    private final CctvRepository cctvRepository;

    public List<CctvDto> getCctvData() {
        return cctvRepository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<CctvDto> getCctvsInBounds(
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

        return cctvRepository.findInBounds(
                        BigDecimal.valueOf(minLat),
                        BigDecimal.valueOf(maxLat),
                        BigDecimal.valueOf(minLng),
                        BigDecimal.valueOf(maxLng)
                )
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<Cctv> findInBounds(
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

        if (minLat < -90 || maxLat > 90
                || minLng < -180 || maxLng > 180) {

            throw new BadRequestException(
                    "위도 또는 경도 값이 올바르지 않습니다."
            );
        }

        return cctvRepository.findInBounds(
                BigDecimal.valueOf(minLat),
                BigDecimal.valueOf(maxLat),
                BigDecimal.valueOf(minLng),
                BigDecimal.valueOf(maxLng)
        );
    }

    private CctvDto toDto(
            Cctv cctv
    ) {
        CctvDto dto =
                new CctvDto();

        dto.setCctvId(cctv.getCctvId());
        dto.setCctvName(cctv.getCctvName());
        dto.setLatitude(cctv.getLatitude().doubleValue());
        dto.setLongitude(cctv.getLongitude().doubleValue());
        dto.setPurpose(cctv.getPurpose());

        return dto;
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

        if (minLat < -90 || maxLat > 90
                || minLng < -180 || maxLng > 180) {

            throw new BadRequestException(
                    "위도 또는 경도 값이 올바르지 않습니다."
            );
        }
    }
}