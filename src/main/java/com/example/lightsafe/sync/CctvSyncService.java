package com.example.lightsafe.sync;

import com.example.lightsafe.common.exception.BadRequestException;
import com.example.lightsafe.emergency.Cctv;
import com.example.lightsafe.emergency.CctvRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.net.URI;

@Slf4j
@Service
@RequiredArgsConstructor
public class CctvSyncService {

    private static final String DATASET =
            "CCTV";

    private static final String PUBLIC_DATA_CCTV_URL =
            "https://apis.data.go.kr/1741000/cctv_info/info";

    private static final int NUM_OF_ROWS =
            1000;

    private final CctvRepository cctvRepository;
    private final DataSyncLogRepository dataSyncLogRepository;

    @Value("${public-data.service-key:}")
    private String publicDataServiceKey;

    public SyncResultResponse syncCctvs() {
        if (publicDataServiceKey == null || publicDataServiceKey.isBlank()) {
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
            int totalCount = Integer.MAX_VALUE;

            while (fetchedCount < totalCount) {
                String url =
                        PUBLIC_DATA_CCTV_URL
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

                if (responseBody == null || responseBody.isBlank()) {
                    throw new BadRequestException(
                            "CCTV API 응답이 비어 있습니다."
                    );
                }

                if (!responseBody.trim().startsWith("{")) {
                    throw new BadRequestException(
                            "CCTV API가 JSON이 아닌 응답을 반환했습니다. type=json 또는 인증키를 확인해주세요."
                    );
                }

                JsonNode root =
                        objectMapper.readTree(responseBody);

                JsonNode responseNode =
                        root.path("response");

                JsonNode headerNode =
                        responseNode.path("header");

                String resultCode =
                        headerNode.path("resultCode").asText("");

                String resultMessage =
                        headerNode.path("resultMsg").asText("");

                if (!resultCode.isBlank()
                        && !"0".equals(resultCode)
                        && !"00".equals(resultCode)) {

                    throw new BadRequestException(
                            "CCTV API 오류: " + resultCode + " / " + resultMessage
                    );
                }

                JsonNode bodyNode =
                        responseNode.path("body");

                if (bodyNode.isMissingNode()) {
                    bodyNode =
                            root.path("body");
                }

                if (bodyNode.isMissingNode()) {
                    throw new BadRequestException(
                            "CCTV API 응답에 body가 없습니다."
                    );
                }

                totalCount =
                        getIntAny(
                                bodyNode,
                                totalCount,
                                "totalCount",
                                "totalCnt"
                        );

                JsonNode itemNode =
                        bodyNode.path("items").path("item");

                if (itemNode.isMissingNode()) {
                    itemNode =
                            bodyNode.path("items");
                }

                List<JsonNode> items =
                        toItemList(itemNode);

                fetchedCount += items.size();

                List<Cctv> cctvs =
                        new ArrayList<>();

                for (JsonNode item : items) {
                    Cctv cctv =
                            convertToCctv(item);

                    if (cctv == null) {
                        continue;
                    }

                    cctvs.add(cctv);
                }

                cctvRepository.saveAll(cctvs);
                savedCount += cctvs.size();

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
                    "CCTV 수집 실패: " + e.getMessage()
            );
        }
    }

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

        if (mngNo.isBlank()) {
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

        if (!isValidKoreaCoordinate(
                latitude,
                longitude
        )) {
            return null;
        }

        Cctv cctv =
                cctvRepository
                        .findByMngNo(mngNo)
                        .orElseGet(Cctv::new);

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

        cctv.setMngNo(mngNo);
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
        cctv.setLatitude(toDecimal(latitude));
        cctv.setLongitude(toDecimal(longitude));
        cctv.setAddress(limit(address, 255));
        cctv.setPurpose(limit(purpose, 50));
        cctv.setCameraCount(parsePositiveInt(cameraCountText));
        cctv.setInstitution(limit(institution, 120));
        cctv.setReferenceDate(parseDate(referenceDateText));

        return cctv;
    }

    private String makeCctvName(
            String institution,
            String purpose,
            String mngNo
    ) {
        if (institution != null && !institution.isBlank()
                && purpose != null && !purpose.isBlank()) {

            return institution + " " + purpose;
        }

        if (institution != null && !institution.isBlank()) {
            return institution + " CCTV";
        }

        return "CCTV-" + mngNo;
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
            itemNode.forEach(items::add);
            return items;
        }

        if (itemNode.isObject()) {
            items.add(itemNode);
        }

        return items;
    }

    private String asTextAny(
            JsonNode node,
            String... fieldNames
    ) {
        if (node == null || fieldNames == null) {
            return "";
        }

        for (String fieldName : fieldNames) {
            JsonNode value =
                    node.path(fieldName);

            if (!value.isMissingNode() && !value.isNull()) {
                String text =
                        value.asText("").trim();

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
                    node.path(fieldName);

            if (value.isNumber()) {
                return value.asInt(defaultValue);
            }

            String text =
                    value.asText("").trim();

            if (!text.isBlank()) {
                try {
                    return Integer.parseInt(text);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return defaultValue;
    }

    private double parseDouble(
            String value
    ) {
        if (value == null || value.isBlank()) {
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
        if (value == null || value.isBlank()) {
            return 1;
        }

        try {
            int number =
                    Integer.parseInt(
                            value.trim()
                    );

            return number <= 0 ? 1 : number;

        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private LocalDate parseDate(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String text =
                value.trim()
                        .replace(".", "-")
                        .replace("/", "-");

        try {
            return LocalDate.parse(text);
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

    private DataSyncLog startLog() {
        DataSyncLog syncLog =
                new DataSyncLog();

        syncLog.setDataset(DATASET);
        syncLog.setStartedAt(LocalDateTime.now());
        syncLog.setStatus("RUNNING");

        return dataSyncLogRepository.save(syncLog);
    }

    private void finishLog(
            DataSyncLog syncLog,
            String status,
            int fetchedCount,
            int savedCount,
            String message
    ) {
        syncLog.setFinishedAt(LocalDateTime.now());
        syncLog.setStatus(status);
        syncLog.setFetchedCount(fetchedCount);
        syncLog.setSavedCount(savedCount);

        if (message != null && message.length() > 500) {
            syncLog.setMessage(
                    message.substring(0, 500)
            );
        } else {
            syncLog.setMessage(message);
        }

        dataSyncLogRepository.save(syncLog);
    }
}