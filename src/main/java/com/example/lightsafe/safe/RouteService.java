package com.example.lightsafe.safe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteService {

    @Value("${kakao.rest.api.key}")
    private String kakaoApiKey;

    private final CctvService cctvService;

    public RouteDto getSafeRoute(
            RouteRequestDto request,
            int routeId
    ) {
        RestTemplate restTemplate =
                new RestTemplate();

        ObjectMapper objectMapper =
                new ObjectMapper();

        String url =
                String.format(
                        "https://apis-navi.kakaomobility.com/v1/directions?origin=%f,%f&destination=%f,%f",
                        request.getStartLongitude(),
                        request.getStartLatitude(),
                        request.getEndLongitude(),
                        request.getEndLatitude()
                );

        HttpHeaders headers =
                new HttpHeaders();

        headers.set(
                "Authorization",
                "KakaoAK " + kakaoApiKey
        );

        HttpEntity<String> entity =
                new HttpEntity<>(
                        headers
                );

        try {
            ResponseEntity<String> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            entity,
                            String.class
                    );

            JsonNode root =
                    objectMapper.readTree(
                            response.getBody()
                    );

            List<LocationDto> path =
                    extractPathFromJson(
                            root
                    );

            if (path == null
                    || path.isEmpty()) {

                log.warn(
                        "경로 탐색 결과가 비어 있습니다. routeId={}",
                        routeId
                );

                return null;
            }

            int safetyScore =
                    calculateSafetyScore(
                            path
                    );

            return new RouteDto(
                    routeId,
                    path,
                    safetyScore,
                    "CCTV " + safetyScore + "대를 지나는 안전 경로입니다."
            );

        } catch (Exception e) {
            log.error(
                    "경로 탐색 중 오류가 발생했습니다. routeId={}",
                    routeId,
                    e
            );

            return null;
        }
    }

    private List<LocationDto> extractPathFromJson(
            JsonNode root
    ) {
        List<LocationDto> path =
                new ArrayList<>();

        try {
            JsonNode routes =
                    root.path(
                            "routes"
                    );

            if (routes.isMissingNode()
                    || routes.isEmpty()) {

                return path;
            }

            JsonNode sections =
                    routes.get(0)
                            .path(
                                    "sections"
                            );

            if (sections.isMissingNode()
                    || sections.isEmpty()) {

                return path;
            }

            JsonNode roads =
                    sections.get(0)
                            .path(
                                    "roads"
                            );

            if (roads.isMissingNode()
                    || roads.isEmpty()) {

                return path;
            }

            for (JsonNode road : roads) {
                JsonNode vertexes =
                        road.path(
                                "vertexes"
                        );

                for (int i = 0; i < vertexes.size(); i += 2) {
                    double longitude =
                            vertexes.get(i)
                                    .asDouble();

                    double latitude =
                            vertexes.get(i + 1)
                                    .asDouble();

                    path.add(
                            new LocationDto(
                                    latitude,
                                    longitude
                            )
                    );
                }
            }

        } catch (Exception e) {
            log.warn(
                    "JSON 데이터에서 경로 좌표를 추출하는 중 문제가 발생했습니다.",
                    e
            );
        }

        return path;
    }

    private int calculateSafetyScore(
            List<LocationDto> path
    ) {
        List<CctvDto> allCctvs =
                cctvService.getCctvData();

        if (allCctvs == null
                || allCctvs.isEmpty()) {

            return 0;
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

                if (distance <= 50.0) {
                    countedCctvIds.add(
                            cctv.getCctvId()
                    );
                }
            }
        }

        return countedCctvIds.size();
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