package com.example.lightsafe.safe;

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

/**
 * 전국보안등표준데이터 1건(= 가로등 1지점).
 *
 * 예전에는 자치구별 CSV 를 서버 기동 때마다 메모리에 올렸지만,
 * 전국 데이터는 180만 건이 넘어 매번 다시 받을 수 없습니다.
 * 그래서 수집한 결과를 이 표에 저장해 두고 화면 범위(bbox)로만 잘라서 조회합니다.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "street_lamps",
        uniqueConstraints = {
                /*
                 * 같은 자리에 등이 여러 개 등록된 행이 많아 좌표로 중복을 막습니다.
                 * 이 유일 인덱스가 화면 범위 조회의 인덱스 역할도 같이 합니다.
                 */
                @UniqueConstraint(
                        name = "uk_street_lamps_lat_lng",
                        columnNames = {"latitude", "longitude"}
                )
        }
)
public class SecurityLightPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lamp_id")
    private Long lampId;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "address", length = 255)
    private String address;

    /**
     * 설치개수. 한 지점에 등이 여러 개 달린 경우가 있습니다.
     */
    @Column(name = "lamp_count")
    private Integer lampCount;

    @Column(name = "sido", length = 40)
    private String sido;

    /**
     * 원본에 좌표가 없어 주소를 지오코딩해서 채운 지점인지 여부.
     */
    @Column(name = "geocoded", nullable = false)
    private boolean geocoded;

    public SecurityLightPoint(
            double latitude,
            double longitude,
            String address,
            Integer lampCount,
            String sido,
            boolean geocoded
    ) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.lampCount = lampCount;
        this.sido = sido;
        this.geocoded = geocoded;
    }
}
