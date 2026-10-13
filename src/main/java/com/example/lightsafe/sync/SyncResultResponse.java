package com.example.lightsafe.sync;

public record SyncResultResponse(
        String dataset,
        String status,
        int fetchedCount,
        int savedCount,
        String message
) {
}