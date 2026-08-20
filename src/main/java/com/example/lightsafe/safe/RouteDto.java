package com.example.lightsafe.safe;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class RouteDto {

    private int routeId;
    private List<LocationDto> path;
    private int safetyScore;
    private String description;

    // 경로 주변 CCTV 위치 목록
    private List<LocationDto> cctvLocations;

    // 경로 주변 편의점 위치 목록
    private List<LocationDto> storeLocations;

    // 🔥 경로 주변 보안등 위치 목록 추가
    private List<LocationDto> securityLightLocations;

    public RouteDto(
            int routeId,
            List<LocationDto> path,
            int safetyScore,
            String description
    ) {
        this.routeId = routeId;
        this.path = path;
        this.safetyScore = safetyScore;
        this.description = description;
    }

    public RouteDto(
            int routeId,
            List<LocationDto> path,
            int safetyScore,
            String description,
            List<LocationDto> cctvLocations,
            List<LocationDto> storeLocations,
            List<LocationDto> securityLightLocations
    ) {
        this.routeId = routeId;
        this.path = path;
        this.safetyScore = safetyScore;
        this.description = description;
        this.cctvLocations = cctvLocations;
        this.storeLocations = storeLocations;
        this.securityLightLocations = securityLightLocations;
    }
}