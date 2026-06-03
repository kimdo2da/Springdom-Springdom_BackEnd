package com.example.lightsafe.safe;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RouteRequestDto {
    private double startLatitude;  // startLat -> startLatitude
    private double startLongitude; // startLng -> startLongitude
    private double endLatitude;    // endLat -> endLatitude
    private double endLongitude;   // endLng -> endLongitude
}