package com.example.lightsafe.emergency;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EmergencyReportRepository
        extends JpaRepository<EmergencyReport, Long> {

    @EntityGraph(
            attributePaths = {
                    "user",
                    "dangerZone",
                    "nearestCctv"
            }
    )
    List<EmergencyReport> findByUser_UserIdOrderByReportedAtDesc(
            Long userId
    );

    @EntityGraph(
            attributePaths = {
                    "user",
                    "dangerZone",
                    "nearestCctv"
            }
    )
    List<EmergencyReport> findByDangerZone_DangerZoneIdOrderByReportedAtDesc(
            Long dangerZoneId
    );

    long countByDangerZone_DangerZoneIdAndIsFalseReportFalse(
            Long dangerZoneId
    );

    @Query(
            value = """
                    SELECT report
                    FROM EmergencyReport report
                    JOIN FETCH report.user reporter
                    JOIN FETCH report.dangerZone dangerZone
                    LEFT JOIN FETCH report.nearestCctv nearestCctv
                    WHERE (:status IS NULL
                           OR report.reportStatus = :status)
                      AND (:isFalseReport IS NULL
                           OR report.isFalseReport = :isFalseReport)
                      AND (:dangerZoneId IS NULL
                           OR dangerZone.dangerZoneId = :dangerZoneId)
                      AND (:reporterId IS NULL
                           OR reporter.userId = :reporterId)
                      AND (:startDate IS NULL
                           OR report.reportedAt >= :startDate)
                      AND (:endDate IS NULL
                           OR report.reportedAt <= :endDate)
                    ORDER BY report.reportedAt DESC,
                             report.reportId DESC
                    """,
            countQuery = """
                    SELECT COUNT(report)
                    FROM EmergencyReport report
                    JOIN report.user reporter
                    JOIN report.dangerZone dangerZone
                    WHERE (:status IS NULL
                           OR report.reportStatus = :status)
                      AND (:isFalseReport IS NULL
                           OR report.isFalseReport = :isFalseReport)
                      AND (:dangerZoneId IS NULL
                           OR dangerZone.dangerZoneId = :dangerZoneId)
                      AND (:reporterId IS NULL
                           OR reporter.userId = :reporterId)
                      AND (:startDate IS NULL
                           OR report.reportedAt >= :startDate)
                      AND (:endDate IS NULL
                           OR report.reportedAt <= :endDate)
                    """
    )
    Page<EmergencyReport> findAdminEmergencyReports(
            @Param("status")
            EmergencyReportStatus status,

            @Param("isFalseReport")
            Boolean isFalseReport,

            @Param("dangerZoneId")
            Long dangerZoneId,

            @Param("reporterId")
            Long reporterId,

            @Param("startDate")
            LocalDateTime startDate,

            @Param("endDate")
            LocalDateTime endDate,

            Pageable pageable
    );
}