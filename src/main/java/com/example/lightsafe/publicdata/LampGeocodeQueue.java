package com.example.lightsafe.publicdata;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 원본에 좌표가 없어 주소를 변환해야 하는 보안등 대기열.
 *
 * 전국 보안등 약 184만 건 중 9% 가량은 위·경도 칸이 비어 있고 주소만 있습니다.
 * 이런 지점은 여기에 쌓아 두었다가, 지오코딩 일일 한도에 맞춰 조금씩 좌표로 바꿔
 * street_lamps 로 옮깁니다.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "lamp_geocode_queue",
        indexes = {
                @Index(
                        name = "idx_lamp_geocode_queue_address",
                        columnList = "address"
                )
        }
)
public class LampGeocodeQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lamp_geocode_queue_id")
    private Long lampGeocodeQueueId;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "lamp_count")
    private Integer lampCount;

    @Column(name = "sido", length = 40)
    private String sido;

    public LampGeocodeQueue(
            String address,
            Integer lampCount,
            String sido
    ) {
        this.address = address;
        this.lampCount = lampCount;
        this.sido = sido;
    }
}
