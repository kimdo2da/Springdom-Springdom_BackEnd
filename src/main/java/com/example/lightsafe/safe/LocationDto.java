package com.example.lightsafe.safe;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LocationDto {
    private double latitude;  // lat -> latitude
    private double longitude; // lng -> longitude
}