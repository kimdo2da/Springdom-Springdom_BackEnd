package com.example.lightsafe.safe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    private static final String TMAP_PEDESTRIAN_ROUTE_URL =
            "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1";

    private static final double SAFETY_SEARCH_RADIUS_METERS =
            50.0;

    private static final int[] TMAP_SEARCH_OPTIONS =
            {0, 4, 10};

    @Value("${tmap.api.key:}")
    private String tmapApiKey;

    private final CctvService cctvService;
    private final KakaoLocalService kakaoLocalService;

    /**
     * TMAP 보행자 경로 API를 여러 옵션으로 호출해서
     * 안전 점수가 높은 순서대로 최대 3개 경로를 반환합니다.
     */
    public List<RouteDto> getTop3SafeRoutes(
            RouteRequestDto request
    ) {
        validateRouteRequest(
                request
        );

        if (tmapApiKey == null
                || tmapApiKey.isBlank()) {

            log.warn(
                    "tmap.api.key가 설정되어 있지 않습니다."
            );

            return List.of();
        }

        List<RouteDto> results =
                new ArrayList<>();

        for (int searchOption : TMAP_SEARCH_OPTIONS) {
            RouteDto route =
                    fetchTmapRoute(
                            request,
                            searchOption
                    );

            if (route == null
                    || route.getPath() == null
                    || route.getPath().isEmpty()) {

                continue;
            }

            if (isDuplicateRoute(
                    results,
                    route
            )) {
                continue;
            }

            results.add(
                    route
            );
        }

        results.sort(
                (left, right) ->
                        Integer.compare(
                                right.getSafetyScore(),
                                left.getSafetyScore()
                        )
        );

        for (int i = 0; i < results.size(); i++) {
            RouteDto route =
                    results.get(
                            i
                    );

            route.setRouteId(
                    i + 1
            );

            route.setDescription(
                    "안전 순위 "
                            + (i + 1)
                            + "위 경로 (종합 안전 점수: "
                            + route.getSafetyScore()
                            + "점, CCTV "
                            + safeSize(route.getCctvLocations())
                            + "개, 편의점 "
                            + safeSize(route.getStoreLocations())
                            + "개)"
            );
        }

        return results;
    }

    /**
     * 기존 코드 호환용.
     * 혹시 다른 코드가 getSafeRoute(request, routeId)를 호출하고 있으면
     * 가장 안전 점수가 높은 첫 번째 경로를 반환합니다.
     */
    public RouteDto getSafeRoute(
            RouteRequestDto request,
            int routeId
    ) {
        List<RouteDto> routes =
                getTop3SafeRoutes(
                        request
                );

        if (routes.isEmpty()) {
            return null;
        }

        RouteDto route =
                routes.get(
                        0
                );

        route.setRouteId(
                routeId
        );

        return route;
    }

    private RouteDto fetchTmapRoute(
            RouteRequestDto request,
            int searchOption
    ) {
        RestTemplate restTemplate =
                new RestTemplate();

        ObjectMapper objectMapper =
                new ObjectMapper();

        HttpHeaders headers =
                new HttpHeaders();

        headers.setContentType(
                MediaType.APPLICATION_JSON
        );

        headers.set(
                "accept",
                "application/json"
        );

        headers.set(
                "appKey",
                tmapApiKey
        );

        Map<String, Object> payload =
                Map.of(
                        "startX",
                        request.getStartLongitude(),
                        "startY",
                        request.getStartLatitude(),
                        "endX",
                        request.getEndLongitude(),
                        "endY",
                        request.getEndLatitude(),
                        "startName",
                        "출발지",
                        "endName",
                        "도착지",
                        "reqCoordType",
                        "WGS84GEO",
                        "resCoordType",
                        "WGS84GEO",
                        "searchOption",
                        searchOption
                );

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(
                        payload,
                        headers
                );

        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(
                            TMAP_PEDESTRIAN_ROUTE_URL,
                            entity,
                            String.class
                    );

            JsonNode root =
                    objectMapper.readTree(
                            response.getBody()
                    );

            List<LocationDto> path =
                    extractPathFromTmapJson(
                            root
                    );

            if (path == null
                    || path.isEmpty()) {

                log.warn(
                        "TMAP 경로 탐색 결과가 비어 있습니다. searchOption={}",
                        searchOption
                );

                return null;
            }

            RouteDto route =
                    new RouteDto();

            route.setPath(
                    path
            );

            analyzeSafetyData(
                    route,
                    path
            );

            return route;

        } catch (Exception e) {
            log.warn(
                    "TMAP 보행자 경로 탐색 중 오류가 발생했습니다. searchOption={}",
                    searchOption,
                    e
            );

            return null;
        }
    }

    private List<LocationDto> extractPathFromTmapJson(
            JsonNode root
    ) {
        List<LocationDto> path =
                new ArrayList<>();

        try {
            JsonNode features =
                    root.path(
                            "features"
                    );

            if (features.isMissingNode()
                    || !features.isArray()) {

                return path;
            }

            for (JsonNode feature : features) {
                JsonNode geometry =
                        feature.path(
                                "geometry"
                        );

                String type =
                        geometry.path(
                                "type"
                        ).asText();

                JsonNode coordinates =
                        geometry.path(
                                "coordinates"
                        );

                if ("Point".equals(
                        type
                )) {
                    if (coordinates.size() >= 2) {
                        addPoint(
                                path,
                                coordinates.get(1)
                                        .asDouble(),
                                coordinates.get(0)
                                        .asDouble()
                        );
                    }

                    continue;
                }

                if ("LineString".equals(
                        type
                )) {
                    for (JsonNode pointNode : coordinates) {
                        if (pointNode.size() >= 2) {
                            addPoint(
                                    path,
                                    pointNode.get(1)
                                            .asDouble(),
                                    pointNode.get(0)
                                            .asDouble()
                            );
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.warn(
                    "TMAP GeoJSON에서 경로 좌표를 추출하는 중 문제가 발생했습니다.",
                    e
            );
        }

        return path;
    }

    private void addPoint(
            List<LocationDto> path,
            double latitude,
            double longitude
    ) {
        if (path.isEmpty()) {
            path.add(
                    new LocationDto(
                            latitude,
                            longitude
                    )
            );

            return;
        }

        LocationDto lastPoint =
                path.get(
                        path.size() - 1
                );

        if (Double.compare(
                lastPoint.getLatitude(),
                latitude
        ) != 0
                || Double.compare(
                lastPoint.getLongitude(),
                longitude
        ) != 0) {

            path.add(
                    new LocationDto(
                            latitude,
                            longitude
                    )
            );
        }
    }

    private void analyzeSafetyData(
            RouteDto route,
            List<LocationDto> path
    ) {
        List<LocationDto> cctvLocations =
                findNearbyCctvLocations(
                        path
                );

        List<LocationDto> storeLocations =
                kakaoLocalService.getConvenienceStores(
                        path
                );

        route.setCctvLocations(
                cctvLocations
        );

        route.setStoreLocations(
                storeLocations
        );

        route.setSafetyScore(
                cctvLocations.size()
                        + storeLocations.size()
        );

        log.info(
                "경로 안전도 분석 완료. safetyScore={}, cctvCount={}, storeCount={}",
                route.getSafetyScore(),
                cctvLocations.size(),
                storeLocations.size()
        );
    }

    private List<LocationDto> findNearbyCctvLocations(
            List<LocationDto> path
    ) {
        List<CctvDto> allCctvs =
                cctvService.getCctvData();

        List<LocationDto> cctvLocations =
                new ArrayList<>();

        if (allCctvs == null
                || allCctvs.isEmpty()) {

            return cctvLocations;
        }

        Set<Long> countedCctvIds =
                new HashSet<>();

        for (LocationDto point : path) {
            for (CctvDto cctv : allCctvs) {
                double distance =
                        getDistance(
                                point.getLatitude(),
                                point.getLongitude(),
                                cctv.getLatitude(),
                                cctv.getLongitude()
                        );

                if (distance <= SAFETY_SEARCH_RADIUS_METERS
                        && countedCctvIds.add(
                        cctv.getCctvId()
                )) {

                    cctvLocations.add(
                            new LocationDto(
                                    cctv.getLatitude(),
                                    cctv.getLongitude()
                            )
                    );
                }
            }
        }

        return cctvLocations;
    }

    private boolean isDuplicateRoute(
            List<RouteDto> routes,
            RouteDto candidate
    ) {
        for (RouteDto route : routes) {
            if (route.getPath() == null
                    || candidate.getPath() == null) {

                continue;
            }

            if (route.getPath().size()
                    == candidate.getPath().size()) {

                return true;
            }
        }

        return false;
    }

    private int safeSize(
            List<?> values
    ) {
        return values == null
                ? 0
                : values.size();
    }

    private void validateRouteRequest(
            RouteRequestDto request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "경로 요청 정보가 비어 있습니다."
            );
        }

        if (!isValidLatitude(
                request.getStartLatitude()
        )
                || !isValidLatitude(
                request.getEndLatitude()
        )
                || !isValidLongitude(
                request.getStartLongitude()
        )
                || !isValidLongitude(
                request.getEndLongitude()
        )) {

            throw new IllegalArgumentException(
                    "위도 또는 경도 값이 올바르지 않습니다."
            );
        }
    }

    private boolean isValidLatitude(
            double latitude
    ) {
        return latitude >= -90
                && latitude <= 90;
    }

    private boolean isValidLongitude(
            double longitude
    ) {
        return longitude >= -180
                && longitude <= 180;
    }

    private double getDistance(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {
        double earthRadiusMeters =
                6371000;

        double dLat =
                Math.toRadians(
                        lat2 - lat1
                );

        double dLon =
                Math.toRadians(
                        lon2 - lon1
                );

        double a =
                Math.sin(
                        dLat / 2
                ) * Math.sin(
                        dLat / 2
                )
                        + Math.cos(
                        Math.toRadians(
                                lat1
                        )
                ) * Math.cos(
                        Math.toRadians(
                                lat2
                        )
                ) * Math.sin(
                        dLon / 2
                ) * Math.sin(
                        dLon / 2
                );

        double c =
                2 * Math.atan2(
                        Math.sqrt(
                                a
                        ),
                        Math.sqrt(
                                1 - a
                        )
                );

        return earthRadiusMeters * c;
    }
}