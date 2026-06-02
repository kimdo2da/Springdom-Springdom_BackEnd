package com.example.lightsafe.safe;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RouteDto {
    private int routeId;
    private List<LocationDto> path; // 실제 도로 좌표 리스트
    private int safetyScore;        // CCTV 기반 안전 점수
    private String description;     // 설명
}