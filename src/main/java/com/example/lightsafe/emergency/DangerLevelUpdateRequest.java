package com.example.lightsafe.emergency;

import jakarta.validation.constraints.NotBlank;

public record DangerLevelUpdateRequest(
        @NotBlank(message = "dangerLevel은 필수입니다.")
        String dangerLevel
) {}