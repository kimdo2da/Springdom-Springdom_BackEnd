package com.example.lightsafe.safe;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BookmarkRequestDto {
    private String routeName;
    private double startLatitude;
    private double startLongitude;
    private double endLatitude;
    private double endLongitude;
    private int safetyScore;
}