package com.example.lightsafe.safe;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SecurityLightPointRepository
        extends JpaRepository<SecurityLightPoint, Long> {

    /**
     * 화면(또는 경로) 범위 안의 보안등 좌표만 꺼냅니다.
     *
     * 전국 180만 건을 통째로 내려주면 응답이 80MB 를 넘기 때문에
     * 반드시 범위를 잘라서 조회합니다.
     */
    @Query("""
            SELECT new com.example.lightsafe.safe.LocationDto(
                       light.latitude,
                       light.longitude
                   )
              FROM SecurityLightPoint light
             WHERE light.latitude  BETWEEN :minLatitude  AND :maxLatitude
               AND light.longitude BETWEEN :minLongitude AND :maxLongitude
            """)
    List<LocationDto> findLocationsInBounds(
            @Param("minLatitude") double minLatitude,
            @Param("maxLatitude") double maxLatitude,
            @Param("minLongitude") double minLongitude,
            @Param("maxLongitude") double maxLongitude,
            Pageable pageable
    );

    long countByGeocodedTrue();
}
