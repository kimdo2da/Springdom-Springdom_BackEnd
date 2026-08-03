package com.example.lightsafe.safe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class KakaoLocalService {

    private static final String KAKAO_LOCAL_CATEGORY_URL =
            "https://dapi.kakao.com/v2/local/search/category.json";

    private static final String CONVENIENCE_STORE_CATEGORY_CODE =
            "CS2";

    private static final int SEARCH_RADIUS_METERS =
            50;

    @Value("${kakao.rest.api.key:}")
    private String kakaoRestApiKey;

    public List<LocationDto> getConvenienceStores(
            List<LocationDto> path
    ) {
        List<LocationDto> storeLocations =
                new ArrayList<>();

        if (path == null
                || path.isEmpty()) {

            return storeLocations;
        }

        if (kakaoRestApiKey == null
                || kakaoRestApiKey.isBlank()) {

            log.warn(
                    "kakao.rest.api.key가 설정되어 있지 않아 편의점 검색을 건너뜁니다."
            );

            return storeLocations;
        }

        RestTemplate restTemplate =
                new RestTemplate();

        ObjectMapper objectMapper =
                new ObjectMapper();

        Set<String> uniqueStoreIds =
                new HashSet<>();

        HttpHeaders headers =
                new HttpHeaders();

        headers.set(
                "Authorization",
                "KakaoAK " + kakaoRestApiKey
        );

        HttpEntity<String> entity =
                new HttpEntity<>(
                        headers
                );

        int step =
                Math.max(
                        1,
                        path.size() / 10
                );

        for (int i = 0; i < path.size(); i += step) {
            LocationDto point =
                    path.get(
                            i
                    );

            String url =
                    UriComponentsBuilder
                            .fromUriString(
                                    KAKAO_LOCAL_CATEGORY_URL
                            )
                            .queryParam(
                                    "category_group_code",
                                    CONVENIENCE_STORE_CATEGORY_CODE
                            )
                            .queryParam(
                                    "x",
                                    point.getLongitude()
                            )
                            .queryParam(
                                    "y",
                                    point.getLatitude()
                            )
                            .queryParam(
                                    "radius",
                                    SEARCH_RADIUS_METERS
                            )
                            .toUriString();

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

                JsonNode documents =
                        root.path(
                                "documents"
                        );

                if (!documents.isArray()) {
                    continue;
                }

                for (JsonNode document : documents) {
                    String storeId =
                            document.path(
                                    "id"
                            ).asText();

                    if (!uniqueStoreIds.add(
                            storeId
                    )) {
                        continue;
                    }

                    double longitude =
                            document.path(
                                    "x"
                            ).asDouble();

                    double latitude =
                            document.path(
                                    "y"
                            ).asDouble();

                    storeLocations.add(
                            new LocationDto(
                                    latitude,
                                    longitude
                            )
                    );
                }

            } catch (Exception e) {
                log.warn(
                        "카카오 로컬 API 편의점 검색 중 오류가 발생했습니다. latitude={}, longitude={}",
                        point.getLatitude(),
                        point.getLongitude(),
                        e
                );
            }
        }

        return storeLocations;
    }
}