package com.example.lightsafe.sync;

import com.example.lightsafe.common.exception.BadRequestException;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityLightSyncService {

    private static final String DATASET =
            "SECURITY_LIGHT";

    private static final String SECURITY_LIGHT_API_URL =
            "https://api.data.go.kr/openapi/tn_pubr_public_scrty_lmp_api";

    private static final int NUM_OF_ROWS =
            1000;

    private final JdbcTemplate jdbcTemplate;
    private final DataSyncLogRepository dataSyncLogRepository;

    @Value("${public-data.service-key:}")
    private String publicDataServiceKey;

    public SyncResultResponse syncSecurityLights() {

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
        int skippedCount = 0;

        try {
            RestTemplate restTemplate =
                    new RestTemplate();

            ObjectMapper objectMapper =
                    new ObjectMapper();

            int pageNo = 1;
            int totalCount = Integer.MAX_VALUE;

            while ((pageNo - 1) * NUM_OF_ROWS < totalCount) {

                String url =
                        SECURITY_LIGHT_API_URL
                                + "?serviceKey=" + publicDataServiceKey
                                + "&pageNo=" + pageNo
                                + "&numOfRows=" + NUM_OF_ROWS
                                + "&type=json";

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
                            "보안등 API 응답이 비어 있습니다."
                    );
                }

                if (!responseBody.trim().startsWith("{")) {
                    throw new BadRequestException(
                            "보안등 API가 JSON이 아닌 응답을 반환했습니다."
                    );
                }

                JsonNode root =
                        objectMapper.readTree(responseBody);

                JsonNode header =
                        root.path("header");

                String resultCode =
                        header.path("resultCode")
                                .asText("");

                String resultMsg =
                        header.path("resultMsg")
                                .asText("");

                if (!resultCode.isBlank()
                        && !"00".equals(resultCode)
                        && !"0".equals(resultCode)) {

                    throw new BadRequestException(
                            "보안등 API 오류: "
                                    + resultCode
                                    + " / "
                                    + resultMsg
                    );
                }

                JsonNode body =
                        root.path("body");

                if (body.isMissingNode()
                        || body.isNull()) {

                    throw new BadRequestException(
                            "보안등 API 응답에 body가 없습니다."
                    );
                }

                totalCount =
                        body.path("totalCount")
                                .asInt(0);

                JsonNode itemNode =
                        body.path("items")
                                .path("item");

                if (!itemNode.isArray()) {
                    break;
                }

                List<StreetLampSyncRow> rows =
                        new ArrayList<>();

                for (JsonNode item : itemNode) {

                    fetchedCount++;

                    StreetLampSyncRow row =
                            convert(item);

                    if (row == null) {
                        skippedCount++;
                        continue;
                    }

                    rows.add(row);
                }

                if (!rows.isEmpty()) {
                    batchUpsert(rows);
                    savedCount += rows.size();
                }

                if (pageNo == 1
                        || pageNo % 50 == 0) {

                    log.info(
                            "보안등 수집 진행. pageNo={}, totalCount={}, fetchedCount={}, savedCount={}, skippedCount={}",
                            pageNo,
                            totalCount,
                            fetchedCount,
                            savedCount,
                            skippedCount
                    );
                }

                if (itemNode.isEmpty()) {
                    break;
                }

                pageNo++;
            }

            String message =
                    "보안등 수집 완료"
                            + " (좌표 없는 데이터 "
                            + skippedCount
                            + "건 제외)";

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
                    "보안등 수집 중 오류가 발생했습니다.",
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
                    "보안등 수집 실패: "
                            + e.getMessage()
            );
        }
    }

    private StreetLampSyncRow convert(
            JsonNode item
    ) {
        String latitudeText =
                text(item, "latitude");

        String longitudeText =
                text(item, "longitude");

        double latitude =
                parseDouble(latitudeText);

        double longitude =
                parseDouble(longitudeText);

        /*
         * 현재 단계에서는 API 자체에 정상 위경도가 있는
         * 보안등만 DB에 저장합니다.
         *
         * 좌표 없는 행은 이후 VWorld 지오코딩 단계에서 처리합니다.
         */
        if (!isValidKoreaCoordinate(
                latitude,
                longitude
        )) {
            return null;
        }

        String locationName =
                limit(
                        text(item, "lmpLcNm"),
                        255
                );

        String roadAddress =
                limit(
                        text(item, "rdnmadr"),
                        255
                );

        String lotAddress =
                limit(
                        text(item, "lnmadr"),
                        255
                );

        String address =
                !roadAddress.isBlank()
                        ? roadAddress
                        : lotAddress;

        String installType =
                limit(
                        text(item, "installationType"),
                        30
                );

        String insttName =
                limit(
                        text(item, "insttNm"),
                        100
                );

        String insttCode =
                text(item, "insttCode");

        int lampCount =
                parsePositiveInt(
                        text(
                                item,
                                "installationCo"
                        )
                );

        LocalDate referenceDate =
                parseDate(
                        text(
                                item,
                                "referenceDate"
                        )
                );

        byte[] sourceHash =
                createSourceHash(
                        insttCode,
                        locationName,
                        latitude,
                        longitude
                );

        return new StreetLampSyncRow(
                toDecimal(latitude),
                toDecimal(longitude),
                limit(address, 255),
                lampCount,
                sourceHash,
                roadAddress,
                lotAddress,
                locationName,
                insttName,
                installType,
                "API",
                referenceDate
        );
    }

    private void batchUpsert(
            List<StreetLampSyncRow> rows
    ) {
        String sql = """
                INSERT INTO street_lamps (
                    latitude,
                    longitude,
                    address,
                    lamp_count,
                    source_hash,
                    road_address,
                    lot_address,
                    location_name,
                    instt_name,
                    install_type,
                    coord_source,
                    reference_date
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    latitude = VALUES(latitude),
                    longitude = VALUES(longitude),
                    address = VALUES(address),
                    lamp_count = VALUES(lamp_count),
                    road_address = VALUES(road_address),
                    lot_address = VALUES(lot_address),
                    location_name = VALUES(location_name),
                    instt_name = VALUES(instt_name),
                    install_type = VALUES(install_type),
                    coord_source = VALUES(coord_source),
                    reference_date = VALUES(reference_date)
                """;

        jdbcTemplate.batchUpdate(
                sql,
                rows,
                rows.size(),
                (ps, row) -> {
                    ps.setBigDecimal(
                            1,
                            row.latitude()
                    );

                    ps.setBigDecimal(
                            2,
                            row.longitude()
                    );

                    ps.setString(
                            3,
                            row.address()
                    );

                    ps.setInt(
                            4,
                            row.lampCount()
                    );

                    ps.setBytes(
                            5,
                            row.sourceHash()
                    );

                    ps.setString(
                            6,
                            row.roadAddress()
                    );

                    ps.setString(
                            7,
                            row.lotAddress()
                    );

                    ps.setString(
                            8,
                            row.locationName()
                    );

                    ps.setString(
                            9,
                            row.insttName()
                    );

                    ps.setString(
                            10,
                            row.installType()
                    );

                    ps.setString(
                            11,
                            row.coordSource()
                    );

                    if (row.referenceDate() == null) {
                        ps.setNull(
                                12,
                                java.sql.Types.DATE
                        );
                    } else {
                        ps.setDate(
                                12,
                                Date.valueOf(
                                        row.referenceDate()
                                )
                        );
                    }
                }
        );
    }

    private byte[] createSourceHash(
            String insttCode,
            String locationName,
            double latitude,
            double longitude
    ) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "MD5"
                    );

            String source =
                    safe(insttCode)
                            + "|"
                            + safe(locationName)
                            + "|"
                            + latitude
                            + "|"
                            + longitude;

            return digest.digest(
                    source.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

        } catch (Exception e) {
            throw new IllegalStateException(
                    "보안등 source_hash 생성 실패",
                    e
            );
        }
    }

    private String text(
            JsonNode node,
            String field
    ) {
        JsonNode value =
                node.path(field);

        if (value.isMissingNode()
                || value.isNull()) {
            return "";
        }

        return value.asText("")
                .trim();
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

        try {
            return LocalDate.parse(
                    value.trim()
            );
        } catch (Exception e) {
            return null;
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

    private BigDecimal toDecimal(
            double value
    ) {
        return BigDecimal
                .valueOf(value)
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

    private String safe(
            String value
    ) {
        return value == null
                ? ""
                : value;
    }

    private DataSyncLog startLog() {
        DataSyncLog syncLog =
                new DataSyncLog();

        syncLog.setDataset(DATASET);
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

    private record StreetLampSyncRow(
            BigDecimal latitude,
            BigDecimal longitude,
            String address,
            int lampCount,
            byte[] sourceHash,
            String roadAddress,
            String lotAddress,
            String locationName,
            String insttName,
            String installType,
            String coordSource,
            LocalDate referenceDate
    ) {
    }
}