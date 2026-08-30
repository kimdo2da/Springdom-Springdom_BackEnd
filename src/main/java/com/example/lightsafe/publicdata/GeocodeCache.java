package com.example.lightsafe.publicdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 주소 → 좌표 변환 결과 보관함.
 *
 * 브이월드·카카오 모두 일일 호출 한도가 있어서, 한 번 물어본 주소는 다시 묻지 않습니다.
 * 매달 데이터를 새로 받아도 주소가 그대로면 이 표에서 바로 꺼내 씁니다.
 *
 * 찾지 못한 주소도 found=false 로 남깁니다. 그래야 매달 같은 주소로
 * 헛되이 한도를 쓰지 않습니다.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "geocode_cache",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_geocode_cache_address",
                        columnNames = "address"
                )
        }
)
public class GeocodeCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "geocode_cache_id")
    private Long geocodeCacheId;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "found", nullable = false)
    private boolean found;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public GeocodeCache(
            String address,
            Double latitude,
            Double longitude,
            boolean found
    ) {
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.found = found;
        this.updatedAt = LocalDateTime.now();
    }
}
