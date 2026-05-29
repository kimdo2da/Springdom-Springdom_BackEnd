package com.example.lightsafe.emergency;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DangerZoneService {

    private final DangerZoneRepository dangerZoneRepository;
    private final EmergencyReportRepository emergencyReportRepository;

    public DangerZoneService(
            DangerZoneRepository dangerZoneRepository,
            EmergencyReportRepository emergencyReportRepository
    ) {
        this.dangerZoneRepository = dangerZoneRepository;
        this.emergencyReportRepository = emergencyReportRepository;
    }

    @Transactional(readOnly = true)
    public List<DangerZoneResponse> getDangerZones() {
        return dangerZoneRepository.findByIsActiveTrueOrderByCreatedAtDesc()
                .stream()
                .map(DangerZoneResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public DangerZoneDetailResponse getDangerZoneDetail(Long dangerZoneId) {
        DangerZone dangerZone = dangerZoneRepository.findById(dangerZoneId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 위험구역입니다. id=" + dangerZoneId));

        List<EmergencyReportResponse> reports = emergencyReportRepository
                .findByDangerZoneDangerZoneIdOrderByReportedAtDesc(dangerZoneId)
                .stream()
                .map(EmergencyReportResponse::from)
                .toList();

        return DangerZoneDetailResponse.of(dangerZone, reports);
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