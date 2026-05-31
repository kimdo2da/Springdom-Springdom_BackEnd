package com.example.lightsafe.emergency;

import jakarta.validation.constraints.NotNull;

public record EmergencyReportCreateRequest(
        @NotNull(message = "latitude는 필수입니다.")
        Double latitude,

        @NotNull(message = "longitude는 필수입니다.")
        Double longitude,

        String description
) {}