package com.example.lightsafe.post;

import jakarta.validation.constraints.NotBlank;


public record AdminNoticeCreateRequest(
        @NotBlank String title,
        @NotBlank String content
) {}
