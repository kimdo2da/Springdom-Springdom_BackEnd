package com.example.lightsafe.routehistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RouteHistoryRepository
        extends JpaRepository<RouteHistory, Long> {

    List<RouteHistory> findTop10ByUser_UserIdOrderBySearchedAtDescRouteHistoryIdDesc(
            Long userId
    );

    Optional<RouteHistory> findByRouteHistoryIdAndUser_UserId(
            Long routeHistoryId,
            Long userId
    );

    void deleteAllByUser_UserId(
            Long userId
    );
}