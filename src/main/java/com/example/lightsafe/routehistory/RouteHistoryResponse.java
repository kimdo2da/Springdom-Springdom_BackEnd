package com.example.lightsafe.routehistory;

import java.time.LocalDateTime;

public record RouteHistoryResponse(
        Long routeHistoryId,
        Long id,
        String routeName,
        double startLatitude,
        double startLongitude,
        double endLatitude,
        double endLongitude,
        LocalDateTime searchedAt,
        LocalDateTime searchTime
) {

    public static RouteHistoryResponse from(
            RouteHistory routeHistory
    ) {
        return new RouteHistoryResponse(
                routeHistory.getRouteHistoryId(),
                routeHistory.getRouteHistoryId(),
                routeHistory.getRouteName(),
                routeHistory.getStartLatitude().doubleValue(),
                routeHistory.getStartLongitude().doubleValue(),
                routeHistory.getEndLatitude().doubleValue(),
                routeHistory.getEndLongitude().doubleValue(),
                routeHistory.getSearchedAt(),
                routeHistory.getSearchedAt()
        );
    }
}