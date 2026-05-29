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
}