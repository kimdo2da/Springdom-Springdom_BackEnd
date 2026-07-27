package com.example.lightsafe.admin;

import com.example.lightsafe.emergency.EmergencyReport;
import com.example.lightsafe.emergency.EmergencyReportRepository;
import com.example.lightsafe.emergency.EmergencyReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.lightsafe.emergency.EmergencyReportStatus;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminEmergencyReportService {

    private static final int MAX_PAGE_SIZE = 100;

    private final EmergencyReportRepository
            emergencyReportRepository;

    public AdminEmergencyReportPageResponse getAllReports(
            int page,
            int size,
            String status,
            Boolean isFalseReport,
            Long dangerZoneId,
            Long reporterId,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        validatePage(page, size);
        validateIds(dangerZoneId, reporterId);
        validateDateRange(startDate, endDate);

        EmergencyReportStatus normalizedStatus =
                normalizeStatus(status);

        PageRequest pageable =
                PageRequest.of(
                        page,
                        size
                );

        Page<EmergencyReport> reportPage =
                emergencyReportRepository
                        .findAdminEmergencyReports(
                                normalizedStatus,
                                isFalseReport,
                                dangerZoneId,
                                reporterId,
                                startDate,
                                endDate,
                                pageable
                        );

        Page<EmergencyReportResponse> responsePage =
                reportPage.map(
                        EmergencyReportResponse::from
                );

        return AdminEmergencyReportPageResponse.from(
                responsePage
        );
    }

    private void validatePage(
            int page,
            int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "page는 0 이상이어야 합니다."
            );
        }

        if (size < 1
                || size > MAX_PAGE_SIZE) {

            throw new IllegalArgumentException(
                    "size는 1 이상 100 이하여야 합니다."
            );
        }
    }

    private void validateIds(
            Long dangerZoneId,
            Long reporterId
    ) {
        if (dangerZoneId != null
                && dangerZoneId <= 0) {

            throw new IllegalArgumentException(
                    "dangerZoneId는 1 이상이어야 합니다."
            );
        }

        if (reporterId != null
                && reporterId <= 0) {

            throw new IllegalArgumentException(
                    "reporterId는 1 이상이어야 합니다."
            );
        }
    }

    private void validateDateRange(
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        if (startDate != null
                && endDate != null
                && startDate.isAfter(endDate)) {

            throw new IllegalArgumentException(
                    "startDate는 endDate보다 늦을 수 없습니다."
            );
        }
    }

    private EmergencyReportStatus normalizeStatus(
            String status
    ) {
        if (status == null
                || status.isBlank()) {

            return null;
        }

        try {
            return EmergencyReportStatus.valueOf(
                    status
                            .trim()
                            .toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "status는 RECEIVED, RESOLVED, FALSE 중 하나여야 합니다."
            );
        }
    }
}