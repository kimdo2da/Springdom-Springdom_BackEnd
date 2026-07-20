package com.example.lightsafe.emergency;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.time.LocalDateTime;

public interface EmergencyReportRepository extends JpaRepository<EmergencyReport, Long> {

    @EntityGraph(attributePaths = {"user", "dangerZone", "nearestCctv"})
    List<EmergencyReport> findByUserUserIdOrderByReportedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"user", "dangerZone", "nearestCctv"})
    List<EmergencyReport> findByDangerZoneDangerZoneIdOrderByReportedAtDesc(Long dangerZoneId);

    long countByDangerZoneDangerZoneIdAndIsFalseReportFalse(Long dangerZoneId);

    // 특정 유저가 신고한 내역을 신고일 기준 내림차순(최신순)으로 조회
    List<EmergencyReport> findByUser_UserIdOrderByReportedAtDesc(Long userId);

    /*
     * 관리자용 전체 긴급신고 이력 조회
     *
     * dangerZone.isActive를 조건으로 사용하지 않으므로
     * 활성·비활성 위험구역의 신고를 모두 조회합니다.
     */
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
            String status,

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