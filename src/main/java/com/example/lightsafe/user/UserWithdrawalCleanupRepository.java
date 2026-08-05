package com.example.lightsafe.user;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserWithdrawalCleanupRepository {

    private final JdbcTemplate jdbcTemplate;

    public int deleteRouteHistory(
            Long userId
    ) {
        return jdbcTemplate.update(
                """
                DELETE FROM route_history
                 WHERE user_id = ?
                """,
                userId
        );
    }

    public int deleteFavoritePlaces(
            Long userId
    ) {
        return jdbcTemplate.update(
                """
                DELETE FROM favorite_places
                 WHERE user_id = ?
                """,
                userId
        );
    }
}