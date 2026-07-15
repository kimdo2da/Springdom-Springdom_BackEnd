package com.example.lightsafe.safe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Service
public class ConvenienceStoreService {

    // RouteService에서 카카오모빌리티(길찾기) 호출에 쓰는 것과 동일한 REST API 키를 재사용합니다.
    // 카카오 디벨로퍼스 콘솔에서 "로컬" API가 해당 앱에 활성화되어 있어야 합니다.
    @Value("${kakao.rest.api.key}")
    private String kakaoApiKey;

    private static final String BASE_URL = "https://dapi.kakao.com/v2/local/search/category.json";
    private static final String CATEGORY_CONVENIENCE_STORE = "CS2";
    private static final int PAGE_SIZE = 15; // 카카오 API 페이지당 최대 15건
    private static final int MAX_PAGE = 3;   // 카카오 API 카테고리 검색은 최대 3페이지(45건)까지만 허용

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 주어진 좌표 반경(radius, 단위 m) 내의 편의점을 조회합니다.
     * @param lat 위도
     * @param lng 경도
     * @param radius 반경(m), 최대 20000(20km)
     */
    public List<ConvenienceStoreDto> searchNearby(double lat, double lng, int radius) {
        List<ConvenienceStoreDto> result = new ArrayList<>();

        if (kakaoApiKey == null || kakaoApiKey.isBlank()) {
            System.err.println("🚨 kakao.rest.api.key 가 설정되지 않아 편의점 검색을 수행할 수 없습니다.");
            return result;
        }

        int safeRadius = Math.min(Math.max(radius, 1), 20000);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + kakaoApiKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            for (int page = 1; page <= MAX_PAGE; page++) {
                URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
                        .queryParam("category_group_code", CATEGORY_CONVENIENCE_STORE)
                        .queryParam("x", lng)
                        .queryParam("y", lat)
                        .queryParam("radius", safeRadius)
                        .queryParam("page", page)
                        .queryParam("size", PAGE_SIZE)
                        .queryParam("sort", "distance")
                        .build(true)
                        .toUri();

                String rawResponse = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class).getBody();
                JsonNode root = objectMapper.readTree(rawResponse);
                JsonNode documents = root.path("documents");

                for (JsonNode doc : documents) {
                    ConvenienceStoreDto dto = new ConvenienceStoreDto();
                    dto.setStoreId(doc.path("id").asText(""));
                    dto.setStoreName(doc.path("place_name").asText(""));
                    String roadAddr = doc.path("road_address_name").asText("");
                    String lotAddr = doc.path("address_name").asText("");
                    dto.setAddress(roadAddr.isEmpty() ? lotAddr : roadAddr);
                    dto.setLatitude(doc.path("y").asDouble(0));
                    dto.setLongitude(doc.path("x").asDouble(0));
                    dto.setPhone(doc.path("phone").asText(""));
                    dto.setPlaceUrl(doc.path("place_url").asText(""));
                    result.add(dto);
                }

                boolean isEnd = root.path("meta").path("is_end").asBoolean(true);
                if (isEnd) break;
            }
        } catch (Exception e) {
            System.err.println("🚨 편의점 검색 중 오류 발생: " + e.getMessage());
        }

        return result;
    }
}


