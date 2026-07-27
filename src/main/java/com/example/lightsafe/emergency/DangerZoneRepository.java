package com.example.lightsafe.emergency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DangerZoneRepository extends JpaRepository<DangerZone, Long> {

    @Query("""
            SELECT zone
            FROM DangerZone zone
            WHERE zone.isActive = true
              AND (
                    zone.expiredAt IS NULL
                    OR zone.expiredAt > :now
                  )
            ORDER BY zone.createdAt DESC
            """)
    List<DangerZone> findPublicActiveZones(
            @Param("now")
            LocalDateTime now
    );

    @Query("""
            SELECT zone
            FROM DangerZone zone
            WHERE zone.isActive = true
              AND (
                    zone.expiredAt IS NULL
                    OR zone.expiredAt > :now
                  )
            """)
    List<DangerZone> findNearbyCandidateZones(
            @Param("now")
            LocalDateTime now
    );

    @Modifying(
            clearAutomatically = true,
            flushAutomatically = true
    )
    @Query("""
            UPDATE DangerZone zone
               SET zone.isActive = false
             WHERE zone.isActive = true
               AND zone.expiredAt IS NOT NULL
               AND zone.expiredAt <= :now
            """)
    int deactivateExpiredZones(
            @Param("now")
            LocalDateTime now
    );
}