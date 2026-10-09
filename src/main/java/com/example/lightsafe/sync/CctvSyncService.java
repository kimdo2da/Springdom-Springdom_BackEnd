package com.example.lightsafe.sync;

import com.example.lightsafe.common.exception.BadRequestException;
import com.example.lightsafe.emergency.Cctv;
import com.example.lightsafe.geocoding.VWorldGeocodingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CctvSyncService {

    private static final String DATASET =
            "CCTV";

    private static final String PUBLIC_DATA_CCTV_URL =
            "https://apis.data.go.kr/1741000/cctv_info/info";

    /*
     * 요청값은 1000으로 보내지만
     * 실제 CCTV API는 약 100건씩 반환할 수 있습니다.
     *
     * 따라서 종료 여부는 pageNo * numOfRows가 아니라
     * 실제 fetchedCount와 totalCount를 기준으로 판단합니다.
     */
    private static final int NUM_OF_ROWS =
            1000;

    /*
     * CCTV API가 일시적으로 비정상 응답을 반환하면
     * 동일 페이지를 최대 3회까지 재시도합니다.
     */
    private static final int API_RETRY_COUNT =
            3;

    private static final long API_RETRY_DELAY_MS =
            1500L;

    private static final int DB_BATCH_SIZE =
            1000;

    /*
     * mng_no 기준 UPSERT
     *
     * 기존 데이터 → UPDATE
     * 신규 데이터 → INSERT
     *
     * 기존 cctv_id는 유지됩니다.
     */
    private static final String UPSERT_SQL = """
            INSERT INTO cctvs (
                cctv_name,
                latitude,
                longitude,
                address,
                purpose,
                mng_no,
                camera_count,
                institution,
                reference_date
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                cctv_name = VALUES(cctv_name),
                latitude = VALUES(latitude),
                longitude = VALUES(longitude),
                address = VALUES(address),
                purpose = VALUES(purpose),
                camera_count = VALUES(camera_count),
                institution = VALUES(institution),
                reference_date = VALUES(reference_date)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final DataSyncLogRepository dataSyncLogRepository;
    private final VWorldGeocodingService vWorldGeocodingService;

    @Value("${public-data.service-key:}")
    private String publicDataServiceKey;


    public SyncResultResponse syncCctvs() {

        if (publicDataServiceKey == null
                || publicDataServiceKey.isBlank()) {

            throw new BadRequestException(
                    "public-data.service-key가 설정되어 있지 않습니다."
            );
        }

        DataSyncLog syncLog =
                startLog();

        int fetchedCount = 0;
        int savedCount = 0;

        VWorldGeocodingService.GeocodingSession geocodingSession =
                vWorldGeocodingService.newSession(
                        DATASET
                );

        try {

            RestTemplate restTemplate =
                    new RestTemplate();

            ObjectMapper objectMapper =
                    new ObjectMapper();

            int pageNo = 1;

            int totalCount =
                    Integer.MAX_VALUE;

            while (fetchedCount < totalCount) {

                CctvPage page =
                        fetchPageWithRetry(
                                pageNo,
                                totalCount,
                                restTemplate,
                                objectMapper
                        );

                totalCount =
                        page.totalCount();

                List<JsonNode> items =
                        page.items();

                fetchedCount +=
                        items.size();

                List<Cctv> cctvs =
                        new ArrayList<>(
                                items.size()
                        );

                for (JsonNode item : items) {

                    Cctv cctv =
                            convertToCctv(
                                    item,
                                    geocodingSession
                            );

                    if (cctv == null) {
                        continue;
                    }

                    cctvs.add(
                            cctv
                    );
                }

                if (!cctvs.isEmpty()) {

                    batchUpsertCctvs(
                            cctvs
                    );
                }

                savedCount +=
                        cctvs.size();

                log.info(
                        "CCTV 수집 진행. pageNo={}, fetchedCount={}, savedCount={}, totalCount={}",
                        pageNo,
                        fetchedCount,
                        savedCount,
                        totalCount
                );

                if (items.isEmpty()) {
                    break;
                }

                if (fetchedCount >= totalCount) {
                    break;
                }

                pageNo++;
            }

            String message =
                    "CCTV 수집 완료"
                            + " (지오코딩 적용="
                            + geocodingSession.resolvedCount()
                            + ", VWorld API 호출="
                            + geocodingSession.apiCalls()
                            + ", 캐시 사용="
                            + geocodingSession.cacheHits()
                            + ", 지오코딩 실패 시도="
                            + geocodingSession.failureCount()
                            + ", 호출 제한 스킵="
                            + geocodingSession.limitSkippedCount()
                            + ")";

            finishLog(
                    syncLog,
                    "SUCCESS",
                    fetchedCount,
                    savedCount,
                    message
            );

            return new SyncResultResponse(
                    DATASET,
                    "SUCCESS",
                    fetchedCount,
                    savedCount,
                    message
            );

        } catch (Exception e) {

            log.warn(
                    "CCTV 수집 중 오류가 발생했습니다.",
                    e
            );

            finishLog(
                    syncLog,
                    "FAILED",
                    fetchedCount,
                    savedCount,
                    e.getMessage()
            );

            throw new BadRequestException(
                    "CCTV 수집 실패: "
                            + e.getMessage()
            );
        }
    }


    private CctvPage fetchPageWithRetry(
            int pageNo,
            int currentTotalCount,
            RestTemplate restTemplate,
            ObjectMapper objectMapper
    ) {

        Exception lastException =
                null;

        for (
                int attempt = 1;
                attempt <= API_RETRY_COUNT;
                attempt++
        ) {

            try {

                return fetchPage(
                        pageNo,
                        currentTotalCount,
                        restTemplate,
                        objectMapper
                );

            } catch (Exception e) {

                lastException =
                        e;

                if (attempt >= API_RETRY_COUNT) {
                    break;
                }

                log.warn(
                        "CCTV API 요청 실패. pageNo={}, attempt={}/{}, error={}. 재시도합니다.",
                        pageNo,
                        attempt,
                        API_RETRY_COUNT,
                        e.getMessage()
                );

                try {

                    Thread.sleep(
                            API_RETRY_DELAY_MS
                                    * attempt
                    );

                } catch (InterruptedException interruptedException) {

                    Thread.currentThread()
                            .interrupt();

                    throw new BadRequestException(
                            "CCTV API 재시도 대기 중 작업이 중단되었습니다."
                    );
                }
            }
        }

        String lastError =
                lastException == null
                        ? "알 수 없는 오류"
                        : lastException.getMessage();

        throw new BadRequestException(
                "CCTV API pageNo="
                        + pageNo
                        + " 요청이 "
                        + API_RETRY_COUNT
                        + "회 모두 실패했습니다. 마지막 오류: "
                        + lastError
        );
    }


    private CctvPage fetchPage(
            int pageNo,
            int currentTotalCount,
            RestTemplate restTemplate,
            ObjectMapper objectMapper
    ) throws Exception {

        String url =
                PUBLIC_DATA_CCTV_URL
                        + "?serviceKey="
                        + publicDataServiceKey
                        + "&pageNo="
                        + pageNo
                        + "&numOfRows="
                        + NUM_OF_ROWS
                        + "&type=json";

        ResponseEntity<String> response =
                restTemplate.getForEntity(
                        URI.create(
                                url
                        ),
                        String.class
                );

        String responseBody =
                response.getBody();

        if (responseBody == null
                || responseBody.isBlank()) {

            throw new BadRequestException(
                    "CCTV API 응답이 비어 있습니다."
            );
        }

        if (!responseBody
                .trim()
                .startsWith("{")) {

            throw new BadRequestException(
                    "CCTV API가 JSON이 아닌 응답을 반환했습니다. type=json 또는 인증키를 확인해주세요."
            );
        }

        JsonNode root =
                objectMapper.readTree(
                        responseBody
                );

        JsonNode responseNode =
                root.path(
                        "response"
                );

        JsonNode headerNode =
                responseNode.path(
                        "header"
                );

        String resultCode =
                headerNode
                        .path(
                                "resultCode"
                        )
                        .asText("");

        String resultMessage =
                headerNode
                        .path(
                                "resultMsg"
                        )
                        .asText("");

        if (!resultCode.isBlank()
                && !"0".equals(
                resultCode
        )
                && !"00".equals(
                resultCode
        )) {

            throw new BadRequestException(
                    "CCTV API 오류: "
                            + resultCode
                            + " / "
                            + resultMessage
            );
        }

        JsonNode bodyNode =
                responseNode.path(
                        "body"
                );

        if (bodyNode.isMissingNode()
                || bodyNode.isNull()) {

            bodyNode =
                    root.path(
                            "body"
                    );
        }

        if (bodyNode.isMissingNode()
                || bodyNode.isNull()
                || !bodyNode.isObject()) {

            throw new BadRequestException(
                    "CCTV API 응답에 body가 없습니다."
            );
        }

        int totalCount =
                getIntAny(
                        bodyNode,
                        currentTotalCount,
                        "totalCount",
                        "totalCnt"
                );

        JsonNode itemNode =
                bodyNode
                        .path(
                                "items"
                        )
                        .path(
                                "item"
                        );

        if (itemNode.isMissingNode()) {

            itemNode =
                    bodyNode.path(
                            "items"
                    );
        }

        List<JsonNode> items =
                toItemList(
                        itemNode
                );

        return new CctvPage(
                totalCount,
                items
        );
    }


    private record CctvPage(
            int totalCount,
            List<JsonNode> items
    ) {
    }


    private void batchUpsertCctvs(
            List<Cctv> cctvs
    ) {

        jdbcTemplate.batchUpdate(
                UPSERT_SQL,
                cctvs,
                DB_BATCH_SIZE,
                (preparedStatement, cctv) -> {

                    preparedStatement.setString(
                            1,
                            cctv.getCctvName()
                    );

                    preparedStatement.setBigDecimal(
                            2,
                            cctv.getLatitude()
                    );

                    preparedStatement.setBigDecimal(
                            3,
                            cctv.getLongitude()
                    );

                    preparedStatement.setString(
                            4,
                            cctv.getAddress()
                    );

                    preparedStatement.setString(
                            5,
                            cctv.getPurpose()
                    );

                    preparedStatement.setString(
                            6,
                            cctv.getMngNo()
                    );

                    Integer cameraCount =
                            cctv.getCameraCount();

                    preparedStatement.setInt(
                            7,
                            cameraCount == null
                                    ? 1
                                    : cameraCount
                    );

                    preparedStatement.setString(
                            8,
                            cctv.getInstitution()
                    );

                    if (cctv.getReferenceDate() == null) {

                        preparedStatement.setNull(
                                9,
                                Types.DATE
                        );

                    } else {

                        preparedStatement.setDate(
                                9,
                                java.sql.Date.valueOf(
                                        cctv.getReferenceDate()
                                )
                        );
                    }
                }
        );
    }


    private Cctv convertToCctv(
            JsonNode item,
            VWorldGeocodingService.GeocodingSession geocodingSession
    ) {

        String mngNo =
                limit(
                        asTextAny(
                                item,
                                "MNG_NO",
                                "mng_no",
                                "mngNo",
                                "manageNo",
                                "managementNo"
                        ),
                        40
                );

        /*
         * mng_no가 없으면
         * UPSERT 기준이 없으므로 제외합니다.
         */
        if (mngNo == null
                || mngNo.isBlank()) {

            return null;
        }

        double latitude =
                parseDouble(
                        asTextAny(
                                item,
                                "WGS84_LAT",
                                "wgs84Lat",
                                "latitude",
                                "LAT",
                                "lat"
                        )
                );

        double longitude =
                parseDouble(
                        asTextAny(
                                item,
                                "WGS84_LOT",
                                "WGS84_LON",
                                "wgs84Lot",
                                "wgs84Lon",
                                "longitude",
                                "LON",
                                "lng",
                                "lot"
                        )
                );

        /*
         * 좌표 확인보다 주소를 먼저 확보합니다.
         *
         * 원본 좌표가 잘못된 경우
         * 이 주소들을 이용해 VWorld fallback을 수행합니다.
         */
        String roadAddress =
                asTextAny(
                        item,
                        "LCTN_ROAD_NM_ADDR",
                        "roadNmAdres",
                        "roadAddress",
                        "rdnmadr"
                );

        String lotAddress =
                asTextAny(
                        item,
                        "LCTN_LOTNO_ADDR",
                        "lnmAdres",
                        "lotAddress",
                        "lnmadr"
                );

        /*
         * 정상 좌표면 그대로 사용.
         *
         * 좌표가 없거나 한국 범위를 벗어나면
         * VWorld 주소 지오코딩으로 보정합니다.
         */
        if (!isValidKoreaCoordinate(
                latitude,
                longitude
        )) {

            VWorldGeocodingService.GeocodingResult geocodingResult =
                    vWorldGeocodingService.geocode(
                            roadAddress,
                            lotAddress,
                            geocodingSession
                    );

            if (geocodingResult == null) {
                return null;
            }

            latitude =
                    geocodingResult.latitude();

            longitude =
                    geocodingResult.longitude();
        }

        Cctv cctv =
                new Cctv();

        String address =
                roadAddress.isBlank()
                        ? lotAddress
                        : roadAddress;

        String purpose =
                asTextAny(
                        item,
                        "INSTL_PRPS_SE_NM",
                        "INSTL_PURPS_NM",
                        "instlPrpsSeNm",
                        "instlPurpose",
                        "oprationGoal",
                        "purpose"
                );

        String institution =
                asTextAny(
                        item,
                        "MNG_INST_NM",
                        "mngInstNm",
                        "institutionNm",
                        "institution"
                );

        String cameraCountText =
                asTextAny(
                        item,
                        "CAM_CNTOM",
                        "CAM_CNT",
                        "CAMERA_CNT",
                        "cameraCount",
                        "cctvCo"
                );

        String referenceDateText =
                asTextAny(
                        item,
                        "DATA_STD_DE",
                        "dataStdDe",
                        "REFERENCE_DATE",
                        "referenceDate"
                );

        cctv.setMngNo(
                mngNo
        );

        cctv.setCctvName(
                limit(
                        makeCctvName(
                                institution,
                                purpose,
                                mngNo
                        ),
                        100
                )
        );

        cctv.setLatitude(
                toDecimal(
                        latitude
                )
        );

        cctv.setLongitude(
                toDecimal(
                        longitude
                )
        );

        cctv.setAddress(
                limit(
                        address,
                        255
                )
        );

        cctv.setPurpose(
                limit(
                        purpose,
                        50
                )
        );

        cctv.setCameraCount(
                parsePositiveInt(
                        cameraCountText
                )
        );

        cctv.setInstitution(
                limit(
                        institution,
                        120
                )
        );

        cctv.setReferenceDate(
                parseDate(
                        referenceDateText
                )
        );

        return cctv;
    }


    private String makeCctvName(
            String institution,
            String purpose,
            String mngNo
    ) {

        if (institution != null
                && !institution.isBlank()
                && purpose != null
                && !purpose.isBlank()) {

            return institution
                    + " "
                    + purpose;
        }

        if (institution != null
                && !institution.isBlank()) {

            return institution
                    + " CCTV";
        }

        return "CCTV-"
                + mngNo;
    }


    private List<JsonNode> toItemList(
            JsonNode itemNode
    ) {

        List<JsonNode> items =
                new ArrayList<>();

        if (itemNode == null
                || itemNode.isMissingNode()
                || itemNode.isNull()) {

            return items;
        }

        if (itemNode.isArray()) {

            itemNode.forEach(
                    items::add
            );

            return items;
        }

        if (itemNode.isObject()) {

            items.add(
                    itemNode
            );
        }

        return items;
    }


    private String asTextAny(
            JsonNode node,
            String... fieldNames
    ) {

        if (node == null
                || fieldNames == null) {

            return "";
        }

        for (String fieldName : fieldNames) {

            JsonNode value =
                    node.path(
                            fieldName
                    );

            if (!value.isMissingNode()
                    && !value.isNull()) {

                String text =
                        value
                                .asText("")
                                .trim();

                if (!text.isBlank()) {
                    return text;
                }
            }
        }

        return "";
    }


    private int getIntAny(
            JsonNode node,
            int defaultValue,
            String... fieldNames
    ) {

        for (String fieldName : fieldNames) {

            JsonNode value =
                    node.path(
                            fieldName
                    );

            if (value.isNumber()) {

                return value.asInt(
                        defaultValue
                );
            }

            String text =
                    value
                            .asText("")
                            .trim();

            if (!text.isBlank()) {

                try {

                    return Integer.parseInt(
                            text
                    );

                } catch (NumberFormatException ignored) {
                }
            }
        }

        return defaultValue;
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


    private int parsePositiveInt(
            String value
    ) {

        if (value == null
                || value.isBlank()) {

            return 1;
        }

        try {

            int number =
                    Integer.parseInt(
                            value.trim()
                    );

            return number <= 0
                    ? 1
                    : number;

        } catch (NumberFormatException e) {

            return 1;
        }
    }


    private LocalDate parseDate(
            String value
    ) {

        if (value == null
                || value.isBlank()) {

            return null;
        }

        String text =
                value
                        .trim()
                        .replace(
                                ".",
                                "-"
                        )
                        .replace(
                                "/",
                                "-"
                        );

        try {

            return LocalDate.parse(
                    text
            );

        } catch (Exception ignored) {
        }

        try {

            return LocalDate.parse(
                    text,
                    DateTimeFormatter.BASIC_ISO_DATE
            );

        } catch (Exception ignored) {
        }

        return null;
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


    private BigDecimal toDecimal(
            double value
    ) {

        return BigDecimal
                .valueOf(
                        value
                )
                .setScale(
                        7,
                        RoundingMode.HALF_UP
                );
    }


    private String limit(
            String value,
            int maxLength
    ) {

        if (value == null) {
            return null;
        }

        String text =
                value.trim();

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(
                0,
                maxLength
        );
    }


    private DataSyncLog startLog() {

        DataSyncLog syncLog =
                new DataSyncLog();

        syncLog.setDataset(
                DATASET
        );

        syncLog.setStartedAt(
                LocalDateTime.now()
        );

        syncLog.setStatus(
                "RUNNING"
        );

        return dataSyncLogRepository.save(
                syncLog
        );
    }


    private void finishLog(
            DataSyncLog syncLog,
            String status,
            int fetchedCount,
            int savedCount,
            String message
    ) {

        syncLog.setFinishedAt(
                LocalDateTime.now()
        );

        syncLog.setStatus(
                status
        );

        syncLog.setFetchedCount(
                fetchedCount
        );

        syncLog.setSavedCount(
                savedCount
        );

        if (message != null
                && message.length() > 500) {

            syncLog.setMessage(
                    message.substring(
                            0,
                            500
                    )
            );

        } else {

            syncLog.setMessage(
                    message
            );
        }

        dataSyncLogRepository.save(
                syncLog
        );
    }
}