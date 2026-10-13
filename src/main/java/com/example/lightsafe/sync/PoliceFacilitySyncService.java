package com.example.lightsafe.sync;

import com.example.lightsafe.common.exception.BadRequestException;
import com.example.lightsafe.geocoding.VWorldGeocodingService;
import com.example.lightsafe.safe.PoliceFacility;
import com.example.lightsafe.safe.PoliceFacilityRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final VWorldGeocodingService vWorldGeocodingService;
    private final PlatformTransactionManager transactionManager;

    @Value("${safemap.service-key:}")
    private String safemapServiceKey;


    public SyncResultResponse syncPoliceFacilities() {

        if (safemapServiceKey == null
                || safemapServiceKey.isBlank()) {

            throw new BadRequestException(
                    "safemap.service-key가 설정되어 있지 않습니다."
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

        /*
         * 치안시설 objt_id는 제공기관 갱신 과정에서
         * 기존 시설임에도 값이 변경될 수 있음이 확인되었습니다.
         *
         * 따라서 기존 DB를 objt_id 기준으로 누적 UPSERT하지 않고,
         * 이번 API 전체 결과를 최신 스냅샷으로 준비한 뒤
         * 수집이 완전히 성공했을 때 DB 전체를 교체합니다.
         *
         * 같은 최신 API 결과 안에서 동일 object_id가
         * 중복되는 경우에는 마지막 행 하나만 유지합니다.
         */
        Map<String, PoliceFacility> latestFacilitiesByObjectId =
                new LinkedHashMap<>();

        try {

            RestTemplate restTemplate =
                    new RestTemplate();

            ObjectMapper objectMapper =
                    new ObjectMapper();

            int pageNo =
                    1;

            int totalCount =
                    -1;

            while (totalCount < 0
                    || fetchedCount < totalCount) {

                String url =
                        UriComponentsBuilder
                                .fromUriString(
                                        SAFEMAP_POLICE_FACILITY_URL
                                )
                                .queryParam(
                                        "serviceKey",
                                        safemapServiceKey
                                )
                                .queryParam(
                                        "pageNo",
                                        pageNo
                                )
                                .queryParam(
                                        "numOfRows",
                                        NUM_OF_ROWS
                                )
                                .queryParam(
                                        "returnType",
                                        "JSON"
                                )
                                .toUriString();

                ResponseEntity<String> response =
                        restTemplate.getForEntity(
                                url,
                                String.class
                        );

                String responseBody =
                        response.getBody();

                if (responseBody == null
                        || responseBody.isBlank()) {

                    throw new BadRequestException(
                            "치안시설 API 응답이 비어 있습니다."
                    );
                }

                if (!responseBody
                        .trim()
                        .startsWith("{")) {

                    throw new BadRequestException(
                            "치안시설 API가 JSON이 아닌 응답을 반환했습니다. returnType 또는 인증키를 확인해주세요."
                    );
                }

                JsonNode root =
                        objectMapper.readTree(
                                responseBody
                        );

                JsonNode bodyNode =
                        root.path(
                                "body"
                        );

                if (bodyNode.isMissingNode()
                        || bodyNode.isNull()) {

                    throw new BadRequestException(
                            "치안시설 API 응답에 body가 없습니다."
                    );
                }

                int responseTotalCount =
                        bodyNode
                                .path("totalCount")
                                .asInt(-1);

                if (responseTotalCount < 0) {

                    throw new BadRequestException(
                            "치안시설 API 응답의 totalCount가 올바르지 않습니다."
                    );
                }

                /*
                 * 첫 페이지에서 전체 건수를 확정합니다.
                 *
                 * 수집 도중 totalCount가 바뀌면
                 * 서로 다른 시점의 데이터가 섞일 수 있으므로
                 * 기존 DB를 교체하지 않고 실패 처리합니다.
                 */
                if (totalCount < 0) {

                    totalCount =
                            responseTotalCount;

                    if (totalCount == 0) {

                        throw new BadRequestException(
                                "치안시설 API의 totalCount가 0입니다. 기존 데이터를 보호하기 위해 교체하지 않습니다."
                        );
                    }

                } else if (totalCount
                        != responseTotalCount) {

                    throw new BadRequestException(
                            "치안시설 API 수집 도중 totalCount가 변경되었습니다. 기존 데이터를 보호하기 위해 교체하지 않습니다."
                    );
                }

                JsonNode itemNode =
                        bodyNode
                                .path("items")
                                .path("item");

                List<JsonNode> items =
                        toItemList(
                                itemNode
                        );

                /*
                 * 전체 건수를 아직 다 받지 않았는데
                 * 빈 페이지가 나오면 부분 수집으로 판단합니다.
                 *
                 * 이 경우 기존 DB는 절대 삭제하지 않습니다.
                 */
                if (items.isEmpty()
                        && fetchedCount < totalCount) {

                    throw new BadRequestException(
                            "치안시설 API가 전체 데이터를 받기 전에 빈 페이지를 반환했습니다."
                    );
                }

                fetchedCount +=
                        items.size();

                for (JsonNode item : items) {

                    PoliceFacility facility =
                            convertToPoliceFacility(
                                    item,
                                    geocodingSession
                            );

                    if (facility == null) {
                        continue;
                    }

                    latestFacilitiesByObjectId.put(
                            facility.getObjectId(),
                            facility
                    );
                }

                log.info(
                        "치안시설 수집 진행. pageNo={}, fetchedCount={}, preparedCount={}, totalCount={}",
                        pageNo,
                        fetchedCount,
                        latestFacilitiesByObjectId.size(),
                        totalCount
                );

                pageNo++;
            }

            /*
             * 제공기관이 알려준 전체 건수와
             * 실제로 받은 원본 건수가 정확히 일치해야
             * DB 교체를 허용합니다.
             */
            if (fetchedCount != totalCount) {

                throw new BadRequestException(
                        "치안시설 전체 수집 건수가 일치하지 않습니다. "
                                + "totalCount="
                                + totalCount
                                + ", fetchedCount="
                                + fetchedCount
                );
            }

            /*
             * VWorld 외부 장애로 circuit이 열린 경우나
             * VWorld 호출 제한 때문에 좌표 보정이 스킵된 경우에는
             * 이번 스냅샷이 불완전할 가능성이 있습니다.
             *
             * 이 상태에서 기존 정상 DB를 삭제하면 안 되므로
             * 전체 교체를 중단합니다.
             */
            if (geocodingSession.externalCircuitOpen()) {

                throw new BadRequestException(
                        "VWorld 외부 장애로 치안시설 좌표 보정이 중단되었습니다. 기존 치안시설 DB를 유지합니다."
                );
            }

            if (geocodingSession.limitSkippedCount() > 0) {

                throw new BadRequestException(
                        "VWorld 호출 제한으로 일부 치안시설 좌표 보정이 스킵되었습니다. 기존 치안시설 DB를 유지합니다."
                );
            }

            List<PoliceFacility> latestSnapshot =
                    new ArrayList<>(
                            latestFacilitiesByObjectId.values()
                    );

            if (latestSnapshot.isEmpty()) {

                throw new BadRequestException(
                        "저장 가능한 치안시설이 0건입니다. 기존 치안시설 DB를 유지합니다."
                );
            }

            /*
             * 여기까지 왔을 때만 DB 교체를 수행합니다.
             *
             * DELETE와 INSERT를 하나의 트랜잭션으로 묶기 때문에
             * 새 데이터 저장 도중 문제가 생기면 전체 ROLLBACK되어
             * 기존 치안시설 데이터가 복구됩니다.
             */
            replaceSnapshot(
                    latestSnapshot
            );

            savedCount =
                    latestSnapshot.size();

            String message =
                    "치안시설 수집 완료"
                            + " (최신 스냅샷="
                            + savedCount
                            + ", 지오코딩 적용="
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
                    "치안시설 수집 실패: "
                            + e.getMessage()
            );
        }
    }


    /*
     * 기존 DB 삭제 + 최신 스냅샷 저장을
     * 하나의 DB 트랜잭션으로 처리합니다.
     *
     * TRUNCATE는 MySQL에서 트랜잭션 ROLLBACK 보호를
     * 기대하기 어렵기 때문에 사용하지 않습니다.
     */
    private void replaceSnapshot(
            List<PoliceFacility> latestSnapshot
    ) {

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        transactionTemplate.executeWithoutResult(
                status -> {

                    policeFacilityRepository
                            .deleteAllInBatch();

                    policeFacilityRepository
                            .saveAllAndFlush(
                                    latestSnapshot
                            );
                }
        );
    }


    private PoliceFacility convertToPoliceFacility(
            JsonNode item,
            VWorldGeocodingService.GeocodingSession geocodingSession
    ) {

        String objectId =
                asText(
                        item,
                        "objt_id"
                );

        if (objectId.isBlank()) {
            return null;
        }

        String roadAddress =
                asText(
                        item,
                        "rn_adres"
                );

        String lotAddress =
                asText(
                        item,
                        "adres"
                );

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

        LocationPoint locationPoint =
                null;

        /*
         * Safemap 원본 좌표가 정상이라면
         * 원본 좌표를 우선 사용합니다.
         */
        if (x != 0.0
                && y != 0.0) {

            LocationPoint converted =
                    convertWebMercatorToWgs84(
                            x,
                            y
                    );

            if (isValidKoreaCoordinate(
                    converted.latitude(),
                    converted.longitude()
            )) {

                locationPoint =
                        converted;
            }
        }

        /*
         * 원본 좌표가 없거나 잘못된 경우에만
         * VWorld 주소 지오코딩 fallback을 사용합니다.
         */
        if (locationPoint == null) {

            VWorldGeocodingService.GeocodingResult geocodingResult =
                    vWorldGeocodingService.geocode(
                            roadAddress,
                            lotAddress,
                            geocodingSession
                    );

            if (geocodingResult == null) {
                return null;
            }

            locationPoint =
                    new LocationPoint(
                            geocodingResult.latitude(),
                            geocodingResult.longitude()
                    );
        }

        /*
         * 이번에는 기존 DB의 object_id를 찾아
         * UPDATE하지 않습니다.
         *
         * API 전체 수집이 끝난 뒤
         * 최신 스냅샷 전체를 교체하기 때문에
         * 항상 새로운 snapshot row를 만듭니다.
         */
        PoliceFacility facility =
                new PoliceFacility();

        facility.setObjectId(
                objectId
        );

        facility.setName(
                asText(
                        item,
                        "fclty_nm"
                )
        );

        facility.setKind(
                asText(
                        item,
                        "fclty_ty"
                )
        );

        facility.setAgency(
                asText(
                        item,
                        "police"
                )
        );

        facility.setStation(
                asText(
                        item,
                        "polcsttn"
                )
        );

        facility.setAddress(
                roadAddress.isBlank()
                        ? lotAddress
                        : roadAddress
        );

        facility.setTel(
                asText(
                        item,
                        "telno"
                )
        );

        facility.setLatitude(
                toDecimal(
                        locationPoint.latitude()
                )
        );

        facility.setLongitude(
                toDecimal(
                        locationPoint.longitude()
                )
        );

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


    private LocationPoint convertWebMercatorToWgs84(
            double x,
            double y
    ) {

        double radius =
                6378137.0;

        double longitude =
                (x / radius)
                        * (180.0 / Math.PI);

        double latitude =
                (
                        2.0
                                * Math.atan(
                                Math.exp(
                                        y / radius
                                )
                        )
                                - Math.PI / 2.0
                )
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
                .valueOf(
                        value
                )
                .setScale(
                        7,
                        RoundingMode.HALF_UP
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


    private String asText(
            JsonNode node,
            String fieldName
    ) {

        if (node == null
                || node
                .path(fieldName)
                .isMissingNode()) {

            return "";
        }

        return node
                .path(fieldName)
                .asText("")
                .trim();
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

        return dataSyncLogRepository
                .save(
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

        dataSyncLogRepository
                .save(
                        syncLog
                );
    }


    private record LocationPoint(
            double latitude,
            double longitude
    ) {
    }
}