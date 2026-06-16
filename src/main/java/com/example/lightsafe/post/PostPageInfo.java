package com.example.lightsafe.post;

public record PostPageInfo(
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
