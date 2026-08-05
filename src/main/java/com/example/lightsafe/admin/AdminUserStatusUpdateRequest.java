package com.example.lightsafe.admin;

public record AdminUserStatusUpdateRequest(
        String role,
        Boolean isBlacklisted
) {
}