package com.example.lightsafe.safe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class RouteService {

    @Value("${kakao.rest.api.key}")
    private String kakaoApiKey;

    private final CctvService cctvService;

    // 1. 카카오내비 API 호출 및 파싱
    public RouteDto getSafeRoute(RouteRequestDto request, int routeId) {
        RestTemplate restTemplate = new RestTemplate();
        ObjectMapper objectMapper = new ObjectMapper();

        // 💡 명세서 규칙 반영: 줄임말 대신 전체 필드명(getStartLongitude 등) 적용
        // 카카오 API는 origin=경도(x),위도(y) 순서입니다.
        String url = String.format("https://apis-navi.kakaomobility.com/v1/directions?origin=%f,%f&destination=%f,%f",
                request.getStartLongitude(), request.getStartLatitude(), request.getEndLongitude(), request.getEndLatitude());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + kakaoApiKey);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());

            // JSON에서 vertexes(좌표 배열) 안전하게 추출
            List<LocationDto> path = extractPathFromJson(root);

            // 경로를 찾지 못했을 경우 예외 처리
            if (path == null || path.isEmpty()) {
                return null;
            }

            // 2. 안전도(CCTV 개수) 계산 로직 실행
            int safetyScore = calculateSafetyScore(path);

            return new RouteDto(
                    routeId,
                    path,
                    safetyScore,
                    "CCTV " + safetyScore + "대를 지나는 안전 경로입니다."
            );

        } catch (Exception e) {
            System.err.println("경로 탐색 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // JSON 깊은 곳에 있는 좌표 배열 안전하게 꺼내기
    private List<LocationDto> extractPathFromJson(JsonNode root) {
        List<LocationDto> path = new ArrayList<>();

        try {
            JsonNode routes = root.path("routes");
            if (routes.isMissingNode() || routes.isEmpty()) return path;

            JsonNode sections = routes.get(0).path("sections");
            if (sections.isMissingNode() || sections.isEmpty()) return path;

            JsonNode roads = sections.get(0).path("roads");
            if (roads.isMissingNode() || roads.isEmpty()) return path;

            for (JsonNode road : roads) {
                JsonNode vertexes = road.path("vertexes");
                for (int i = 0; i < vertexes.size(); i += 2) {
                    // 💡 명세서 규칙 반영: 내부 변수명도 구체적으로 변경
                    double longitude = vertexes.get(i).asDouble();
                    double latitude = vertexes.get(i + 1).asDouble();
                    path.add(new LocationDto(latitude, longitude));
                }
            }
        } catch (Exception e) {
            System.err.println("JSON 데이터 추출 중 문제 발생: " + e.getMessage());
        }

        return path;
    }

    // 3. CCTV 데이터를 기반으로 안전 점수 계산 (🔥 중복 카운트 방지 적용)
    private int calculateSafetyScore(List<LocationDto> path) {
        List<CctvDto> allCctvs = cctvService.getCctvData();

        if (allCctvs == null || allCctvs.isEmpty()) {
            return 0; // CCTV 데이터가 없으면 안전 점수 0점 처리
        }

        // 중복을 허용하지 않는 Set 자료구조 생성
        Set<Long> countedCctvIds = new HashSet<>();

        for (LocationDto point : path) {
            for (CctvDto cctv : allCctvs) {
                // 💡 명세서 규칙 반영: point.getLat() -> point.getLatitude()로 변경
                double distance = getDistance(point.getLatitude(), point.getLongitude(), cctv.getLatitude(), cctv.getLongitude());

                // 50m 이내에 있는 CCTV를 발견하면 Set에 ID를 추가
                if (distance <= 50.0) {
                    countedCctvIds.add(cctv.getCctvId());
                }
            }
        }

        // Set의 크기가 곧 '중복 없이 지나친 실제 CCTV의 개수'가 됨
        return countedCctvIds.size();
    }

    // 두 위경도 사이의 거리(미터) 계산 공식 (Haversine formula)
    private double getDistance(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000; // 지구 반지름 (미터)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}