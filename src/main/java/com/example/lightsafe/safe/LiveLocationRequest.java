package com.example.lightsafe.safe;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LiveLocationRequest {
    private Double latitude;
    private Double longitude;
}