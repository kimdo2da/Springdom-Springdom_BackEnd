package com.example.lightsafe.safe;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class LiveLocationResponse {
    private Double latitude;
    private Double longitude;
    private LocalDateTime updatedAt;
}