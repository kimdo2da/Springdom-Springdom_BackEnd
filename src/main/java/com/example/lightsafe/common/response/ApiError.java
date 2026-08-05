package com.example.lightsafe.common.response;

public record ApiError(
        String code,
        String message
) {
}