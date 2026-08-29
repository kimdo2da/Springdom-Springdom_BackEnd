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
    private static final int SECURITY_LIGHT_SCORE =
            1;

    private static final int CONVENIENCE_STORE_SCORE =
            3;

    private static final int CCTV_SCORE =
            3;

    private static final int PUBLIC_SAFETY_FACILITY_SCORE =
            4;

    private static final double DUPLICATE_ROUTE_DISTANCE_DIFF_RATIO =
            0.05;

    private static final double DUPLICATE_ROUTE_ENDPOINT_THRESHOLD_METERS =
            30.0;

    private static final double DUPLICATE_ROUTE_MIDPOINT_THRESHOLD_METERS =
            50.0;

    private static final int[] TMAP_SEARCH_OPTIONS =
            {0, 4, 10};

    @Value("${tmap.api.key:}")
    private String tmapApiKey;

    private final CctvService cctvService;
    private final KakaoLocalService kakaoLocalService;
    private final SecurityLightService securityLightService; // 🔥 보안등 서비스 주입

    public List<RouteDto> getTop3SafeRoutes(
            RouteRequestDto request
    ) {
        validateRouteRequest(request);

        if (tmapApiKey == null || tmapApiKey.isBlank()) {
            log.warn("tmap.api.key가 설정되어 있지 않습니다.");
            return List.of();
        }

        List<RouteDto> results = new ArrayList<>();

        for (int searchOption : TMAP_SEARCH_OPTIONS) {
            RouteDto route = fetchTmapRoute(request, searchOption);

            if (route == null || route.getPath() == null || route.getPath().isEmpty()) {
                continue;
            }

            if (isDuplicateRoute(results, route, searchOption)) {
                continue;
            }

            results.add(route);
        }

        results.sort(
                (left, right) -> Integer.compare(right.getSafetyScore(), left.getSafetyScore())
        );

        for (int i = 0; i < results.size(); i++) {
            RouteDto route = results.get(i);
            route.setRouteId(i + 1);
            route.setDescription(
                    "안전 순위 " + (i + 1) + "위 경로 "
                            + "(종합 안전 점수: " + route.getSafetyScore() + "점, "
                            + "CCTV " + safeSize(route.getCctvLocations()) + "개×" + CCTV_SCORE + "점, "
                            + "편의점 " + safeSize(route.getStoreLocations()) + "개×" + CONVENIENCE_STORE_SCORE + "점, "
                            + "보안등 " + safeSize(route.getSecurityLightLocations()) + "개×" + SECURITY_LIGHT_SCORE + "점, "
                            + "치안시설 0개×" + PUBLIC_SAFETY_FACILITY_SCORE + "점)"
            );
        }

        return results;
    }

    public RouteDto getSafeRoute(
            RouteRequestDto request,
            int routeId
    ) {
        List<RouteDto> routes = getTop3SafeRoutes(request);
        if (routes.isEmpty()) return null;
        RouteDto route = routes.get(0);
        route.setRouteId(routeId);
        return route;
    }

    private RouteDto fetchTmapRoute(
            RouteRequestDto request,
            int searchOption
    ) {
        RestTemplate restTemplate = new RestTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("accept", "application/json");
        headers.set("appKey", tmapApiKey);

        Map<String, Object> payload = Map.of(
                "startX", request.getStartLongitude(),
                "startY", request.getStartLatitude(),
                "endX", request.getEndLongitude(),
                "endY", request.getEndLatitude(),
                "startName", "출발지",
                "endName", "도착지",
                "reqCoordType", "WGS84GEO",
                "resCoordType", "WGS84GEO",
                "searchOption", searchOption
        );

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    TMAP_PEDESTRIAN_ROUTE_URL, entity, String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            List<LocationDto> path = extractPathFromTmapJson(root);
            double tmapTotalDistance = extractTmapTotalDistance(root);

            if (path == null || path.isEmpty()) {
                log.warn("TMAP 경로 탐색 결과가 비어 있습니다. searchOption={}", searchOption);
                return null;
            }

            RouteDto route = new RouteDto();
            route.setPath(path);

            analyzeSafetyData(route, path);
            log.info("TMAP 경로 후보. searchOption={}, tmapTotalDistanceMeter={}, calculatedDistanceMeter={}, pathPointCount={}, safetyScore={}",
                    searchOption, Math.round(tmapTotalDistance), Math.round(calculatePathDistance(path)), path.size(), route.getSafetyScore());

            return route;

        } catch (Exception e) {
            log.warn("TMAP 보행자 경로 탐색 중 오류가 발생했습니다. searchOption={}", searchOption, e);
            return null;
        }
    }

    private List<LocationDto> extractPathFromTmapJson(JsonNode root) {
        List<LocationDto> path = new ArrayList<>();
        try {
            JsonNode features = root.path("features");
            if (features.isMissingNode() || !features.isArray()) return path;

            for (JsonNode feature : features) {
                JsonNode geometry = feature.path("geometry");
                String type = geometry.path("type").asText();
                JsonNode coordinates = geometry.path("coordinates");

                if ("Point".equals(type)) {
                    if (coordinates.size() >= 2) {
                        addPoint(path, coordinates.get(1).asDouble(), coordinates.get(0).asDouble());
                    }
                    continue;
                }

                if ("LineString".equals(type)) {
                    for (JsonNode pointNode : coordinates) {
                        if (pointNode.size() >= 2) {
                            addPoint(path, pointNode.get(1).asDouble(), pointNode.get(0).asDouble());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("TMAP GeoJSON에서 경로 좌표를 추출하는 중 문제가 발생했습니다.", e);
        }
        return path;
    }

    private double extractTmapTotalDistance(JsonNode root) {
        try {
            JsonNode features = root.path("features");
            if (features.isMissingNode() || !features.isArray()) return 0.0;

            for (JsonNode feature : features) {
                JsonNode totalDistance = feature.path("properties").path("totalDistance");
                if (totalDistance.isNumber()) return totalDistance.asDouble();
            }
        } catch (Exception e) {
            log.warn("TMAP 응답에서 totalDistance를 추출하는 중 문제가 발생했습니다.", e);
        }
        return 0.0;
    }

    private void addPoint(List<LocationDto> path, double latitude, double longitude) {
        if (path.isEmpty()) {
            path.add(new LocationDto(latitude, longitude));
            return;
        }
        LocationDto lastPoint = path.get(path.size() - 1);
        if (Double.compare(lastPoint.getLatitude(), latitude) != 0 || Double.compare(lastPoint.getLongitude(), longitude) != 0) {
            path.add(new LocationDto(latitude, longitude));
        }
    }

    // 🔥 안전도 분석 로직
    private void analyzeSafetyData(
            RouteDto route,
            List<LocationDto> path
    ) {
        List<LocationDto> cctvLocations =
                findNearbyCctvLocations(path);

        List<LocationDto> storeLocations =
                kakaoLocalService.getConvenienceStores(path);

        List<LocationDto> lightLocations =
                findNearbySecurityLights(path);

        /*
         * 치안시설은 아직 데이터/API가 연결되지 않았기 때문에
         * 현재는 빈 목록으로 처리합니다.
         *
         * 추후 치안시설 서비스가 구현되면
         * findNearbyPublicSafetyFacilities(path) 내부만 실제 조회 로직으로 교체하면 됩니다.
         */
        List<LocationDto> publicSafetyFacilityLocations =
                findNearbyPublicSafetyFacilities(path);

        route.setCctvLocations(cctvLocations);
        route.setStoreLocations(storeLocations);
        route.setSecurityLightLocations(lightLocations);

        int safetyScore =
                calculateWeightedSafetyScore(
                        cctvLocations,
                        storeLocations,
                        lightLocations,
                        publicSafetyFacilityLocations
                );

        route.setSafetyScore(safetyScore);

        log.info(
                "경로 안전도 분석 완료. safetyScore={}, cctvCount={}, storeCount={}, lightCount={}, publicSafetyFacilityCount={}",
                route.getSafetyScore(),
                cctvLocations.size(),
                storeLocations.size(),
                lightLocations.size(),
                publicSafetyFacilityLocations.size()
        );
    }
    private int calculateWeightedSafetyScore(
            List<LocationDto> cctvLocations,
            List<LocationDto> storeLocations,
            List<LocationDto> lightLocations,
            List<LocationDto> publicSafetyFacilityLocations
    ) {
        return safeSize(cctvLocations) * CCTV_SCORE
                + safeSize(storeLocations) * CONVENIENCE_STORE_SCORE
                + safeSize(lightLocations) * SECURITY_LIGHT_SCORE
                + safeSize(publicSafetyFacilityLocations) * PUBLIC_SAFETY_FACILITY_SCORE;
    }

    private List<LocationDto> findNearbyCctvLocations(List<LocationDto> path) {
        List<CctvDto> allCctvs = cctvService.getCctvData();
        List<LocationDto> cctvLocations = new ArrayList<>();
        if (allCctvs == null || allCctvs.isEmpty()) return cctvLocations;

        Set<Long> countedCctvIds = new HashSet<>();
        for (LocationDto point : path) {
            for (CctvDto cctv : allCctvs) {
                double distance = getDistance(
                        point.getLatitude(), point.getLongitude(),
                        cctv.getLatitude(), cctv.getLongitude()
                );
                if (distance <= SAFETY_SEARCH_RADIUS_METERS && countedCctvIds.add(cctv.getCctvId())) {
                    cctvLocations.add(new LocationDto(cctv.getLatitude(), cctv.getLongitude()));
                }
            }
        }
        return cctvLocations;
    }
    private List<LocationDto> findNearbyPublicSafetyFacilities(
            List<LocationDto> path
    ) {
        /*
         * TODO:
         * 치안시설 데이터가 연결되면 이 메서드에서
         * 경로 주변 50m 이내 치안시설 좌표를 찾아 반환하도록 구현합니다.
         *
         * 현재는 미구현 상태이므로 빈 목록을 반환합니다.
         */
        return List.of();
    }

    // 🔥 주변 보안등 탐색 로직
    private List<LocationDto> findNearbySecurityLights(List<LocationDto> path) {
        List<LocationDto> allLights = securityLightService.getSecurityLightData();
        List<LocationDto> lightLocations = new ArrayList<>();
        if (allLights == null || allLights.isEmpty()) return lightLocations;

        Set<String> countedLights = new HashSet<>();
        for (LocationDto point : path) {
            for (LocationDto light : allLights) {
                double distance = getDistance(
                        point.getLatitude(), point.getLongitude(),
                        light.getLatitude(), light.getLongitude()
                );

                String lightKey = light.getLatitude() + "_" + light.getLongitude();
                if (distance <= SAFETY_SEARCH_RADIUS_METERS && countedLights.add(lightKey)) {
                    lightLocations.add(new LocationDto(light.getLatitude(), light.getLongitude()));
                }
            }
        }
        return lightLocations;
    }

    private boolean isDuplicateRoute(List<RouteDto> routes, RouteDto candidate, int searchOption) {
        if (routes == null || routes.isEmpty() || candidate == null || candidate.getPath() == null || candidate.getPath().isEmpty()) {
            return false;
        }

        for (int i = 0; i < routes.size(); i++) {
            RouteDto route = routes.get(i);
            if (route == null || route.getPath() == null || route.getPath().isEmpty()) continue;

            if (isSameRoute(route.getPath(), candidate.getPath())) {
                log.info("TMAP 경로 중복 제거. searchOption={}, duplicatedWithIndex={}", searchOption, i);
                return true;
            }
        }
        log.info("TMAP 경로 후보 채택. searchOption={}", searchOption);
        return false;
    }

    private boolean isSameRoute(List<LocationDto> firstPath, List<LocationDto> secondPath) {
        if (firstPath == null || secondPath == null || firstPath.size() < 2 || secondPath.size() < 2) return false;

        LocationDto firstStart = firstPath.get(0);
        LocationDto secondStart = secondPath.get(0);
        LocationDto firstEnd = firstPath.get(firstPath.size() - 1);
        LocationDto secondEnd = secondPath.get(secondPath.size() - 1);

        if (getDistance(firstStart.getLatitude(), firstStart.getLongitude(), secondStart.getLatitude(), secondStart.getLongitude()) > DUPLICATE_ROUTE_ENDPOINT_THRESHOLD_METERS) return false;
        if (getDistance(firstEnd.getLatitude(), firstEnd.getLongitude(), secondEnd.getLatitude(), secondEnd.getLongitude()) > DUPLICATE_ROUTE_ENDPOINT_THRESHOLD_METERS) return false;

        double firstDistance = calculatePathDistance(firstPath);
        double secondDistance = calculatePathDistance(secondPath);
        double maxDistance = Math.max(firstDistance, secondDistance);

        if (maxDistance <= 0) return false;
        double distanceDiffRatio = Math.abs(firstDistance - secondDistance) / maxDistance;
        if (distanceDiffRatio > DUPLICATE_ROUTE_DISTANCE_DIFF_RATIO) return false;

        return isSameRoutePointAtRatio(firstPath, secondPath, 0.25)
                && isSameRoutePointAtRatio(firstPath, secondPath, 0.50)
                && isSameRoutePointAtRatio(firstPath, secondPath, 0.75);
    }

    private boolean isSameRoutePointAtRatio(List<LocationDto> firstPath, List<LocationDto> secondPath, double ratio) {
        LocationDto firstPoint = getPointAtDistanceRatio(firstPath, ratio);
        LocationDto secondPoint = getPointAtDistanceRatio(secondPath, ratio);

        if (firstPoint == null || secondPoint == null) return false;

        double distance = getDistance(firstPoint.getLatitude(), firstPoint.getLongitude(), secondPoint.getLatitude(), secondPoint.getLongitude());
        return distance <= DUPLICATE_ROUTE_MIDPOINT_THRESHOLD_METERS;
    }

    private LocationDto getPointAtDistanceRatio(List<LocationDto> path, double ratio) {
        if (path == null || path.isEmpty()) return null;
        if (path.size() == 1) return path.get(0);

        double totalDistance = calculatePathDistance(path);
        if (totalDistance <= 0) return path.get(0);

        double targetDistance = totalDistance * ratio;
        double accumulatedDistance = 0.0;

        for (int i = 1; i < path.size(); i++) {
            LocationDto previous = path.get(i - 1);
            LocationDto current = path.get(i);
            double segmentDistance = getDistance(previous.getLatitude(), previous.getLongitude(), current.getLatitude(), current.getLongitude());

            if (segmentDistance <= 0) continue;

            if (accumulatedDistance + segmentDistance >= targetDistance) {
                double segmentRatio = (targetDistance - accumulatedDistance) / segmentDistance;
                double latitude = previous.getLatitude() + (current.getLatitude() - previous.getLatitude()) * segmentRatio;
                double longitude = previous.getLongitude() + (current.getLongitude() - previous.getLongitude()) * segmentRatio;
                return new LocationDto(latitude, longitude);
            }
            accumulatedDistance += segmentDistance;
        }
        return path.get(path.size() - 1);
    }

    private double calculatePathDistance(List<LocationDto> path) {
        if (path == null || path.size() < 2) return 0.0;
        double totalDistance = 0.0;
        for (int i = 1; i < path.size(); i++) {
            LocationDto previous = path.get(i - 1);
            LocationDto current = path.get(i);
            totalDistance += getDistance(previous.getLatitude(), previous.getLongitude(), current.getLatitude(), current.getLongitude());
        }
        return totalDistance;
    }

    private int safeSize(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private void validateRouteRequest(RouteRequestDto request) {
        if (request == null) throw new IllegalArgumentException("경로 요청 정보가 비어 있습니다.");
        if (!isValidLatitude(request.getStartLatitude()) || !isValidLatitude(request.getEndLatitude()) ||
                !isValidLongitude(request.getStartLongitude()) || !isValidLongitude(request.getEndLongitude())) {
            throw new IllegalArgumentException("위도 또는 경도 값이 올바르지 않습니다.");
        }
    }

    private boolean isValidLatitude(double latitude) {
        return latitude >= -90 && latitude <= 90;
    }

    private boolean isValidLongitude(double longitude) {
        return longitude >= -180 && longitude <= 180;
    }

    private double getDistance(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusMeters = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusMeters * c;
    }
}