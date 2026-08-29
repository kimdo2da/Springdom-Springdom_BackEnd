package com.example.lightsafe.publicdata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface LampGeocodeQueueRepository
        extends JpaRepository<LampGeocodeQueue, Long> {

    /**
     * 아직 처리하지 않은 서로 다른 주소를 한도만큼 꺼냅니다.
     *
     * 한 주소에 보안등이 여러 개 걸려 있는 경우가 많아서
     * 카카오에는 주소 단위로 한 번만 물어봅니다.
     * 처리한 주소는 대기열에서 지우므로 남아 있는 것이 곧 할 일입니다.
     *
     * **시·도를 번갈아 가며 꺼냅니다.** 그냥 주소 순으로 꺼내면 강원도를 다 끝낸 다음에야
     * 서울에 닿아서, 며칠 동안 서울 지도가 그대로 비어 있습니다. 각 시·도의 1순위를 한 바퀴,
     * 그다음 2순위를 한 바퀴 도는 식이라 전국이 고르게 채워집니다.
     *
     * 시·도 안에서는 **보안등이 많이 걸린 주소부터** 꺼냅니다. 카카오 호출 한 번에
     * 지도에 찍히는 점이 가장 많이 늘어나는 순서입니다(한 주소에 232개가 걸린 곳도 있습니다).
     */
    @Query(
            value = """
                    SELECT ranked.address
                      FROM (
                            SELECT queue.address AS address,
                                   queue.sido    AS sido,
                                   ROW_NUMBER() OVER (
                                       PARTITION BY queue.sido
                                       ORDER BY COUNT(*) DESC, queue.address
                                   ) AS rn
                              FROM lamp_geocode_queue queue
                             GROUP BY queue.sido, queue.address
                           ) ranked
                     ORDER BY ranked.rn, ranked.sido
                     LIMIT :limit
                    """,
            nativeQuery = true
    )
    List<String> findPendingAddresses(@Param("limit") int limit);

    List<LampGeocodeQueue> findByAddressIn(List<String> addresses);

    @Modifying
    @Transactional
    @Query("DELETE FROM LampGeocodeQueue queue WHERE queue.address IN :addresses")
    void deleteByAddressIn(List<String> addresses);

    @Query("SELECT COUNT(DISTINCT queue.address) FROM LampGeocodeQueue queue")
    long countPendingAddresses();
}
