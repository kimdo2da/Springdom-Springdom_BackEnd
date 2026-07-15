package com.example.lightsafe.safe;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StreetLampDto {
    private Long lampId;
    private String lampName;      // 보안등위치명
    private String address;       // 도로명주소 우선, 없으면 지번주소
    private Double latitude;
    private Double longitude;
    private String installYear;   // 설치연도
    private String institutionName; // 관리기관명(지자체)
}

