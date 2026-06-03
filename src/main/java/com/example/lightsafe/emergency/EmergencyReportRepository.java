package com.example.lightsafe.emergency;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmergencyReportRepository extends JpaRepository<EmergencyReport, Long> {

    @EntityGraph(attributePaths = {"user", "dangerZone", "nearestCctv"})
    List<EmergencyReport> findByUserUserIdOrderByReportedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"user", "dangerZone", "nearestCctv"})
    List<EmergencyReport> findByDangerZoneDangerZoneIdOrderByReportedAtDesc(Long dangerZoneId);

    long countByDangerZoneDangerZoneIdAndIsFalseReportFalse(Long dangerZoneId);

    // 특정 유저가 신고한 내역을 신고일 기준 내림차순(최신순)으로 조회
    List<EmergencyReport> findByUser_UserIdOrderByReportedAtDesc(Long userId);
}