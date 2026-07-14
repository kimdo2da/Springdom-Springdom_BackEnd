package com.example.lightsafe.emergency;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DangerZoneService {

    private final DangerZoneRepository dangerZoneRepository;

    public DangerZoneService(
            DangerZoneRepository dangerZoneRepository
    ) {
        this.dangerZoneRepository = dangerZoneRepository;
    }

    @Transactional(readOnly = true)
    public List<PublicDangerZoneResponse> getDangerZones() {
        return dangerZoneRepository.findByIsActiveTrueOrderByCreatedAtDesc()
                .stream()
                .map(PublicDangerZoneResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicDangerZoneResponse getDangerZoneDetail(Long dangerZoneId) {
        DangerZone dangerZone = dangerZoneRepository.findById(dangerZoneId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 위험구역입니다. id=" + dangerZoneId
                        )
                );

        return PublicDangerZoneResponse.from(dangerZone);
    }

    @Transactional
    public DangerZoneResponse updateDangerLevel(Long dangerZoneId, DangerLevelUpdateRequest request) {
        DangerZone dangerZone = dangerZoneRepository.findById(dangerZoneId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 위험구역입니다. id=" + dangerZoneId));

        dangerZone.setDangerLevel(request.dangerLevel());

        return DangerZoneResponse.from(dangerZone);
    }

    @Transactional
    public DangerZoneResponse deactivateDangerZone(Long dangerZoneId) {
        DangerZone dangerZone = dangerZoneRepository.findById(dangerZoneId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 위험구역입니다. id=" + dangerZoneId));

        dangerZone.setIsActive(false);
        dangerZone.setExpiredAt(LocalDateTime.now());

        return DangerZoneResponse.from(dangerZone);
    }
}