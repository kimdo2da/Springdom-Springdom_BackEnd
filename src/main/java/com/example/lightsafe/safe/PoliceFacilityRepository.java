package com.example.lightsafe.safe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PoliceFacilityRepository
        extends JpaRepository<PoliceFacility, Long> {

    Optional<PoliceFacility> findByObjectId(
            String objectId
    );

    @Query("""
            SELECT p
            FROM PoliceFacility p
            WHERE p.latitude BETWEEN :minLat AND :maxLat
              AND p.longitude BETWEEN :minLng AND :maxLng
            """)
    List<PoliceFacility> findInBounds(
            @Param("minLat") BigDecimal minLat,
            @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng,
            @Param("maxLng") BigDecimal maxLng
    );
}