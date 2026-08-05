package com.example.lightsafe.routehistory;

import com.example.lightsafe.common.exception.NotFoundException;
import com.example.lightsafe.safe.RouteRequestDto;
import com.example.lightsafe.user.CurrentUserService;
import com.example.lightsafe.user.User;
import com.example.lightsafe.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouteHistoryService {

    private static final String DEFAULT_ROUTE_NAME =
            "최근 검색 경로";

    private final RouteHistoryRepository routeHistoryRepository;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;

    @Transactional
    public RouteHistoryResponse saveRouteHistory(
            RouteHistoryCreateRequest request
    ) {
        User loginUser =
                currentUserService.getCurrentUser();

        RouteHistory routeHistory =
                createRouteHistory(
                        loginUser,
                        normalizedRouteName(
                                request.routeName()
                        ),
                        request.startLatitude(),
                        request.startLongitude(),
                        request.endLatitude(),
                        request.endLongitude()
                );

        RouteHistory savedRouteHistory =
                routeHistoryRepository.save(
                        routeHistory
                );

        return RouteHistoryResponse.from(
                savedRouteHistory
        );
    }

    /**
     * /routes는 공개 API 성격이 있으므로,
     * 로그인 사용자가 호출한 경우에만 최근경로를 자동 저장합니다.
     * 비로그인 사용자는 경로 탐색만 하고 저장은 건너뜁니다.
     */
    @Transactional
    public void saveFromRouteSearchIfAuthenticated(
            RouteRequestDto request
    ) {
        Long loginUserId =
                getAuthenticatedUserIdOrNull();

        if (loginUserId == null) {
            return;
        }

        User loginUser =
                userRepository
                        .findByUserIdAndDeletedFalse(
                                loginUserId
                        )
                        .orElse(null);

        if (loginUser == null) {
            return;
        }

        RouteHistory routeHistory =
                createRouteHistory(
                        loginUser,
                        DEFAULT_ROUTE_NAME,
                        request.getStartLatitude(),
                        request.getStartLongitude(),
                        request.getEndLatitude(),
                        request.getEndLongitude()
                );

        routeHistoryRepository.save(
                routeHistory
        );
    }

    public List<RouteHistoryResponse> getMyRouteHistories() {
        Long loginUserId =
                currentUserService.getCurrentUserId();

        return routeHistoryRepository
                .findTop10ByUser_UserIdOrderBySearchedAtDescRouteHistoryIdDesc(
                        loginUserId
                )
                .stream()
                .map(
                        RouteHistoryResponse::from
                )
                .toList();
    }

    @Transactional
    public void deleteMyRouteHistory(
            Long routeHistoryId
    ) {
        Long loginUserId =
                currentUserService.getCurrentUserId();

        RouteHistory routeHistory =
                routeHistoryRepository
                        .findByRouteHistoryIdAndUser_UserId(
                                routeHistoryId,
                                loginUserId
                        )
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "존재하지 않거나 삭제 권한이 없는 최근 경로입니다. id="
                                                + routeHistoryId
                                )
                        );

        routeHistoryRepository.delete(
                routeHistory
        );
    }

    @Transactional
    public void deleteAllMyRouteHistories() {
        Long loginUserId =
                currentUserService.getCurrentUserId();

        routeHistoryRepository.deleteAllByUser_UserId(
                loginUserId
        );
    }

    private RouteHistory createRouteHistory(
            User user,
            String routeName,
            double startLatitude,
            double startLongitude,
            double endLatitude,
            double endLongitude
    ) {
        RouteHistory routeHistory =
                new RouteHistory();

        routeHistory.setUser(
                user
        );

        routeHistory.setRouteName(
                routeName
        );

        routeHistory.setStartLatitude(
                toCoordinate(
                        startLatitude
                )
        );

        routeHistory.setStartLongitude(
                toCoordinate(
                        startLongitude
                )
        );

        routeHistory.setEndLatitude(
                toCoordinate(
                        endLatitude
                )
        );

        routeHistory.setEndLongitude(
                toCoordinate(
                        endLongitude
                )
        );

        routeHistory.setSearchedAt(
                LocalDateTime.now()
        );

        return routeHistory;
    }

    private BigDecimal toCoordinate(
            double value
    ) {
        return BigDecimal
                .valueOf(
                        value
                )
                .setScale(
                        7,
                        RoundingMode.HALF_UP
                );
    }

    private String normalizedRouteName(
            String routeName
    ) {
        if (routeName == null
                || routeName.isBlank()) {

            return DEFAULT_ROUTE_NAME;
        }

        return routeName.trim();
    }

    private Long getAuthenticatedUserIdOrNull() {
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()) {

            return null;
        }

        Object principal =
                authentication.getPrincipal();

        if (principal == null
                || "anonymousUser".equals(principal)) {

            return null;
        }

        if (principal instanceof Long userId) {
            return userId;
        }

        if (principal instanceof String value) {
            try {
                return Long.valueOf(
                        value
                );

            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }
}