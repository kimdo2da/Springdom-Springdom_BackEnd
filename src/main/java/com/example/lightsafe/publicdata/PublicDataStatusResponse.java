package com.example.lightsafe.publicdata;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관리자 화면에 보여줄 공공데이터 현황.
 */
public record PublicDataStatusResponse(
        long cctvCount,
        long securityLightCount,
        long geocodedSecurityLightCount,
        long pendingGeocodeAddressCount,
        boolean running,
        String syncCron,
        List<SyncHistoryItem> recentHistories
) {

    public record SyncHistoryItem(
            Long id,
            PublicDataSource source,
            PublicDataSyncStatus status,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            int fetchedCount,
            int savedCount,
            int queuedCount,
            int geocodedCount,
            String message
    ) {

        public static SyncHistoryItem from(PublicDataSyncHistory history) {
            return new SyncHistoryItem(
                    history.getPublicDataSyncHistoryId(),
                    history.getSource(),
                    history.getStatus(),
                    history.getStartedAt(),
                    history.getFinishedAt(),
                    history.getFetchedCount(),
                    history.getSavedCount(),
                    history.getQueuedCount(),
                    history.getGeocodedCount(),
                    history.getMessage()
            );
        }
    }
}
