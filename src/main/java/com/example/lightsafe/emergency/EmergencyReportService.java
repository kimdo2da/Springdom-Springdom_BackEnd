package com.example.lightsafe.emergency;

import com.example.lightsafe.common.exception.BadRequestException;
import com.example.lightsafe.common.exception.NotFoundException;
import com.example.lightsafe.friends.FriendService;
import com.example.lightsafe.notification.NotificationService;
import com.example.lightsafe.user.User;
import com.example.lightsafe.user.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class EmergencyReportService {

    private static final int DEFAULT_DANGER_RADIUS_METER = 300;
    private static final int DANGER_ZONE_ACTIVE_HOURS = 24;

    private final EmergencyReportRepository emergencyReportRepository;
    private final DangerZoneRepository dangerZoneRepository;
    private final CctvRepository cctvRepository;
    private final UserRepository userRepository;
    private final FriendService friendService;
    private final NotificationService notificationService;

    public EmergencyReportService(
            EmergencyReportRepository emergencyReportRepository,
            DangerZoneRepository dangerZoneRepository,
            CctvRepository cctvRepository,
            UserRepository userRepository,
            FriendService friendService,
            NotificationService notificationService
    ) {
        this.emergencyReportRepository = emergencyReportRepository;
        this.dangerZoneRepository = dangerZoneRepository;
        this.cctvRepository = cctvRepository;
        this.userRepository = userRepository;
        this.friendService = friendService;
        this.notificationService = notificationService;
    }

    @Transactional
    public EmergencyReportResponse createReport(
            EmergencyReportCreateRequest request
    ) {
        User user = getCurrentUser();

        if (user == null) {
            throw new AccessDeniedException(
                    "로그인이 필요합니다."
            );
        }

        if (user.isBlacklisted()) {
            throw new BadRequestException(
                    "블랙리스트 사용자는 긴급신고를 할 수 없습니다."
            );
        }

        BigDecimal latitude =
                BigDecimal.valueOf(request.latitude());

        BigDecimal longitude =
                BigDecimal.valueOf(request.longitude());

        DangerZone dangerZone =
                findNearbyActiveDangerZone(
                        request.latitude(),
                        request.longitude()
                );

        if (dangerZone == null) {
            dangerZone =
                    createNewDangerZone(
                            latitude,
                            longitude
                    );
        }

        Cctv nearestCctv =
                findNearestCctv(
                        request.latitude(),
                        request.longitude()
                );

        EmergencyReport report = new EmergencyReport();

        report.setUser(user);
        report.setLatitude(latitude);
        report.setLongitude(longitude);
        report.setDescription(request.description());
        report.setReportStatus(EmergencyReportStatus.RECEIVED);
        report.setIsFalseReport(false);
        report.setDangerZone(dangerZone);
        report.setNearestCctv(nearestCctv);

        EmergencyReport saved =
                emergencyReportRepository.save(report);

        updateDangerZoneLevelAndCount(dangerZone);

        List<User> notificationRecipients =
                friendService
                        .getEmergencyNotificationRecipients(
                                user.getUserId()
                        );

        notificationService
                .createEmergencyReportNotifications(
                        saved,
                        notificationRecipients
                );

        return EmergencyReportResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public EmergencyReportResponse getReport(Long reportId) {
        EmergencyReport report =
                emergencyReportRepository.findById(reportId)
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "존재하지 않는 신고입니다. id=" + reportId
                                )
                        );

        User currentUser = getCurrentUser();

        if (currentUser == null) {
            throw new AccessDeniedException(
                    "로그인이 필요합니다."
            );
        }

        boolean isReporter =
                Objects.equals(
                        report.getUser().getUserId(),
                        currentUser.getUserId()
                );

        boolean isAdmin =
                "ADMIN".equalsIgnoreCase(
                        currentUser.getRole()
                );

        if (!isReporter && !isAdmin) {
            throw new AccessDeniedException(
                    "신고자 본인 또는 관리자만 신고 상세정보를 조회할 수 있습니다."
            );
        }

        return EmergencyReportResponse.from(report);
    }

    @Transactional(readOnly = true)
    public SharedEmergencyLocationResponse getSharedLocation(
            Long reportId
    ) {
        EmergencyReport report =
                emergencyReportRepository.findById(reportId)
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "존재하지 않는 신고입니다. id=" + reportId
                                )
                        );

        User currentUser = getCurrentUser();

        if (currentUser == null) {
            throw new AccessDeniedException(
                    "로그인이 필요합니다."
            );
        }

        Long reporterUserId =
                report.getUser().getUserId();

        Long viewerUserId =
                currentUser.getUserId();

        boolean isReporter =
                Objects.equals(
                        reporterUserId,
                        viewerUserId
                );

        boolean isAdmin =
                "ADMIN".equalsIgnoreCase(
                        currentUser.getRole()
                );

        boolean isAllowedFriend =
                friendService.canAccessEmergencyLocation(
                        reporterUserId,
                        viewerUserId
                );

        if (!isReporter && !isAdmin && !isAllowedFriend) {
            throw new AccessDeniedException(
                    "위치 공유가 허용된 친구만 정확한 위치를 조회할 수 있습니다."
            );
        }

        return SharedEmergencyLocationResponse.from(report);
    }

    @Transactional(readOnly = true)
    public List<EmergencyReportResponse> getMyReports() {
        User user = getCurrentUser();

        if (user == null) {
            throw new AccessDeniedException(
                    "로그인이 필요합니다."
            );
        }

        return emergencyReportRepository
                .findByUser_UserIdOrderByReportedAtDesc(
                        user.getUserId()
                )
                .stream()
                .map(EmergencyReportResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<EmergencyReportResponse> getReportsByDangerZone(
            Long dangerZoneId
    ) {
        if (!dangerZoneRepository.existsById(dangerZoneId)) {
            throw new NotFoundException(
                    "존재하지 않는 위험구역입니다. id=" + dangerZoneId
            );
        }

        return emergencyReportRepository
                .findByDangerZone_DangerZoneIdOrderByReportedAtDesc(
                        dangerZoneId
                )
                .stream()
                .map(EmergencyReportResponse::from)
                .toList();
    }

    @Transactional
    public EmergencyReportResponse updateReportStatus(
            Long reportId,
            EmergencyReportStatusUpdateRequest request
    ) {
        EmergencyReport report =
                emergencyReportRepository.findById(reportId)
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "존재하지 않는 신고입니다. id=" + reportId
                                )
                        );

        EmergencyReportStatus normalizedStatus =
                normalizeReportStatus(
                        request.reportStatus()
                );

        if (normalizedStatus == EmergencyReportStatus.FALSE) {
            throw new BadRequestException(
                    "허위신고 처리는 false-report API를 사용해주세요."
            );
        }

        if (Boolean.TRUE.equals(
                report.getIsFalseReport()
        )) {
            throw new BadRequestException(
                    "허위신고로 확정된 신고는 false-report/cancel API로만 되돌릴 수 있습니다."
            );
        }

        report.setReportStatus(
                normalizedStatus
        );

        updateDangerZoneLevelAndCount(
                report.getDangerZone()
        );

        return EmergencyReportResponse.from(report);
    }

    @Transactional
    public EmergencyReportResponse markFalseReport(Long reportId) {
        EmergencyReport report =
                emergencyReportRepository.findById(reportId)
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "존재하지 않는 신고입니다. id=" + reportId
                                )
                        );

        if (!Boolean.TRUE.equals(report.getIsFalseReport())) {
            report.setIsFalseReport(true);
            report.setReportStatus(EmergencyReportStatus.FALSE);

            applyFalseReportPenalty(
                    report.getUser()
            );

            updateDangerZoneLevelAndCount(
                    report.getDangerZone()
            );
        }

        return EmergencyReportResponse.from(report);
    }

    @Transactional
    public EmergencyReportResponse cancelFalseReport(Long reportId) {
        EmergencyReport report =
                emergencyReportRepository.findById(reportId)
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "존재하지 않는 신고입니다. id=" + reportId
                                )
                        );

        if (!Boolean.TRUE.equals(
                report.getIsFalseReport()
        )) {
            throw new BadRequestException(
                    "허위신고로 확정된 신고가 아닙니다."
            );
        }

        report.setIsFalseReport(
                false
        );

        report.setReportStatus(
                EmergencyReportStatus.RECEIVED
        );

        cancelFalseReportPenalty(
                report.getUser()
        );

        updateDangerZoneLevelAndCount(
                report.getDangerZone()
        );

        return EmergencyReportResponse.from(report);
    }

    private void applyFalseReportPenalty(User reporter) {
        Integer currentCount =
                reporter.getFalseReportCount();

        int nextCount =
                (currentCount == null ? 0 : currentCount) + 1;

        reporter.setFalseReportCount(nextCount);

        if (nextCount >= 3) {
            reporter.setBlacklisted(true);
        }
    }

    private void cancelFalseReportPenalty(User reporter) {
        if (reporter == null) {
            return;
        }

        int currentCount =
                reporter.getFalseReportCount();

        int nextCount =
                Math.max(
                        0,
                        currentCount - 1
                );

        reporter.setFalseReportCount(
                nextCount
        );

        if (nextCount < 3) {
            reporter.setBlacklisted(
                    false
            );
        }
    }

    private DangerZone createNewDangerZone(
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        DangerZone dangerZone = new DangerZone();

        dangerZone.setCenterLatitude(latitude);
        dangerZone.setCenterLongitude(longitude);
        dangerZone.setRadius(DEFAULT_DANGER_RADIUS_METER);
        dangerZone.setDangerLevel(DangerLevel.LOW);
        dangerZone.setReportCount(0);
        dangerZone.setIsActive(true);
        dangerZone.setExpiredAt(
                LocalDateTime.now().plusHours(DANGER_ZONE_ACTIVE_HOURS)
        );

        return dangerZoneRepository.save(dangerZone);
    }

    private DangerZone findNearbyActiveDangerZone(
            double latitude,
            double longitude
    ) {
        LocalDateTime now = LocalDateTime.now();

        dangerZoneRepository.deactivateExpiredZones(now);

        List<DangerZone> activeZones =
                dangerZoneRepository.findNearbyCandidateZones(now);

        return activeZones.stream()
                .filter(zone -> {
                    double distance =
                            calculateDistanceMeter(
                                    latitude,
                                    longitude,
                                    zone.getCenterLatitude().doubleValue(),
                                    zone.getCenterLongitude().doubleValue()
                            );

                    return distance <= zone.getRadius();
                })
                .min(
                        Comparator.comparingDouble(
                                zone -> calculateDistanceMeter(
                                        latitude,
                                        longitude,
                                        zone.getCenterLatitude().doubleValue(),
                                        zone.getCenterLongitude().doubleValue()
                                )
                        )
                )
                .orElse(null);
    }

    private Cctv findNearestCctv(
            double latitude,
            double longitude
    ) {
        return cctvRepository.findAll()
                .stream()
                .min(
                        Comparator.comparingDouble(
                                cctv -> calculateDistanceMeter(
                                        latitude,
                                        longitude,
                                        cctv.getLatitude().doubleValue(),
                                        cctv.getLongitude().doubleValue()
                                )
                        )
                )
                .orElse(null);
    }

    private void updateDangerZoneLevelAndCount(
            DangerZone dangerZone
    ) {
        if (dangerZone == null) {
            return;
        }

        LocalDateTime now =
                LocalDateTime.now();

        long validReportCount =
                emergencyReportRepository
                        .countByDangerZone_DangerZoneIdAndReportStatusAndIsFalseReportFalse(
                                dangerZone.getDangerZoneId(),
                                EmergencyReportStatus.RECEIVED
                        );

        dangerZone.setReportCount(
                (int) validReportCount
        );

        dangerZone.setDangerLevel(
                calculateDangerLevel(
                        validReportCount
                )
        );

        if (validReportCount <= 0) {
            dangerZone.setIsActive(false);

            if (dangerZone.getExpiredAt() == null
                    || dangerZone.getExpiredAt()
                    .isAfter(now)) {

                dangerZone.setExpiredAt(
                        now
                );
            }

            dangerZoneRepository.save(
                    dangerZone
            );

            return;
        }

        /*
         * 해결완료로 인해 비활성화된 위험구역은
         * 다시 RECEIVED 상태가 생기면 활성화할 수 있습니다.
         *
         * 다만 createdAt 기준 24시간이 이미 지난 자연 만료 구역은
         * 다시 살리지 않습니다.
         */
        if (!Boolean.TRUE.equals(
                dangerZone.getIsActive()
        ) && canReactivateDangerZone(
                dangerZone,
                now
        )) {
            dangerZone.setIsActive(
                    true
            );

            dangerZone.setExpiredAt(
                    now.plusHours(
                            DANGER_ZONE_ACTIVE_HOURS
                    )
            );
        }

        dangerZoneRepository.save(
                dangerZone
        );
    }
    private boolean canReactivateDangerZone(
            DangerZone dangerZone,
            LocalDateTime now
    ) {
        if (dangerZone.getCreatedAt() == null) {
            return true;
        }

        return dangerZone.getCreatedAt()
                .plusHours(
                        DANGER_ZONE_ACTIVE_HOURS
                )
                .isAfter(now);
    }

    private DangerLevel calculateDangerLevel(long reportCount) {
        if (reportCount >= 4) {
            return DangerLevel.HIGH;
        }

        if (reportCount >= 2) {
            return DangerLevel.MEDIUM;
        }

        return DangerLevel.LOW;
    }

    private EmergencyReportStatus normalizeReportStatus(
            String reportStatus
    ) {
        if (reportStatus == null || reportStatus.isBlank()) {
            throw new BadRequestException(
                    "reportStatus는 필수입니다."
            );
        }

        try {
            return EmergencyReportStatus.valueOf(
                    reportStatus
                            .trim()
                            .toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "reportStatus는 RECEIVED, RESOLVED, FALSE 중 하나여야 합니다."
            );
        }
    }

    private double calculateDistanceMeter(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {
        final int earthRadius = 6371000;

        double latDistance =
                Math.toRadians(lat2 - lat1);

        double lonDistance =
                Math.toRadians(lon2 - lon1);

        double a =
                Math.sin(latDistance / 2)
                        * Math.sin(latDistance / 2)
                        + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(lonDistance / 2)
                        * Math.sin(lonDistance / 2);

        double c =
                2 * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );

        return earthRadius * c;
    }

    private User getCurrentUser() {
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || authentication.getPrincipal() == null) {

            return null;
        }

        Object principal =
                authentication.getPrincipal();

        if (!(principal instanceof Long userId)) {
            return null;
        }

        return userRepository
                .findById(userId)
                .orElse(null);
    }
}