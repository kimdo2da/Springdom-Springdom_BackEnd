package com.example.lightsafe.geocoding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VWorldGeocodingService {

    private static final String VWORLD_ADDRESS_URL =
            "https://api.vworld.kr/req/address";

    private final JdbcTemplate jdbcTemplate;

    @Value("${vworld.api-key:}")
    private String vworldApiKey;

    /*
     * 한 번의 CCTV / 보안등 / 치안시설 전체 수집에서
     * 실제 VWorld HTTP 호출을 최대 몇 번까지 허용할지 설정합니다.
     *
     * 캐시 조회는 이 숫자에 포함되지 않습니다.
     */
    @Value("${vworld.geocoding.max-api-calls-per-sync:500}")
    private int maxApiCallsPerSync;

    /*
     * VWorld가 NOT_FOUND를 반환한 주소는
     * 이 기간 동안 다시 요청하지 않습니다.
     */
    @Value("${vworld.geocoding.failure-retry-days:30}")
    private long failureRetryDays;


    public GeocodingSession newSession(
            String dataset
    ) {
        return new GeocodingSession(
                dataset,
                Math.max(
                        0,
                        maxApiCallsPerSync
                )
        );
    }


    /*
     * 1. 도로명주소 → ROAD
     * 2. 실패 시 지번주소 → PARCEL
     */
    public GeocodingResult geocode(
            String roadAddress,
            String lotAddress,
            GeocodingSession session
    ) {

        if (session == null) {
            throw new IllegalArgumentException(
                    "GeocodingSession이 필요합니다."
            );
        }

        if (vworldApiKey == null
                || vworldApiKey.isBlank()) {

            return null;
        }

        if (roadAddress != null
                && !roadAddress.isBlank()) {

            GeocodingResult roadResult =
                    geocodeSingle(
                            roadAddress,
                            "ROAD",
                            session
                    );

            if (roadResult != null) {
                return roadResult;
            }
        }

        if (lotAddress != null
                && !lotAddress.isBlank()) {

            GeocodingResult parcelResult =
                    geocodeSingle(
                            lotAddress,
                            "PARCEL",
                            session
                    );

            if (parcelResult != null) {
                return parcelResult;
            }
        }

        return null;
    }


    private GeocodingResult geocodeSingle(
            String address,
            String addressType,
            GeocodingSession session
    ) {

        String normalizedAddress =
                normalizeAddress(
                        address
                );

        if (normalizedAddress.isBlank()) {
            return null;
        }

        String cacheKey =
                createCacheKey(
                        addressType,
                        normalizedAddress
                );

        /*
         * 같은 수집 실행 중 동일 주소가 다시 나오면
         * DB도 다시 조회하지 않습니다.
         */
        if (session.hasLocalCache(
                cacheKey
        )) {

            session.incrementCacheHits();

            Optional<GeocodingResult> localResult =
                    session.getLocalCache(
                            cacheKey
                    );

            if (localResult.isPresent()) {
                session.incrementResolvedCount();
                return localResult.get();
            }

            return null;
        }

        /*
         * 이미 이번 수집의 VWorld 요청 한도를 모두 사용했다면
         * 이후 새로운 주소는 이번 실행에서는 처리하지 않습니다.
         *
         * 다음 월간 sync에서 다시 처리됩니다.
         */
        if (session.isApiLimitReached()) {

            session.incrementLimitSkippedCount();

            return null;
        }

        CacheEntry cached =
                findCache(
                        cacheKey
                );

        if (cached != null) {

            if ("SUCCESS".equals(
                    cached.status()
            )
                    && cached.latitude() != null
                    && cached.longitude() != null
                    && isValidKoreaCoordinate(
                    cached.latitude().doubleValue(),
                    cached.longitude().doubleValue()
            )) {

                GeocodingResult result =
                        new GeocodingResult(
                                cached.latitude().doubleValue(),
                                cached.longitude().doubleValue(),
                                "VWORLD"
                        );

                session.putLocalCache(
                        cacheKey,
                        Optional.of(result)
                );

                session.incrementCacheHits();
                session.incrementResolvedCount();

                return result;
            }

            if ("NOT_FOUND".equals(
                    cached.status()
            )
                    && isRecentFailure(
                    cached.lastAttemptAt()
            )) {

                session.putLocalCache(
                        cacheKey,
                        Optional.empty()
                );

                session.incrementCacheHits();

                return null;
            }
        }

        /*
         * 실제 외부 API 호출 전에
         * session 호출량을 확보합니다.
         */
        if (!session.tryAcquireApiCall()) {

            session.incrementLimitSkippedCount();

            return null;
        }

        try {

            URI uri =
                    UriComponentsBuilder
                            .fromUriString(
                                    VWORLD_ADDRESS_URL
                            )
                            .queryParam(
                                    "service",
                                    "address"
                            )
                            .queryParam(
                                    "request",
                                    "getcoord"
                            )
                            .queryParam(
                                    "version",
                                    "2.0"
                            )
                            .queryParam(
                                    "crs",
                                    "epsg:4326"
                            )
                            .queryParam(
                                    "address",
                                    normalizedAddress
                            )
                            .queryParam(
                                    "type",
                                    addressType
                            )
                            .queryParam(
                                    "refine",
                                    true
                            )
                            .queryParam(
                                    "simple",
                                    false
                            )
                            .queryParam(
                                    "format",
                                    "json"
                            )
                            .queryParam(
                                    "key",
                                    vworldApiKey
                            )
                            .build()
                            .encode()
                            .toUri();

            RestTemplate restTemplate =
                    new RestTemplate();

            ResponseEntity<String> response =
                    restTemplate.getForEntity(
                            uri,
                            String.class
                    );

            String responseBody =
                    response.getBody();

            if (responseBody == null
                    || responseBody.isBlank()) {

                session.incrementFailureCount();

                session.putLocalCache(
                        cacheKey,
                        Optional.empty()
                );

                return null;
            }

            ObjectMapper objectMapper =
                    new ObjectMapper();

            JsonNode root =
                    objectMapper.readTree(
                            responseBody
                    );

            JsonNode responseNode =
                    root.path(
                            "response"
                    );

            String status =
                    responseNode
                            .path("status")
                            .asText("");

            /*
             * 주소 자체가 VWorld에 없는 경우에는
             * 30일 동안 같은 주소를 반복 조회하지 않도록
             * NOT_FOUND를 캐시합니다.
             */
            if ("NOT_FOUND".equalsIgnoreCase(
                    status
            )) {

                saveNotFoundCache(
                        cacheKey,
                        normalizedAddress,
                        addressType
                );

                session.incrementFailureCount();

                session.putLocalCache(
                        cacheKey,
                        Optional.empty()
                );

                return null;
            }

            /*
             * API 장애 / 인증 오류 등은
             * 영구적인 주소 실패로 판단하면 안 되므로
             * DB 실패 캐시에는 저장하지 않습니다.
             */
            if (!"OK".equalsIgnoreCase(
                    status
            )) {

                session.incrementFailureCount();

                session.putLocalCache(
                        cacheKey,
                        Optional.empty()
                );

                return null;
            }

            JsonNode point =
                    responseNode
                            .path("result")
                            .path("point");

            if (point.isMissingNode()
                    || point.isNull()) {

                session.incrementFailureCount();

                session.putLocalCache(
                        cacheKey,
                        Optional.empty()
                );

                return null;
            }

            /*
             * VWorld:
             *
             * x = longitude
             * y = latitude
             */
            double longitude =
                    parseDouble(
                            point
                                    .path("x")
                                    .asText("")
                    );

            double latitude =
                    parseDouble(
                            point
                                    .path("y")
                                    .asText("")
                    );

            if (!isValidKoreaCoordinate(
                    latitude,
                    longitude
            )) {

                session.incrementFailureCount();

                session.putLocalCache(
                        cacheKey,
                        Optional.empty()
                );

                return null;
            }

            saveSuccessCache(
                    cacheKey,
                    normalizedAddress,
                    addressType,
                    latitude,
                    longitude
            );

            GeocodingResult result =
                    new GeocodingResult(
                            latitude,
                            longitude,
                            "VWORLD"
                    );

            session.putLocalCache(
                    cacheKey,
                    Optional.of(result)
            );

            session.incrementResolvedCount();

            return result;

        } catch (Exception e) {

            /*
             * 외부 VWorld 장애 때문에
             * CCTV / 보안등 / 치안시설 전체 sync까지
             * 실패시키지 않습니다.
             */
            session.incrementFailureCount();

            session.putLocalCache(
                    cacheKey,
                    Optional.empty()
            );

            log.debug(
                    "VWorld 지오코딩 실패. dataset={}, type={}, address={}, error={}",
                    session.dataset(),
                    addressType,
                    normalizedAddress,
                    e.getMessage()
            );

            return null;
        }
    }


    private CacheEntry findCache(
            String cacheKey
    ) {

        String sql = """
                SELECT
                    status,
                    latitude,
                    longitude,
                    last_attempt_at
                FROM geocoding_cache
                WHERE cache_key = ?
                """;

        List<CacheEntry> results =
                jdbcTemplate.query(
                        sql,
                        (rs, rowNum) -> {

                            Timestamp timestamp =
                                    rs.getTimestamp(
                                            "last_attempt_at"
                                    );

                            return new CacheEntry(
                                    rs.getString(
                                            "status"
                                    ),
                                    rs.getBigDecimal(
                                            "latitude"
                                    ),
                                    rs.getBigDecimal(
                                            "longitude"
                                    ),
                                    timestamp == null
                                            ? null
                                            : timestamp.toLocalDateTime()
                            );
                        },
                        cacheKey
                );

        if (results.isEmpty()) {
            return null;
        }

        return results.get(0);
    }


    private void saveSuccessCache(
            String cacheKey,
            String address,
            String addressType,
            double latitude,
            double longitude
    ) {

        String sql = """
                INSERT INTO geocoding_cache (
                    cache_key,
                    address,
                    address_type,
                    provider,
                    status,
                    latitude,
                    longitude,
                    last_attempt_at
                )
                VALUES (?, ?, ?, 'VWORLD', 'SUCCESS', ?, ?, NOW())
                ON DUPLICATE KEY UPDATE
                    address = VALUES(address),
                    address_type = VALUES(address_type),
                    provider = 'VWORLD',
                    status = 'SUCCESS',
                    latitude = VALUES(latitude),
                    longitude = VALUES(longitude),
                    last_attempt_at = NOW()
                """;

        jdbcTemplate.update(
                sql,
                cacheKey,
                limit(
                        address,
                        255
                ),
                addressType,
                BigDecimal.valueOf(
                        latitude
                ),
                BigDecimal.valueOf(
                        longitude
                )
        );
    }


    private void saveNotFoundCache(
            String cacheKey,
            String address,
            String addressType
    ) {

        String sql = """
                INSERT INTO geocoding_cache (
                    cache_key,
                    address,
                    address_type,
                    provider,
                    status,
                    latitude,
                    longitude,
                    last_attempt_at
                )
                VALUES (?, ?, ?, 'VWORLD', 'NOT_FOUND', NULL, NULL, NOW())
                ON DUPLICATE KEY UPDATE
                    address = VALUES(address),
                    address_type = VALUES(address_type),
                    provider = 'VWORLD',
                    status = 'NOT_FOUND',
                    latitude = NULL,
                    longitude = NULL,
                    last_attempt_at = NOW()
                """;

        jdbcTemplate.update(
                sql,
                cacheKey,
                limit(
                        address,
                        255
                ),
                addressType
        );
    }


    private boolean isRecentFailure(
            LocalDateTime lastAttemptAt
    ) {

        if (lastAttemptAt == null
                || failureRetryDays <= 0) {

            return false;
        }

        return lastAttemptAt.isAfter(
                LocalDateTime
                        .now()
                        .minusDays(
                                failureRetryDays
                        )
        );
    }


    private String createCacheKey(
            String addressType,
            String address
    ) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            (
                                    addressType
                                            + "|"
                                            + address
                            )
                                    .getBytes(
                                            StandardCharsets.UTF_8
                                    )
                    );

            return HexFormat
                    .of()
                    .formatHex(
                            hash
                    );

        } catch (Exception e) {

            throw new IllegalStateException(
                    "지오코딩 cache_key 생성 실패",
                    e
            );
        }
    }


    private String normalizeAddress(
            String address
    ) {

        if (address == null) {
            return "";
        }

        return address
                .trim()
                .replaceAll(
                        "\\s+",
                        " "
                );
    }


    private double parseDouble(
            String value
    ) {

        if (value == null
                || value.isBlank()) {

            return 0.0;
        }

        try {

            return Double.parseDouble(
                    value.trim()
            );

        } catch (NumberFormatException e) {

            return 0.0;
        }
    }


    private boolean isValidKoreaCoordinate(
            double latitude,
            double longitude
    ) {

        return latitude >= 33.0
                && latitude <= 39.0
                && longitude >= 124.0
                && longitude <= 132.0;
    }


    private String limit(
            String value,
            int maxLength
    ) {

        if (value == null) {
            return null;
        }

        if (value.length() <= maxLength) {
            return value;
        }

        return value.substring(
                0,
                maxLength
        );
    }


    public record GeocodingResult(
            double latitude,
            double longitude,
            String source
    ) {
    }


    private record CacheEntry(
            String status,
            BigDecimal latitude,
            BigDecimal longitude,
            LocalDateTime lastAttemptAt
    ) {
    }


    public static class GeocodingSession {

        private final String dataset;

        private final int maxApiCalls;

        private int apiCalls;

        private int cacheHits;

        private int resolvedCount;

        private int failureCount;

        private int limitSkippedCount;

        private final Map<
                String,
                Optional<GeocodingResult>
                > localCache =
                new HashMap<>();


        private GeocodingSession(
                String dataset,
                int maxApiCalls
        ) {
            this.dataset =
                    dataset;

            this.maxApiCalls =
                    maxApiCalls;
        }


        private boolean tryAcquireApiCall() {

            if (apiCalls >= maxApiCalls) {
                return false;
            }

            apiCalls++;

            return true;
        }


        private boolean isApiLimitReached() {

            return apiCalls >= maxApiCalls;
        }


        private boolean hasLocalCache(
                String key
        ) {

            return localCache.containsKey(
                    key
            );
        }


        private Optional<GeocodingResult> getLocalCache(
                String key
        ) {

            return localCache.get(
                    key
            );
        }


        private void putLocalCache(
                String key,
                Optional<GeocodingResult> value
        ) {

            localCache.put(
                    key,
                    value
            );
        }


        private void incrementCacheHits() {
            cacheHits++;
        }


        private void incrementResolvedCount() {
            resolvedCount++;
        }


        private void incrementFailureCount() {
            failureCount++;
        }


        private void incrementLimitSkippedCount() {
            limitSkippedCount++;
        }


        public String dataset() {
            return dataset;
        }


        public int apiCalls() {
            return apiCalls;
        }


        public int cacheHits() {
            return cacheHits;
        }


        public int resolvedCount() {
            return resolvedCount;
        }


        public int failureCount() {
            return failureCount;
        }


        public int limitSkippedCount() {
            return limitSkippedCount;
        }
    }
}