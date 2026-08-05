package com.example.lightsafe.admin;

public record AdminDashboardSummaryResponse(
        long totalPosts,
        long todayPosts,
        long totalUsers
) {
}