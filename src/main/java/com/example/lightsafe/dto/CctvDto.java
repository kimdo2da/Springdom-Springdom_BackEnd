package com.example.lightsafe.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CctvDto {
    private Long cctvId;        // 명세서 규칙
    private String cctvName;    // 명세서 규칙
    private Double latitude;    // lat -> latitude 로 변경
    private Double longitude;   // lng -> longitude 로 변경
    private String purpose;
}