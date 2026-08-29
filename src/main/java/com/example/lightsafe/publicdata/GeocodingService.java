package com.example.lightsafe.publicdata;

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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 좌표 없는 보안등 주소를 좌표로 바꿉니다.
 *
 * 전국 보안등 약 184만 건 중 9% 가량은 위·경도 칸이 비어 있고 주소만 있습니다.
 * (부산 동구처럼 자치단체가 통째로 좌표를 안 채워 올린 경우가 많습니다.)
 * 이런 지점을 그냥 버리면 그 동네만 지도에서 가로등이 사라지므로,
 * 카카오 주소검색으로 좌표를 찾아 채웁니다.
 *
 * 카카오 로컬 API 는 일일 호출 한도가 있어서 한 번에 다 처리하지 않고
 * 정해진 수만큼만 처리하고 나머지는 다음 실행으로 넘깁니다.
 * 한 번 물어본 주소는 geocode_cache 에 남겨 두 번 묻지 않습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private static final String KAKAO_ADDRESS_URL =
            "https://dapi.kakao.com/v2/local/search/address.json";

    private static final String KAKAO_KEYWORD_URL =
            "https://dapi.kakao.com/v2/local/search/keyword.json";

    /**
     * IN 절 한 번에 넣을 주소 수.
     */
    private static final int ADDRESS_CHUNK_SIZE = 500;

    private final PublicDataProperties properties;

    private final GeocodeCacheRepository geocodeCacheRepository;

    private final LampGeocodeQueueRepository lampGeocodeQueueRepository;

    private final PublicDataWriter publicDataWriter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${kakao.rest.api.key:}")
    private String kakaoRestApiKey;

    /**
     * 대기열을 설정된 한도만큼 처리합니다.
     *
     * @return 이번에 좌표를 얻어 street_lamps 로 옮긴 보안등 수
     */
    public int drainQueue() {
        return drainQueue(
                properties.getGeocoding().getMaxAddressesPerRun()
        );
    }

    /**
     * 아직 좌표를 못 구한 주소가 몇 개 남았는지.
     */
    public long countPendingAddresses() {
        return lampGeocodeQueueRepository.countPendingAddresses();
    }

    public int drainQueue(int maxAddresses) {
        PublicDataProperties.Geocoding config =
                properties.getGeocoding();

        if (!config.isEnabled()) {
            log.info("지오코딩이 꺼져 있어 건너뜁니다.");
            return 0;
        }

        if (kakaoRestApiKey == null || kakaoRestApiKey.isBlank()) {
            log.warn(
                    "kakao.rest.api.key 가 없어 좌표 없는 보안등을 채우지 못합니다."
            );
            return 0;
        }

        if (maxAddresses <= 0) {
            return 0;
        }

        List<String> addresses =
                lampGeocodeQueueRepository.findPendingAddresses(maxAddresses);

        if (addresses.isEmpty()) {
            log.info("지오코딩 대기열이 비어 있습니다.");
            return 0;
        }

        log.info(
                "지오코딩을 시작합니다. 이번에 처리할 주소 {}개",
                addresses.size()
        );

        RestTemplate restTemplate = new RestTemplate();
        HttpEntity<Void> entity = createEntity();

        Map<String, GeocodeCache> resolved = new HashMap<>();
        List<GeocodeCache> newCacheEntries = new ArrayList<>();

        /*
         * 실제로 카카오에 물어본 주소만 담습니다.
         *
         * 한도 초과나 통신 오류로 중간에 멈추면, 아직 안 물어본 주소는 대기열에 그대로
         * 남겨야 다음 실행에서 이어서 처리합니다. addresses 를 통째로 지우면
         * 그 동네 가로등이 영영 지도에서 사라집니다.
         */
        List<String> processed = new ArrayList<>();

        int found = 0;
        int missed = 0;
        boolean stopped = false;

        for (String address : addresses) {
            GeocodeCache cached =
                    geocodeCacheRepository.findByAddress(address).orElse(null);

            if (cached == null) {
                try {
                    cached = lookup(restTemplate, entity, address);
                } catch (GeocodingUnavailableException e) {
                    /*
                     * 카카오가 답을 못 준 것이지 '주소가 없다'가 아닙니다.
                     * 이걸 실패로 캐시해 두면 다음 달에도 다시 묻지 않아 영구 결손이 됩니다.
                     * 여기서 멈추고 나머지는 다음 실행으로 넘깁니다.
                     */
                    log.warn(
                            "카카오 지오코딩을 더 진행할 수 없어 이번 실행을 여기서 멈춥니다. "
                                    + "처리한 주소 {}개, 사유: {}",
                            processed.size(),
                            e.getMessage()
                    );
                    stopped = true;
                    break;
                }

                newCacheEntries.add(cached);
                sleep(config.getRequestIntervalMillis());
            }

            processed.add(address);

            if (cached.isFound()) {
                resolved.put(address, cached);
                found++;
            } else {
                missed++;
            }

            if (processed.size() % 500 == 0) {
                log.info(
                        "지오코딩 진행 중. {}/{}개 (성공 {}, 실패 {})",
                        processed.size(),
                        addresses.size(),
                        found,
                        missed
                );
            }
        }

        if (!newCacheEntries.isEmpty()) {
            geocodeCacheRepository.saveAll(newCacheEntries);
        }

        int inserted = materialize(processed, resolved);

        log.info(
                "지오코딩 {}. 주소 {}개 중 {}개 성공, 보안등 {}개를 지도에 추가했습니다. 남은 주소 {}개",
                stopped ? "중단" : "완료",
                processed.size(),
                found,
                inserted,
                lampGeocodeQueueRepository.countPendingAddresses()
        );

        return inserted;
    }

    /**
     * 카카오가 답을 못 준 상태. '주소를 못 찾았다'와 반드시 구분해야 합니다.
     *
     * 일일 한도 초과(429), 인증 오류(401), 통신 실패가 여기 해당합니다.
     * 이런 응답을 '못 찾음'으로 캐시하면 그 주소는 영영 좌표를 못 얻습니다.
     */
    private static class GeocodingUnavailableException extends RuntimeException {
        GeocodingUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 좌표를 알아낸 주소에 걸린 대기열 항목을 실제 보안등 표로 옮기고 대기열에서 지웁니다.
     */
    private int materialize(
            List<String> processedAddresses,
            Map<String, GeocodeCache> resolved
    ) {
        int inserted = 0;

        /*
         * 한 번에 3만 개까지 처리하므로 IN 절에 주소를 통째로 넣으면 질의문이 1MB 를 넘습니다.
         * 나눠서 보냅니다.
         */
        for (int start = 0; start < processedAddresses.size(); start += ADDRESS_CHUNK_SIZE) {
            List<String> chunk =
                    processedAddresses.subList(
                            start,
                            Math.min(start + ADDRESS_CHUNK_SIZE, processedAddresses.size())
                    );

            List<LampGeocodeQueue> queued =
                    lampGeocodeQueueRepository.findByAddressIn(chunk);

            List<RawFacility> facilities = new ArrayList<>();

            for (LampGeocodeQueue item : queued) {
                GeocodeCache cache = resolved.get(item.getAddress());

                if (cache == null) {
                    continue;
                }

                if (!CoordinateSupport.isInKorea(
                        cache.getLatitude(),
                        cache.getLongitude()
                )) {
                    continue;
                }

                facilities.add(
                        new RawFacility(
                                cache.getLatitude(),
                                cache.getLongitude(),
                                item.getAddress(),
                                item.getLampCount(),
                                item.getSido(),
                                null,
                                null
                        )
                );
            }

            inserted +=
                    publicDataWriter.insertSecurityLights(facilities, true);

            /*
             * 찾은 주소든 못 찾은 주소든 이번에 처리한 것은 대기열에서 뺍니다.
             * 못 찾은 주소는 geocode_cache 에 found=false 로 남아 있어
             * 다음 달에 다시 물어보지 않습니다.
             */
            lampGeocodeQueueRepository.deleteByAddressIn(chunk);
        }

        return inserted;
    }

    private GeocodeCache lookup(
            RestTemplate restTemplate,
            HttpEntity<Void> entity,
            String address
    ) {
        /*
         * 지번주소는 주소검색이 잘 맞고, 건물명이 섞인 주소는 키워드검색이 잘 맞습니다.
         * 주소검색을 먼저 하고 못 찾으면 키워드검색으로 한 번 더 물어봅니다.
         */
        double[] point =
                search(restTemplate, entity, KAKAO_ADDRESS_URL, address);

        if (point == null) {
            point = search(restTemplate, entity, KAKAO_KEYWORD_URL, address);
        }

        if (point == null) {
            return new GeocodeCache(address, null, null, false);
        }

        return new GeocodeCache(address, point[0], point[1], true);
    }

    /**
     * @return {위도, 경도}, 또는 카카오가 '그런 주소 없다'고 답하면 null
     * @throws GeocodingUnavailableException 카카오가 답 자체를 못 준 경우(한도 초과·인증·통신)
     */
    private double[] search(
            RestTemplate restTemplate,
            HttpEntity<Void> entity,
            String baseUrl,
            String address
    ) {
        String body;

        try {
            /*
             * 한글 주소가 그대로 나가면 서버가 못 읽습니다. encode() 가 UTF-8 로 바꿔 줍니다.
             */
            URI uri =
                    UriComponentsBuilder
                            .fromUriString(baseUrl)
                            .queryParam("query", address)
                            .queryParam("size", 1)
                            .encode()
                            .build()
                            .toUri();

            ResponseEntity<String> response =
                    restTemplate.exchange(
                            uri,
                            HttpMethod.GET,
                            entity,
                            String.class
                    );

            body = response.getBody();

        } catch (Exception e) {
            /*
             * 4xx·5xx·타임아웃은 전부 여기로 옵니다. 주소가 없다는 뜻이 아니므로
             * 실패로 캐시하지 않고 위로 올립니다.
             */
            throw new GeocodingUnavailableException(
                    "카카오 응답을 받지 못했습니다: " + e.getMessage(),
                    e
            );
        }

        try {
            JsonNode documents =
                    objectMapper.readTree(body).path("documents");

            if (!documents.isArray() || documents.isEmpty()) {
                return null;
            }

            JsonNode first = documents.get(0);

            Double longitude =
                    CoordinateSupport.parseCoordinate(first.path("x").asText(null));

            Double latitude =
                    CoordinateSupport.parseCoordinate(first.path("y").asText(null));

            if (!CoordinateSupport.isInKorea(latitude, longitude)) {
                return null;
            }

            return new double[]{latitude, longitude};

        } catch (Exception e) {
            // 200 을 받았는데 해석이 안 되는 경우. 이건 그 주소의 문제로 본다.
            log.debug(
                    "카카오 응답을 해석하지 못했습니다. address={}, message={}",
                    address,
                    e.getMessage()
            );
            return null;
        }
    }

    private HttpEntity<Void> createEntity() {
        HttpHeaders headers = new HttpHeaders();

        headers.set(
                "Authorization",
                "KakaoAK " + kakaoRestApiKey
        );

        return new HttpEntity<>(headers);
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }

        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "지오코딩이 중단되었습니다.",
                    e
            );
        }
    }
}
