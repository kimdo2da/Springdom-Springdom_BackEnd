package com.example.lightsafe.emergency;

import com.example.lightsafe.safe.LocationDto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CctvRepository extends JpaRepository<Cctv, Long> {

    /**
     * 화면(또는 경로) 범위 안의 CCTV 좌표만 꺼냅니다.
     *
     * 전국 25만 건을 통째로 내려주지 않기 위해 반드시 범위를 잘라서 조회합니다.
     */
    @Query("""
            SELECT new com.example.lightsafe.safe.LocationDto(
                       cctv.latitude,
                       cctv.longitude
                   )
              FROM Cctv cctv
             WHERE cctv.latitude  BETWEEN :minLatitude  AND :maxLatitude
               AND cctv.longitude BETWEEN :minLongitude AND :maxLongitude
            """)
    List<LocationDto> findLocationsInBounds(
            @Param("minLatitude") double minLatitude,
            @Param("maxLatitude") double maxLatitude,
            @Param("minLongitude") double minLongitude,
            @Param("maxLongitude") double maxLongitude,
            Pageable pageable
    );

    /**
     * 화면 범위 안의 CCTV 를 상세 정보까지 꺼냅니다. 지도 목록용.
     */
    @Query("""
            SELECT cctv
              FROM Cctv cctv
             WHERE cctv.latitude  BETWEEN :minLatitude  AND :maxLatitude
               AND cctv.longitude BETWEEN :minLongitude AND :maxLongitude
            """)
    List<Cctv> findInBounds(
            @Param("minLatitude") double minLatitude,
            @Param("maxLatitude") double maxLatitude,
            @Param("minLongitude") double minLongitude,
            @Param("maxLongitude") double maxLongitude,
            Pageable pageable
    );

    /**
     * 긴급신고 지점에서 가장 가까운 CCTV 후보.
     *
     * 예전에는 findAll() 로 전건을 메모리에 올렸는데, 전국 데이터로 바뀌면
     * 신고 1건마다 25만 행을 읽게 되므로 사각 범위로 먼저 좁힙니다.
     * 정확한 최단거리는 이 후보들 안에서 계산합니다.
     */
    @Query("""
            SELECT cctv
              FROM Cctv cctv
             WHERE cctv.latitude  BETWEEN :minLatitude  AND :maxLatitude
               AND cctv.longitude BETWEEN :minLongitude AND :maxLongitude
            """)
    List<Cctv> findNearestCandidates(
            @Param("minLatitude") double minLatitude,
            @Param("maxLatitude") double maxLatitude,
            @Param("minLongitude") double minLongitude,
            @Param("maxLongitude") double maxLongitude,
            Pageable pageable
    );
}
