package com.example.lightsafe.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminNoticeCreateRequest(
        @NotBlank String title,
        @NotBlank String content,
        @NotNull Long adminUserId
) {}
