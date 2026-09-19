package com.example.lightsafe.sync;

import com.example.lightsafe.common.exception.BadRequestException;
import com.example.lightsafe.emergency.Cctv;
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
     * 실제 CCTV API는 현재 약 100건씩 반환하고 있습니다.
     *
     * 따라서 종료 여부는 numOfRows가 아니라
     * 실제 fetchedCount와 totalCount를 기준으로 판단합니다.
     */
    private static final int NUM_OF_ROWS =
            1000;

    /*
     * 공공데이터 API가 일시적으로
     * body가 없는 응답 등을 반환하는 경우
     * 같은 페이지를 최대 3회까지 다시 요청합니다.
     */
    private static final int API_RETRY_COUNT =
            3;

    private static final long API_RETRY_DELAY_MS =
            1500L;

    /*
     * JdbcTemplate batchUpdate에서 사용하는
     * 최대 배치 크기입니다.
     *
     * 현재 CCTV API가 한 페이지당 약 100건을 반환하므로
     * 실질적으로는 페이지 단위로 한 번씩 처리됩니다.
     */
    private static final int DB_BATCH_SIZE =
            1000;

    /*
     * mng_no가 이미 존재하면 UPDATE,
     * 존재하지 않으면 INSERT 합니다.
     *
     * 기존 cctv_id는 유지되므로
     * emergency_reports.nearest_cctv_id 같은
     * 기존 FK 관계도 유지됩니다.
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

        try {

            RestTemplate restTemplate =
                    new RestTemplate();

            ObjectMapper objectMapper =
                    new ObjectMapper();

            int pageNo = 1;

            /*
             * 첫 페이지를 받아오기 전에는
             * 전체 건수를 모르므로 MAX_VALUE로 시작합니다.
             */
            int totalCount =
                    Integer.MAX_VALUE;

            while (fetchedCount < totalCount) {

                /*
                 * 한 페이지를 가져옵니다.
                 *
                 * API에서 일시적으로 이상 응답이 오면
                 * fetchPageWithRetry() 내부에서 최대 3회 재시도합니다.
                 */
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

                /*
                 * fetchedCount는
                 * API에서 실제로 전달받은 원본 데이터 개수입니다.
                 */
                fetchedCount +=
                        items.size();

                List<Cctv> cctvs =
                        new ArrayList<>(
                                items.size()
                        );

                /*
                 * API 데이터 파싱 및 검증
                 *
                 * 기존에 사용하던 필터링 규칙은 그대로 유지합니다.
                 */
                for (JsonNode item : items) {

                    Cctv cctv =
                            convertToCctv(item);

                    if (cctv == null) {
                        continue;
                    }

                    cctvs.add(cctv);
                }

                /*
                 * 기존:
                 *
                 * 각 CCTV마다
                 * findByMngNo() SELECT
                 * +
                 * JPA INSERT/UPDATE
                 *
                 * 수정:
                 *
                 * 한 페이지의 CCTV를
                 * JdbcTemplate batch UPSERT로 처리
                 */
                if (!cctvs.isEmpty()) {

                    batchUpsertCctvs(
                            cctvs
                    );
                }

                /*
                 * savedCount는
                 * 신규 INSERT 건수만 의미하는 것이 아니라
                 * 유효한 데이터로 판단되어
                 * UPSERT 처리된 건수를 의미합니다.
                 */
                savedCount +=
                        cctvs.size();

                log.info(
                        "CCTV 수집 진행. pageNo={}, fetchedCount={}, savedCount={}, totalCount={}",
                        pageNo,
                        fetchedCount,
                        savedCount,
                        totalCount
                );

                /*
                 * API가 빈 페이지를 반환하면
                 * 더 이상 진행하지 않습니다.
                 */
                if (items.isEmpty()) {
                    break;
                }

                /*
                 * 실제 받은 데이터 수가
                 * 전체 건수에 도달하면 종료합니다.
                 *
                 * API가 요청한 numOfRows=1000과 달리
                 * 실제로는 약 100건만 반환하기 때문에
                 * pageNo * NUM_OF_ROWS 방식으로 계산하면 안 됩니다.
                 */
                if (fetchedCount >= totalCount) {
                    break;
                }

                pageNo++;
            }

            finishLog(
                    syncLog,
                    "SUCCESS",
                    fetchedCount,
                    savedCount,
                    "CCTV 수집 완료"
            );

            return new SyncResultResponse(
                    DATASET,
                    "SUCCESS",
                    fetchedCount,
                    savedCount,
                    "CCTV 수집 완료"
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

    /*
     * 같은 API 페이지를 최대 3회까지 재시도합니다.
     *
     * 예:
     *
     * pageNo=3065 1차 실패
     * → 1.5초 대기
     *
     * pageNo=3065 2차 실패
     * → 3초 대기
     *
     * pageNo=3065 3차 실패
     * → 전체 수집 실패 처리
     */
    private CctvPage fetchPageWithRetry(
            int pageNo,
            int currentTotalCount,
            RestTemplate restTemplate,
            ObjectMapper objectMapper
    ) {

        Exception lastException =
                null;

        for (int attempt = 1;
             attempt <= API_RETRY_COUNT;
             attempt++) {

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
                            API_RETRY_DELAY_MS * attempt
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

    /*
     * 실제 CCTV 공공데이터 API 한 페이지 요청
     */
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

        /*
         * 인코딩된 serviceKey가
         * RestTemplate에서 다시 인코딩되는 문제를 막기 위해
         * URI.create()를 사용합니다.
         */
        ResponseEntity<String> response =
                restTemplate.getForEntity(
                        URI.create(url),
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

        /*
         * JSON이 아닌 HTML/XML 오류 페이지 등이
         * 반환되는 경우를 차단합니다.
         */
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
                        .path("resultCode")
                        .asText("");

        String resultMessage =
                headerNode
                        .path("resultMsg")
                        .asText("");

        /*
         * 정상 코드가 아닐 경우 실패 처리
         */
        if (!resultCode.isBlank()
                && !"0".equals(resultCode)
                && !"00".equals(resultCode)) {

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

        /*
         * API 응답 형태가
         *
         * response.body
         *
         * 또는
         *
         * body
         *
         * 형태일 가능성을 모두 처리합니다.
         */
        if (bodyNode.isMissingNode()
                || bodyNode.isNull()) {

            bodyNode =
                    root.path(
                            "body"
                    );
        }

        /*
         * 지난 수집에서 pageNo=3065 근처에서
         * body 없는 응답이 한 번 발생했습니다.
         *
         * 여기서 예외가 발생하면
         * fetchPageWithRetry()가 같은 페이지를 다시 요청합니다.
         */
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
                        .path("items")
                        .path("item");

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

    /*
     * CCTV API 한 페이지 결과
     */
    private record CctvPage(
            int totalCount,
            List<JsonNode> items
    ) {
    }

    /*
     * 한 페이지의 유효 CCTV 데이터를
     * 한 번의 batch 작업으로 UPSERT 합니다.
     *
     * mng_no UNIQUE KEY 기준:
     *
     * 존재함 → UPDATE
     * 존재하지 않음 → INSERT
     */
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

    /*
     * API JSON 한 건을 Cctv 객체로 변환합니다.
     *
     * 여기서는 DB 조회를 하지 않습니다.
     *
     * 기존에는:
     *
     * cctvRepository.findByMngNo(...)
     *
     * 를 CCTV마다 실행했기 때문에 매우 느렸습니다.
     *
     * 이제는 새 객체를 만든 뒤
     * 마지막에 batch UPSERT합니다.
     */
    private Cctv convertToCctv(
            JsonNode item
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
         * 관리번호가 없으면
         * UPSERT 기준을 만들 수 없으므로 제외합니다.
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
         * 한국 범위를 크게 벗어난 좌표나
         * 파싱에 실패해 0이 된 데이터는 제외합니다.
         */
        if (!isValidKoreaCoordinate(
                latitude,
                longitude
        )) {

            return null;
        }

        /*
         * DB SELECT 제거.
         *
         * 존재 여부는 나중에 MySQL
         * ON DUPLICATE KEY UPDATE가 판단합니다.
         */
        Cctv cctv =
                new Cctv();

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
                        .replace(".", "-")
                        .replace("/", "-");

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