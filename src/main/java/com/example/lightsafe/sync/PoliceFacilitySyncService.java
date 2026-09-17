package com.example.lightsafe.sync;

import com.example.lightsafe.common.exception.BadRequestException;
import com.example.lightsafe.safe.PoliceFacility;
import com.example.lightsafe.safe.PoliceFacilityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PoliceFacilitySyncService {

    private static final String DATASET =
            "POLICE";

    private static final String SAFEMAP_POLICE_FACILITY_URL =
            "https://safemap.go.kr/openapi2/IF_0036";

    private static final int NUM_OF_ROWS =
            100;

    private final PoliceFacilityRepository policeFacilityRepository;
    private final DataSyncLogRepository dataSyncLogRepository;

    @Value("${safemap.service-key:}")
    private String safemapServiceKey;

    public SyncResultResponse syncPoliceFacilities() {
        if (safemapServiceKey == null || safemapServiceKey.isBlank()) {
            throw new BadRequestException(
                    "safemap.service-key가 설정되어 있지 않습니다."
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

            while ((pageNo - 1) * NUM_OF_ROWS < totalCount) {
                String url =
                        UriComponentsBuilder
                                .fromUriString(SAFEMAP_POLICE_FACILITY_URL)
                                .queryParam("serviceKey", safemapServiceKey)
                                .queryParam("pageNo", pageNo)
                                .queryParam("numOfRows", NUM_OF_ROWS)
                                .queryParam("returnType", "JSON")
                                .toUriString();

                ResponseEntity<String> response =
                        restTemplate.getForEntity(
                                url,
                                String.class
                        );

                String responseBody =
                        response.getBody();

                if (responseBody == null || responseBody.isBlank()) {
                    throw new BadRequestException(
                            "치안시설 API 응답이 비어 있습니다."
                    );
                }

                if (!responseBody.trim().startsWith("{")) {
                    throw new BadRequestException(
                            "치안시설 API가 JSON이 아닌 응답을 반환했습니다. returnType 또는 인증키를 확인해주세요."
                    );
                }

                JsonNode root =
                        objectMapper.readTree(responseBody);

                JsonNode bodyNode =
                        root.path("body");

                if (bodyNode.isMissingNode()) {
                    throw new BadRequestException(
                            "치안시설 API 응답에 body가 없습니다."
                    );
                }

                totalCount =
                        bodyNode.path("totalCount").asInt(totalCount);

                JsonNode itemNode =
                        bodyNode.path("items").path("item");

                List<JsonNode> items =
                        toItemList(itemNode);

                fetchedCount += items.size();

                List<PoliceFacility> facilities =
                        new ArrayList<>();

                for (JsonNode item : items) {
                    PoliceFacility facility =
                            convertToPoliceFacility(item);

                    if (facility == null) {
                        continue;
                    }

                    facilities.add(facility);
                }

                policeFacilityRepository.saveAll(facilities);
                savedCount += facilities.size();

                log.info(
                        "치안시설 수집 진행. pageNo={}, fetchedCount={}, savedCount={}, totalCount={}",
                        pageNo,
                        fetchedCount,
                        savedCount,
                        totalCount
                );

                if (items.isEmpty()) {
                    break;
                }

                pageNo++;
            }

            finishLog(
                    syncLog,
                    "SUCCESS",
                    fetchedCount,
                    savedCount,
                    "치안시설 수집 완료"
            );

            return new SyncResultResponse(
                    DATASET,
                    "SUCCESS",
                    fetchedCount,
                    savedCount,
                    "치안시설 수집 완료"
            );

        } catch (Exception e) {
            log.warn(
                    "치안시설 수집 중 오류가 발생했습니다.",
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
                    "치안시설 수집 실패: " + e.getMessage()
            );
        }
    }

    private PoliceFacility convertToPoliceFacility(
            JsonNode item
    ) {
        String objectId =
                asText(
                        item,
                        "objt_id"
                );

        if (objectId.isBlank()) {
            return null;
        }

        double x =
                parseDouble(
                        asText(
                                item,
                                "x"
                        )
                );

        double y =
                parseDouble(
                        asText(
                                item,
                                "y"
                        )
                );

        if (x == 0.0 || y == 0.0) {
            return null;
        }

        LocationPoint locationPoint =
                convertWebMercatorToWgs84(
                        x,
                        y
                );

        if (!isValidKoreaCoordinate(
                locationPoint.latitude(),
                locationPoint.longitude()
        )) {
            return null;
        }

        PoliceFacility facility =
                policeFacilityRepository
                        .findByObjectId(objectId)
                        .orElseGet(PoliceFacility::new);

        facility.setObjectId(objectId);
        facility.setName(asText(item, "fclty_nm"));
        facility.setKind(asText(item, "fclty_ty"));
        facility.setAgency(asText(item, "police"));
        facility.setStation(asText(item, "polcsttn"));

        String roadAddress =
                asText(item, "rn_adres");

        String lotAddress =
                asText(item, "adres");

        facility.setAddress(
                roadAddress.isBlank()
                        ? lotAddress
                        : roadAddress
        );

        facility.setTel(asText(item, "telno"));
        facility.setLatitude(toDecimal(locationPoint.latitude()));
        facility.setLongitude(toDecimal(locationPoint.longitude()));

        return facility;
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

    private LocationPoint convertWebMercatorToWgs84(
            double x,
            double y
    ) {
        double radius =
                6378137.0;

        double longitude =
                (x / radius) * (180.0 / Math.PI);

        double latitude =
                (2.0 * Math.atan(Math.exp(y / radius)) - Math.PI / 2.0)
                        * (180.0 / Math.PI);

        return new LocationPoint(
                latitude,
                longitude
        );
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

    private String asText(
            JsonNode node,
            String fieldName
    ) {
        if (node == null || node.path(fieldName).isMissingNode()) {
            return "";
        }

        return node.path(fieldName).asText("").trim();
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
                    message.substring(
                            0,
                            500
                    )
            );
        } else {
            syncLog.setMessage(message);
        }

        dataSyncLogRepository.save(syncLog);
    }

    private record LocationPoint(
            double latitude,
            double longitude
    ) {
    }
}