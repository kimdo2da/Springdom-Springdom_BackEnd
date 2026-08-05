package com.example.lightsafe.emergency;

import com.example.lightsafe.common.exception.BadRequestException;
import com.example.lightsafe.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class DangerZoneService {

    private final DangerZoneRepository dangerZoneRepository;

    public DangerZoneService(
            DangerZoneRepository dangerZoneRepository
    ) {
        this.dangerZoneRepository = dangerZoneRepository;
    }

    @Transactional
    public List<PublicDangerZoneResponse> getDangerZones() {
        LocalDateTime now = LocalDateTime.now();

        dangerZoneRepository.deactivateExpiredZones(now);

        return dangerZoneRepository.findPublicActiveZones(now)
                .stream()
                .map(PublicDangerZoneResponse::from)
                .toList();
    }

    @Transactional
    public PublicDangerZoneResponse getDangerZoneDetail(Long dangerZoneId) {
        LocalDateTime now = LocalDateTime.now();

        dangerZoneRepository.deactivateExpiredZones(now);

        DangerZone dangerZone = dangerZoneRepository.findById(dangerZoneId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "존재하지 않는 위험구역입니다. id=" + dangerZoneId
                        )
                );

        if (!Boolean.TRUE.equals(dangerZone.getIsActive())
                || isExpired(dangerZone, now)) {

            throw new NotFoundException(
                    "존재하지 않는 위험구역입니다. id=" + dangerZoneId
            );
        }

        return PublicDangerZoneResponse.from(dangerZone);
    }

    @Transactional
    public DangerZoneResponse updateDangerLevel(
            Long dangerZoneId,
            DangerLevelUpdateRequest request
    ) {
        DangerZone dangerZone = dangerZoneRepository.findById(dangerZoneId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "존재하지 않는 위험구역입니다. id=" + dangerZoneId
                        )
                );

        dangerZone.setDangerLevel(
                normalizeDangerLevel(
                        request.dangerLevel()
                )
        );

        return DangerZoneResponse.from(dangerZone);
    }

    @Transactional
    public DangerZoneResponse deactivateDangerZone(Long dangerZoneId) {
        DangerZone dangerZone = dangerZoneRepository.findById(dangerZoneId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "존재하지 않는 위험구역입니다. id=" + dangerZoneId
                        )
                );

        dangerZone.setIsActive(false);
        dangerZone.setExpiredAt(LocalDateTime.now());

        return DangerZoneResponse.from(dangerZone);
    }

    private boolean isExpired(
            DangerZone dangerZone,
            LocalDateTime now
    ) {
        return dangerZone.getExpiredAt() != null
                && !dangerZone.getExpiredAt().isAfter(now);
    }

    private DangerLevel normalizeDangerLevel(String dangerLevel) {
        if (dangerLevel == null || dangerLevel.isBlank()) {
            throw new BadRequestException(
                    "dangerLevel은 필수입니다."
            );
        }

        try {
            return DangerLevel.valueOf(
                    dangerLevel
                            .trim()
                            .toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "dangerLevel은 LOW, MEDIUM, HIGH 중 하나여야 합니다."
            );
        }
    }
}