package com.example.lightsafe.geocoding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
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
     *
     * 3번 이슈에서 별도로 조정할 예정이므로
     * 이번 수정에서는 기존 500을 유지합니다.
     */
    @Value("${vworld.geocoding.max-api-calls-per-sync:500}")
    private int maxApiCallsPerSync;

    /*
     * VWorld가 NOT_FOUND를 반환한 주소는
     * 이 기간 동안 다시 요청하지 않습니다.
     */
    @Value("${vworld.geocoding.failure-retry-days:30}")
    private long failureRetryDays;

    /*
     * VWorld 서버와 TCP 연결을 맺을 때
     * 최대 대기 시간입니다.
     *
     * 기본값: 5초
     */
    @Value("${vworld.geocoding.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    /*
     * VWorld와 연결된 후
     * 응답 데이터를 기다리는 최대 시간입니다.
     *
     * 기본값: 5초
     */
    @Value("${vworld.geocoding.read-timeout-ms:5000}")
    private int readTimeoutMs;

    /*
     * timeout / HTTP 오류 / VWorld 서비스 오류 등이
     * 연속으로 몇 번 발생하면
     * 해당 Sync에서 VWorld 외부 호출을 중단할지 설정합니다.
     *
     * NOT_FOUND는 서버 장애가 아니므로
     * 이 횟수에 포함하지 않습니다.
     */
    @Value("${vworld.geocoding.consecutive-external-failure-threshold:3}")
    private int consecutiveExternalFailureThreshold;


    public GeocodingSession newSession(
            String dataset
    ) {

        return new GeocodingSession(
                dataset,
                Math.max(
                        0,
                        maxApiCallsPerSync
                ),
                Math.max(
                        1,
                        consecutiveExternalFailureThreshold
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
         * 기존 구조를 그대로 유지합니다.
         *
         * 이 위치는 프론트 수정요청서의
         * 2번 이슈와 관련되어 있지만
         * 이번에는 1번만 수정하므로 건드리지 않습니다.
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
         * VWorld 외부 서버 장애가 연속으로 발생해
         * circuit이 열린 상태라면
         * 더 이상 실제 VWorld HTTP 요청을 보내지 않습니다.
         *
         * DB 캐시 조회는 위에서 이미 끝났으므로
         * 캐시된 결과는 계속 사용할 수 있습니다.
         */
        if (session.isExternalCircuitOpen()) {
            return null;
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
                    createVWorldRestTemplate();

            ResponseEntity<String> response =
                    restTemplate.getForEntity(
                            uri,
                            String.class
                    );

            String responseBody =
                    response.getBody();

            /*
             * HTTP 연결 자체는 됐지만
             * 응답 body가 비어 있다면
             * 정상적인 VWorld 응답으로 볼 수 없습니다.
             */
            if (responseBody == null
                    || responseBody.isBlank()) {

                registerExternalFailure(
                        session,
                        cacheKey,
                        addressType,
                        normalizedAddress,
                        "응답 body가 비어 있음"
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
             * 주소 자체가 VWorld에 존재하지 않는 경우입니다.
             *
             * NOT_FOUND는 VWorld 서버 장애가 아니라
             * 정상적인 API 응답입니다.
             *
             * 따라서 연속 외부 장애 횟수는 초기화합니다.
             */
            if ("NOT_FOUND".equalsIgnoreCase(
                    status
            )) {

                session.resetConsecutiveExternalFailures();

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
             * OK / NOT_FOUND 외의 상태는
             * 인증 문제, 서비스 오류 등일 가능성이 있으므로
             * 외부 API 장애로 처리합니다.
             *
             * 이런 장애가 연속으로 일정 횟수 발생하면
             * 해당 Sync에서는 VWorld 호출을 중단합니다.
             */
            if (!"OK".equalsIgnoreCase(
                    status
            )) {

                registerExternalFailure(
                        session,
                        cacheKey,
                        addressType,
                        normalizedAddress,
                        "VWorld status=" + status
                );

                return null;
            }

            /*
             * 여기까지 왔다는 것은
             * VWorld가 정상적으로 OK 응답을 반환했다는 뜻입니다.
             *
             * 이전에 timeout 등의 장애가 있었더라도
             * 연속 장애 횟수를 초기화합니다.
             */
            session.resetConsecutiveExternalFailures();

            JsonNode point =
                    responseNode
                            .path("result")
                            .path("point");

            /*
             * VWorld 응답 자체는 OK였으므로
             * point가 없다고 해서 서버 장애 circuit을 올리지는 않습니다.
             *
             * 일반 지오코딩 실패로만 기록합니다.
             */
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

            /*
             * API는 OK였지만 반환 좌표가
             * 대한민국 범위를 벗어난 경우입니다.
             *
             * 이것 역시 VWorld 서버 장애라기보다
             * 해당 주소 결과 문제로 보기 때문에
             * circuit 장애 횟수에는 포함하지 않습니다.
             */
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

        } catch (JsonProcessingException e) {

            /*
             * HTTP 응답은 왔지만
             * VWorld 응답 JSON 자체를 읽을 수 없는 경우입니다.
             *
             * 정상적인 VWorld 응답이 아니므로
             * 외부 장애로 계산합니다.
             */
            registerExternalFailure(
                    session,
                    cacheKey,
                    addressType,
                    normalizedAddress,
                    "응답 JSON 파싱 실패"
            );

            return null;

        } catch (RestClientException e) {

            /*
             * connect timeout
             * read timeout
             * HTTP 4xx / 5xx
             * 네트워크 연결 오류
             *
             * 모두 외부 VWorld 호출 장애로 처리합니다.
             */
            registerExternalFailure(
                    session,
                    cacheKey,
                    addressType,
                    normalizedAddress,
                    e.getMessage()
            );

            return null;

        } catch (Exception e) {

            /*
             * DB 캐시 저장 오류 등
             * VWorld 서버 자체의 장애라고 확정하기 어려운 예외입니다.
             *
             * 전체 Sync는 계속 진행시키되
             * 외부 장애 circuit 횟수에는 포함하지 않습니다.
             */
            session.incrementFailureCount();

            session.putLocalCache(
                    cacheKey,
                    Optional.empty()
            );

            log.debug(
                    "VWorld 지오코딩 처리 실패. dataset={}, type={}, address={}, error={}",
                    session.dataset(),
                    addressType,
                    normalizedAddress,
                    e.getMessage()
            );

            return null;
        }
    }


    /*
     * VWorld HTTP 전용 RestTemplate을 생성합니다.
     *
     * connect timeout:
     * 서버와 연결 자체를 맺는 최대 시간
     *
     * read timeout:
     * 연결 이후 응답 데이터를 기다리는 최대 시간
     */
    private RestTemplate createVWorldRestTemplate() {

        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();

        requestFactory.setConnectTimeout(
                Math.max(
                        1,
                        connectTimeoutMs
                )
        );

        requestFactory.setReadTimeout(
                Math.max(
                        1,
                        readTimeoutMs
                )
        );

        return new RestTemplate(
                requestFactory
        );
    }


    /*
     * timeout / 네트워크 오류 / HTTP 오류 /
     * 잘못된 VWorld 응답 등
     * 외부 서비스 장애를 기록합니다.
     */
    private void registerExternalFailure(
            GeocodingSession session,
            String cacheKey,
            String addressType,
            String normalizedAddress,
            String reason
    ) {

        session.incrementFailureCount();

        session.putLocalCache(
                cacheKey,
                Optional.empty()
        );

        boolean circuitOpened =
                session.recordExternalFailure();

        if (circuitOpened) {

            log.warn(
                    "VWorld 연속 외부 장애 {}회로 이번 sync의 추가 VWorld 호출을 중단합니다. dataset={}, lastType={}, lastAddress={}, reason={}",
                    session.consecutiveExternalFailureCount(),
                    session.dataset(),
                    addressType,
                    normalizedAddress,
                    reason
            );

            return;
        }

        log.debug(
                "VWorld 외부 호출 실패. dataset={}, consecutiveFailure={}, type={}, address={}, reason={}",
                session.dataset(),
                session.consecutiveExternalFailureCount(),
                addressType,
                normalizedAddress,
                reason
        );
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

        /*
         * 외부 VWorld 장애가 몇 번 연속 발생하면
         * 해당 Sync에서 외부 호출을 중단할지 나타냅니다.
         */
        private final int maxConsecutiveExternalFailures;

        private int apiCalls;

        private int cacheHits;

        private int resolvedCount;

        private int failureCount;

        private int limitSkippedCount;

        /*
         * timeout / HTTP 오류 / 서비스 오류 등
         * 외부 VWorld 장애가 연속으로 발생한 횟수입니다.
         *
         * 정상 OK 또는 NOT_FOUND 응답을 받으면
         * 다시 0으로 초기화됩니다.
         */
        private int consecutiveExternalFailureCount;

        /*
         * true가 되면 해당 Sync에서는
         * 더 이상 새로운 VWorld HTTP 요청을 보내지 않습니다.
         *
         * 단, DB / 메모리 캐시는 계속 사용할 수 있습니다.
         */
        private boolean externalCircuitOpen;

        private final Map<
                String,
                Optional<GeocodingResult>
                > localCache =
                new HashMap<>();


        private GeocodingSession(
                String dataset,
                int maxApiCalls,
                int maxConsecutiveExternalFailures
        ) {

            this.dataset =
                    dataset;

            this.maxApiCalls =
                    maxApiCalls;

            this.maxConsecutiveExternalFailures =
                    maxConsecutiveExternalFailures;
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


        private boolean isExternalCircuitOpen() {

            return externalCircuitOpen;
        }


        /*
         * VWorld 외부 장애가 발생했을 때 호출합니다.
         *
         * 반환값 true:
         * 이번 장애로 circuit이 새롭게 열린 경우
         */
        private boolean recordExternalFailure() {

            consecutiveExternalFailureCount++;

            if (!externalCircuitOpen
                    && consecutiveExternalFailureCount
                    >= maxConsecutiveExternalFailures) {

                externalCircuitOpen = true;

                return true;
            }

            return false;
        }


        /*
         * VWorld 서버에서 정상적인 응답
         * OK 또는 NOT_FOUND를 받으면
         * 연속 장애 횟수를 초기화합니다.
         */
        private void resetConsecutiveExternalFailures() {

            consecutiveExternalFailureCount = 0;
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


        public int consecutiveExternalFailureCount() {

            return consecutiveExternalFailureCount;
        }


        public boolean externalCircuitOpen() {

            return externalCircuitOpen;
        }
    }
}