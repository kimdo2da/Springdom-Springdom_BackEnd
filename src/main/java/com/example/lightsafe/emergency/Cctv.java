package com.example.lightsafe.emergency;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "cctvs",
        uniqueConstraints = {
                /*
                 * 한 지점에 카메라가 여러 대 달린 행이 12만 건이라 좌표로 중복을 막습니다.
                 * 이 유일 인덱스가 화면 범위 조회의 인덱스 역할도 같이 합니다.
                 */
                @UniqueConstraint(
                        name = "uk_cctvs_lat_lng",
                        columnNames = {"latitude", "longitude"}
                )
        }
)
public class Cctv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cctv_id")
    private Long cctvId;

    @Column(name = "cctv_name", length = 100)
    private String cctvName;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "purpose", length = 50)
    private String purpose;

    /**
     * 한 지점에 달린 카메라 대수. 전국CCTV표준데이터의 '카메라대수' 칸입니다.
     */
    @Column(name = "camera_count")
    private Integer cameraCount;

    @Column(name = "sido", length = 40)
    private String sido;
}
//이 기능은 cctv 기능이 아닌 근처의 cctv에서 사용자 위급신고시
//위치 가늠화 및 연동을 위한것.
