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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 좌표 없는 보안등 주소를 좌표로 바꿉니다.
 *
 * 전국 보안등 약 184만 건 중 9% 가량은 위·경도 칸이 비어 있고 주소만 있습니다.
 * (부산 동구처럼 자치단체가 통째로 좌표를 안 채워 올린 경우가 많습니다.)
 * 이런 지점을 그냥 버리면 그 동네만 지도에서 가로등이 사라지므로 주소로 좌표를 찾아 채웁니다.
 *
 * <h2>어디에 물어보나</h2>
 * <ol>
 *   <li><b>브이월드</b>(국토교통부) — 1순위. 국가 주소 원장을 그대로 쓰기 때문에
 *       지번주소가 대부분인 우리 데이터와 잘 맞습니다. 실측 300개 중 87.7%.</li>
 *   <li><b>카카오</b> — 브이월드가 못 찾은 것만. 지자체가 올린 주소에는
 *       '묵1동'(행정동 표기), '거창읍대동리602-6'(붙여 씀) 같은 표기가 섞여 있는데
 *       카카오는 이런 것도 찾아냅니다. 브이월드 실패분의 54% 를 회수해 합계 94.3%.</li>
 * </ol>
 *
 * 카카오까지 가는 주소는 전체의 12% 뿐이라 카카오 호출량이 예전의 1/8 로 줄었고,
 * 적중률은 카카오 단독일 때(약 92%)보다 오히려 높습니다.
 *
 * 두 곳 모두 하루 한도가 있어서 한 번에 다 처리하지 않고 정해진 수만큼만 하고
 * 나머지는 다음 실행으로 넘깁니다. 한 번 물어본 주소는 geocode_cache 에 남겨
 * 두 번 묻지 않습니다.
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
     * 도로명주소로 보이는지. '소곡로80번길 7' 처럼 로/길 뒤에 숫자가 붙습니다.
     *
     * 맞으면 브이월드에 도로명(ROAD)으로 먼저 물어봅니다. 지번(PARCEL)으로 헛물켜는
     * 호출을 한 번 아낍니다. '강동구 길동 123' 처럼 길 뒤에 글자가 오는 지번주소는
     * 여기 걸리지 않습니다.
     */
    private static final Pattern ROAD_ADDRESS_HINT =
            Pattern.compile("[로길]\\s*\\d");

    /**
     * IN 절 한 번에 넣을 주소 수.
     */
    private static final int ADDRESS_CHUNK_SIZE = 500;

    /**
     * 이만큼 연달아 통신에 실패하면 그 서버는 이번 실행에서 접습니다.
     */
    private static final int MAX_FAIL_STREAK = 3;

    /**
     * 이만큼 처리할 때마다 진행 상황을 남기고 캐시를 저장합니다.
     */
    private static final int CACHE_FLUSH_SIZE = 500;

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

        Lookup lookup = new Lookup(config);

        if (!lookup.hasAnyProvider()) {
            log.warn(
                    "publicdata.geocoding.vworld-key 와 kakao.rest.api.key 가 모두 없어 "
                            + "좌표 없는 보안등을 채우지 못합니다."
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
                "지오코딩을 시작합니다. 이번에 처리할 주소 {}개 (사용 가능: {})",
                addresses.size(),
                lookup.describeProviders()
        );

        Map<String, GeocodeCache> resolved = new HashMap<>();
        List<GeocodeCache> newCacheEntries = new ArrayList<>();

        /*
         * 실제로 물어본 주소만 담습니다.
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
                    cached = lookup.resolve(address);
                } catch (GeocodingUnavailableException e) {
                    /*
                     * 서버가 답을 못 준 것이지 '주소가 없다'가 아닙니다.
                     * 이걸 실패로 캐시해 두면 다음 달에도 다시 묻지 않아 영구 결손이 됩니다.
                     * 여기서 멈추고 나머지는 다음 실행으로 넘깁니다.
                     */
                    log.warn(
                            "지오코딩을 더 진행할 수 없어 이번 실행을 여기서 멈춥니다. "
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

            if (processed.size() % CACHE_FLUSH_SIZE == 0) {
                log.info(
                        "지오코딩 진행 중. {}/{}개 (성공 {}, 실패 {}) {}",
                        processed.size(),
                        addresses.size(),
                        found,
                        missed,
                        lookup.describeUsage()
                );

                /*
                 * 한 번에 수만 개를 처리하면 실행이 몇 시간짜리가 됩니다.
                 * 끝에 한 번만 저장하면 중간에 서버가 죽었을 때 그동안 쓴 호출이
                 * 전부 날아갑니다. 하루 한도가 있는 자원이라 그냥 넘길 수 없습니다.
                 *
                 * 여기서 저장해 두면 다음 실행이 캐시를 보고 건너뜁니다.
                 * (대기열은 아직 안 지웠으므로 좌표는 다음 실행에서 채워집니다.)
                 */
                flushCache(newCacheEntries);
            }
        }

        flushCache(newCacheEntries);

        int inserted = materialize(processed, resolved);

        log.info(
                "지오코딩 {}. 주소 {}개 중 {}개 성공, 보안등 {}개를 지도에 추가했습니다. "
                        + "{} 남은 주소 {}개",
                stopped ? "중단" : "완료",
                processed.size(),
                found,
                inserted,
                lookup.describeUsage(),
                lampGeocodeQueueRepository.countPendingAddresses()
        );

        return inserted;
    }

    /**
     * 지오코딩 서버가 답을 못 준 상태. '주소를 못 찾았다'와 반드시 구분해야 합니다.
     *
     * 일일 한도 초과, 인증 오류, 통신 실패가 여기 해당합니다.
     * 이런 응답을 '못 찾음'으로 캐시하면 그 주소는 영영 좌표를 못 얻습니다.
     */
    private static class GeocodingUnavailableException extends RuntimeException {
        GeocodingUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }

        GeocodingUnavailableException(String message) {
            super(message, null);
        }
    }

    /**
     * 한 번 실행하는 동안의 조회 상태.
     *
     * 어느 한쪽이 한도에 걸리면 그쪽만 접고 남은 쪽으로 계속 갑니다.
     * 둘 다 접히면 그때 실행을 멈춥니다.
     */
    private final class Lookup {

        private final PublicDataProperties.Geocoding config;

        private final RestTemplate restTemplate = new RestTemplate();

        private boolean vworldUsable;

        private boolean kakaoUsable;

        private int vworldCalls;

        private int kakaoCalls;

        private int vworldHits;

        private int kakaoHits;

        /**
         * 연속 통신 실패 횟수. 잠깐의 네트워크 문제로 1순위를 접지 않기 위한 것.
         */
        private int vworldFailStreak;

        private int kakaoFailStreak;

        Lookup(PublicDataProperties.Geocoding config) {
            this.config = config;

            this.vworldUsable =
                    config.getVworldKey() != null
                            && !config.getVworldKey().isBlank();

            this.kakaoUsable =
                    config.isKakaoFallbackEnabled()
                            && kakaoRestApiKey != null
                            && !kakaoRestApiKey.isBlank();
        }

        boolean hasAnyProvider() {
            return vworldUsable || kakaoUsable;
        }

        String describeProviders() {
            if (vworldUsable && kakaoUsable) {
                return "브이월드 → 카카오";
            }
            if (vworldUsable) {
                return "브이월드";
            }
            return "카카오";
        }

        String describeUsage() {
            return String.format(
                    "[브이월드 %d회/%d건, 카카오 %d회/%d건]",
                    vworldCalls,
                    vworldHits,
                    kakaoCalls,
                    kakaoHits
            );
        }

        /**
         * 브이월드 → 카카오 순으로 물어봅니다.
         *
         * @throws GeocodingUnavailableException 쓸 수 있는 곳이 하나도 안 남은 경우
         */
        GeocodeCache resolve(String address) {
            double[] point = null;

            if (vworldUsable) {
                point = askVworld(address);

                if (point != null) {
                    vworldHits++;
                }
            }

            if (point == null && kakaoUsable) {
                point = askKakao(address);

                if (point != null) {
                    kakaoHits++;
                }
            }

            if (point != null) {
                return new GeocodeCache(address, point[0], point[1], true);
            }

            /*
             * 좌표를 못 얻은 이유가 둘입니다.
             *   - 물어봤는데 원장에 없다  → 실패로 캐시해서 다음 달에 다시 안 묻는다
             *   - 물어볼 곳이 안 남았다   → 캐시하면 안 된다. 대기열에 남기고 멈춘다
             */
            if (!vworldUsable && !kakaoUsable) {
                throw new GeocodingUnavailableException(
                        "브이월드·카카오 모두 응답하지 않습니다."
                );
            }

            return new GeocodeCache(address, null, null, false);
        }

        /**
         * 지번·도로명 두 가지로 물어봅니다. 주소 생김새를 보고 맞을 것 같은 쪽을 먼저 씁니다.
         */
        private double[] askVworld(String address) {
            boolean roadFirst =
                    ROAD_ADDRESS_HINT.matcher(address).find();

            String[] types =
                    roadFirst
                            ? new String[]{"ROAD", "PARCEL"}
                            : new String[]{"PARCEL", "ROAD"};

            for (String type : types) {
                double[] point = vworldRequest(address, type);

                if (point != null) {
                    return point;
                }

                if (!vworldUsable) {
                    // 한도·인증 문제로 방금 접혔습니다. 두 번째 형식은 물어볼 필요가 없습니다.
                    return null;
                }
            }

            return null;
        }

        /**
         * @return {위도, 경도}, 또는 못 찾았으면 null
         */
        private double[] vworldRequest(String address, String type) {
            String body;

            try {
                URI uri =
                        UriComponentsBuilder
                                .fromUriString(config.getVworldEndpoint())
                                .queryParam("service", "address")
                                .queryParam("request", "getcoord")
                                .queryParam("version", "2.0")
                                .queryParam("crs", "epsg:4326")
                                .queryParam("type", type)
                                .queryParam("refine", "true")
                                .queryParam("simple", "false")
                                .queryParam("format", "json")
                                .queryParam("key", config.getVworldKey())
                                .queryParam("address", address)
                                .encode()
                                .build()
                                .toUri();

                vworldCalls++;

                body =
                        restTemplate.getForEntity(uri, String.class).getBody();

                vworldFailStreak = 0;

            } catch (Exception e) {
                /*
                 * 순간적인 통신 실패로 1순위를 접어 버리면 남은 주소가 전부 카카오로 몰립니다.
                 * 연달아 실패할 때만 접습니다.
                 */
                vworldFailStreak++;

                if (vworldFailStreak >= MAX_FAIL_STREAK) {
                    disableVworld("통신 실패가 이어짐: " + e.getMessage());
                } else {
                    log.debug(
                            "브이월드 통신 실패({}/{}). address={}, message={}",
                            vworldFailStreak,
                            MAX_FAIL_STREAK,
                            address,
                            e.getMessage()
                    );
                }

                return null;
            }

            try {
                JsonNode response =
                        objectMapper.readTree(body).path("response");

                String status = response.path("status").asText("");

                /*
                 * 브이월드는 인증 오류든 한도 초과든 전부 HTTP 200 으로 주고
                 * 본문 status 에만 표시합니다. 여기서 갈라내지 않으면
                 * 키가 막힌 날의 주소가 전부 '못 찾음'으로 캐시돼 영구 결손이 됩니다.
                 */
                if ("ERROR".equals(status)) {
                    disableVworld(
                            response.path("error").path("code").asText("ERROR")
                                    + " "
                                    + response.path("error").path("text").asText("")
                    );
                    return null;
                }

                if (!"OK".equals(status)) {
                    // NOT_FOUND. 그런 주소가 원장에 없다는 뜻입니다.
                    return null;
                }

                JsonNode point =
                        response.path("result").path("point");

                Double longitude =
                        CoordinateSupport.parseCoordinate(point.path("x").asText(null));

                Double latitude =
                        CoordinateSupport.parseCoordinate(point.path("y").asText(null));

                if (!CoordinateSupport.isInKorea(latitude, longitude)) {
                    return null;
                }

                return new double[]{latitude, longitude};

            } catch (Exception e) {
                log.debug(
                        "브이월드 응답을 해석하지 못했습니다. address={}, message={}",
                        address,
                        e.getMessage()
                );
                return null;
            }
        }

        private void disableVworld(String reason) {
            if (!vworldUsable) {
                return;
            }

            vworldUsable = false;

            log.warn(
                    "브이월드를 이번 실행에서 더 쓰지 않습니다. 사유: {}{}",
                    reason,
                    kakaoUsable ? " (카카오로 이어갑니다)" : ""
            );
        }

        /**
         * 지번주소는 주소검색이, 건물명이 섞인 주소는 키워드검색이 잘 맞습니다.
         */
        private double[] askKakao(String address) {
            double[] point =
                    kakaoRequest(KAKAO_ADDRESS_URL, address);

            if (point == null && kakaoUsable) {
                point = kakaoRequest(KAKAO_KEYWORD_URL, address);
            }

            return point;
        }

        private double[] kakaoRequest(String baseUrl, String address) {
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

                HttpHeaders headers = new HttpHeaders();

                headers.set("Authorization", "KakaoAK " + kakaoRestApiKey);

                kakaoCalls++;

                ResponseEntity<String> response =
                        restTemplate.exchange(
                                uri,
                                HttpMethod.GET,
                                new HttpEntity<Void>(headers),
                                String.class
                        );

                body = response.getBody();

                kakaoFailStreak = 0;

            } catch (Exception e) {
                /*
                 * 4xx·5xx·타임아웃은 전부 여기로 옵니다. 주소가 없다는 뜻이 아니므로
                 * 실패로 캐시하지 않습니다. 연달아 실패하면 카카오만 접습니다.
                 */
                kakaoFailStreak++;

                if (kakaoFailStreak >= MAX_FAIL_STREAK) {
                    kakaoUsable = false;

                    log.warn(
                            "카카오를 이번 실행에서 더 쓰지 않습니다. 사유: {}",
                            e.getMessage()
                    );
                } else {
                    log.debug(
                            "카카오 통신 실패({}/{}). address={}, message={}",
                            kakaoFailStreak,
                            MAX_FAIL_STREAK,
                            address,
                            e.getMessage()
                    );
                }

                return null;
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
    }

    /**
     * 새로 알아낸 주소를 캐시에 저장하고 목록을 비웁니다.
     */
    private void flushCache(List<GeocodeCache> newCacheEntries) {
        if (newCacheEntries.isEmpty()) {
            return;
        }

        geocodeCacheRepository.saveAll(newCacheEntries);
        newCacheEntries.clear();
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
