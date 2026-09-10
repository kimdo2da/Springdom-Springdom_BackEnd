package com.example.lightsafe.emergency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CctvRepository
        extends JpaRepository<Cctv, Long> {

    Optional<Cctv> findByMngNo(
            String mngNo
    );

    @Query("""
            SELECT c
            FROM Cctv c
            WHERE c.latitude BETWEEN :minLat AND :maxLat
              AND c.longitude BETWEEN :minLng AND :maxLng
            """)
    List<Cctv> findInBounds(
            @Param("minLat") BigDecimal minLat,
            @Param("maxLat") BigDecimal maxLat,
            @Param("minLng") BigDecimal minLng,
            @Param("maxLng") BigDecimal maxLng
    );
}