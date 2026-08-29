package com.example.lightsafe.safe;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LocationDto {
    private double latitude;  // lat -> latitude
    private double longitude; // lng -> longitude

    /**
     * cctvs 표의 위·경도는 DECIMAL 이라 JPQL 생성자 조회에서 BigDecimal 로 넘어옵니다.
     */
    public LocationDto(
            BigDecimal latitude,
            BigDecimal longitude
    ) {
        this.latitude = latitude == null ? 0.0 : latitude.doubleValue();
        this.longitude = longitude == null ? 0.0 : longitude.doubleValue();
    }
}
