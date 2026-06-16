package com.example.lightsafe.emergency;

import com.example.lightsafe.user.User;
import com.example.lightsafe.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class EmergencyReportService2 {

    private static final int DEFAULT_DANGER_RADIUS_METER = 300;

    private final EmergencyReportRepository emergencyReportRepository;
    private final DangerZoneRepository dangerZoneRepository;
    private final CctvRepository cctvRepository;
    private final UserRepository userRepository;

    public EmergencyReportService2(
            EmergencyReportRepository emergencyReportRepository,
            DangerZoneRepository dangerZoneRepository,
            CctvRepository cctvRepository,
            UserRepository userRepository
    ) {
        this.emergencyReportRepository = emergencyReportRepository;
        this.dangerZoneRepository = dangerZoneRepository;
        this.cctvRepository = cctvRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public EmergencyReportResponse createReport(EmergencyReportCreateRequest request) {
        User user = getCurrentUser();

        if (user == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }

        if (user.isBlacklisted()) {
            throw new IllegalArgumentException("블랙리스트 사용자는 긴급신고를 할 수 없습니다.");
        }

        BigDecimal latitude = BigDecimal.valueOf(request.latitude());
        BigDecimal longitude = BigDecimal.valueOf(request.longitude());

        DangerZone dangerZone = findNearbyActiveDangerZone(request.latitude(), request.longitude());

        if (dangerZone == null) {
            dangerZone = createNewDangerZone(latitude, longitude);
        }

        Cctv nearestCctv = findNearestCctv(request.latitude(), request.longitude());

        EmergencyReport report = new EmergencyReport();
        report.setUser(user);
        report.setLatitude(latitude);
        report.setLongitude(longitude);
        report.setDescription(request.description());
        report.setReportStatus("RECEIVED");
        report.setIsFalseReport(false);
        report.setDangerZone(dangerZone);
        report.setNearestCctv(nearestCctv);

        EmergencyReport saved = emergencyReportRepository.save(report);

        updateDangerZoneLevelAndCount(dangerZone);

        return EmergencyReportResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public EmergencyReportResponse getReport(Long reportId) {
        EmergencyReport report = emergencyReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 신고입니다. id=" + reportId));

        return EmergencyReportResponse.from(report);
    }

    @Transactional(readOnly = true)
    public List<EmergencyReportResponse> getMyReports() {
        User user = getCurrentUser();

        if (user == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }

        return emergencyReportRepository.findByUserUserIdOrderByReportedAtDesc(user.getUserId())
                .stream()
                .map(EmergencyReportResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EmergencyReportResponse> getReportsByDangerZone(Long dangerZoneId) {
        if (!dangerZoneRepository.existsById(dangerZoneId)) {
            throw new IllegalArgumentException("존재하지 않는 위험구역입니다. id=" + dangerZoneId);
        }

        return emergencyReportRepository.findByDangerZoneDangerZoneIdOrderByReportedAtDesc(dangerZoneId)
                .stream()
                .map(EmergencyReportResponse::from)
                .toList();
    }

    @Transactional
    public EmergencyReportResponse updateReportStatus(Long reportId, EmergencyReportStatusUpdateRequest request) {
        EmergencyReport report = emergencyReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 신고입니다. id=" + reportId));

        report.setReportStatus(request.reportStatus());

        return EmergencyReportResponse.from(report);
    }

    @Transactional
    public EmergencyReportResponse markFalseReport(Long reportId) {
        EmergencyReport report = emergencyReportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 신고입니다. id=" + reportId));

        if (!Boolean.TRUE.equals(report.getIsFalseReport())) {
            report.setIsFalseReport(true);
            report.setReportStatus("FALSE");

            User reporter = report.getUser();

            int nextCount = reporter.getFalseReportCount() + 1;
            reporter.setFalseReportCount(nextCount);

            if (nextCount >= 3) {
                reporter.setBlacklisted(true);
            }

            updateDangerZoneLevelAndCount(report.getDangerZone());
        }

        return EmergencyReportResponse.from(report);
    }

    private DangerZone createNewDangerZone(BigDecimal latitude, BigDecimal longitude) {
        DangerZone dangerZone = new DangerZone();
        dangerZone.setCenterLatitude(latitude);
        dangerZone.setCenterLongitude(longitude);
        dangerZone.setRadius(DEFAULT_DANGER_RADIUS_METER);
        dangerZone.setDangerLevel("LOW");
        dangerZone.setReportCount(0);
        dangerZone.setIsActive(true);
        dangerZone.setExpiredAt(LocalDateTime.now().plusHours(24));

        return dangerZoneRepository.save(dangerZone);
    }

    private DangerZone findNearbyActiveDangerZone(double latitude, double longitude) {
        List<DangerZone> activeZones = dangerZoneRepository.findByIsActiveTrue();

        return activeZones.stream()
                .filter(zone -> {
                    double distance = calculateDistanceMeter(
                            latitude,
                            longitude,
                            zone.getCenterLatitude().doubleValue(),
                            zone.getCenterLongitude().doubleValue()
                    );

                    return distance <= zone.getRadius();
                })
                .min(Comparator.comparingDouble(zone -> calculateDistanceMeter(
                        latitude,
                        longitude,
                        zone.getCenterLatitude().doubleValue(),
                        zone.getCenterLongitude().doubleValue()
                )))
                .orElse(null);
    }

    private Cctv findNearestCctv(double latitude, double longitude) {
        return cctvRepository.findAll()
                .stream()
                .min(Comparator.comparingDouble(cctv -> calculateDistanceMeter(
                        latitude,
                        longitude,
                        cctv.getLatitude().doubleValue(),
                        cctv.getLongitude().doubleValue()
                )))
                .orElse(null);
    }

    private void updateDangerZoneLevelAndCount(DangerZone dangerZone) {
        long validReportCount = emergencyReportRepository
                .countByDangerZoneDangerZoneIdAndIsFalseReportFalse(dangerZone.getDangerZoneId());

        dangerZone.setReportCount((int) validReportCount);
        dangerZone.setDangerLevel(calculateDangerLevel(validReportCount));

        dangerZoneRepository.save(dangerZone);
    }

    private String calculateDangerLevel(long reportCount) {
        if (reportCount >= 4) {
            return "HIGH";
        }

        if (reportCount >= 2) {
            return "MEDIUM";
        }

        return "LOW";
    }

    private double calculateDistanceMeter(double lat1, double lon1, double lat2, double lon2) {
        final int earthRadius = 6371000;

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2)
                * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return earthRadius * c;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (!(principal instanceof Long userId)) {
            return null;
        }

        return userRepository.findById(userId).orElse(null);
    }
}