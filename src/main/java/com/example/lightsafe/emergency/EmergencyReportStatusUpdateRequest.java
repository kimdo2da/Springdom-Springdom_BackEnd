package com.example.lightsafe.emergency;

import jakarta.validation.constraints.NotBlank;

public record EmergencyReportStatusUpdateRequest(
        @NotBlank(message = "reportStatus는 필수입니다.")
        String reportStatus
) {}