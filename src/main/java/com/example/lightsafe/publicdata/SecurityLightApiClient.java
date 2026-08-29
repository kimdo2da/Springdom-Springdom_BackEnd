package com.example.lightsafe.publicdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 전국보안등표준데이터 오픈 API 호출기.
 *
 * https://api.data.go.kr/openapi/tn_pubr_public_scrty_lmp_api
 *
 * 한 번에 1000건까지만 주기 때문에 페이지를 넘겨 가며 전부 읽습니다.
 * 전국 약 184만 건이라 페이지가 1900장 가까이 되고, 다 모아 두면 메모리가 터지므로
 * 한 페이지를 읽을 때마다 곧바로 넘겨 저장하도록 Consumer 로 흘려보냅니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityLightApiClient {

    private static final String RESULT_CODE_SUCCESS = "00";

    private final PublicDataProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 전 페이지를 순서대로 읽어 pageConsumer 에 넘깁니다.
     *
     * @return 읽어 온 총 행 수
     */
    public int fetchAll(
            Consumer<List<RawFacility>> pageConsumer
    ) {
        PublicDataProperties.SecurityLight config =
                properties.getSecurityLight();

        if (config.getServiceKey() == null
                || config.getServiceKey().isBlank()) {

            throw new IllegalStateException(
                    "publicdata.security-light.service-key 가 설정되어 있지 않습니다."
            );
        }

        RestTemplate restTemplate = createRestTemplate();

        int totalCount = 0;
        int fetched = 0;
        int pageNo = 1;

        while (true) {
            if (config.getMaxPages() > 0
                    && pageNo > config.getMaxPages()) {

                log.warn(
                        "보안등 수집 안전장치에 걸려 중단합니다. maxPages={}",
                        config.getMaxPages()
                );
                break;
            }

            JsonNode body =
                    requestPage(
                            restTemplate,
                            config,
                            pageNo
                    );

            if (body == null) {
                break;
            }

            if (totalCount == 0) {
                totalCount = body.path("totalCount").asInt(0);
                log.info(
                        "보안등 전체 건수 {}건, 페이지당 {}건으로 수집을 시작합니다.",
                        totalCount,
                        config.getPageSize()
                );
            }

            List<RawFacility> page =
                    toFacilities(
                            body.path("items").path("item")
                    );

            if (page.isEmpty()) {
                break;
            }

            pageConsumer.accept(page);
            fetched += page.size();

            if (pageNo % 100 == 0) {
                log.info(
                        "보안등 수집 진행 중. {}/{}건",
                        fetched,
                        totalCount
                );
            }

            if (totalCount > 0 && fetched >= totalCount) {
                break;
            }

            pageNo++;
            sleep(config.getRequestIntervalMillis());
        }

        log.info(
                "보안등 수집 완료. 총 {}건을 읽었습니다.",
                fetched
        );

        return fetched;
    }

    private JsonNode requestPage(
            RestTemplate restTemplate,
            PublicDataProperties.SecurityLight config,
            int pageNo
    ) {
        /*
         * 인증키는 이미 URL 인코딩된 값이라 UriComponentsBuilder 에 넣으면
         * % 가 %25 로 한 번 더 인코딩돼 인증에 실패합니다.
         * 그래서 문자열을 직접 만들어 URI 로 넘깁니다.
         */
        String url =
                config.getEndpoint()
                        + "?serviceKey=" + config.getServiceKey()
                        + "&pageNo=" + pageNo
                        + "&numOfRows=" + config.getPageSize()
                        + "&type=json";

        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= config.getMaxRetry(); attempt++) {
            try {
                String response =
                        restTemplate.getForObject(
                                URI.create(url),
                                String.class
                        );

                if (response == null || response.isBlank()) {
                    throw new IllegalStateException(
                            "빈 응답을 받았습니다."
                    );
                }

                JsonNode root =
                        objectMapper.readTree(response);

                String resultCode =
                        root.path("header").path("resultCode").asText("");

                if (!RESULT_CODE_SUCCESS.equals(resultCode)) {
                    throw new IllegalStateException(
                            "공공데이터포털 오류 응답입니다. resultCode="
                                    + resultCode
                                    + ", resultMsg="
                                    + root.path("header").path("resultMsg").asText("")
                    );
                }

                return root.path("body");

            } catch (Exception e) {
                lastError =
                        new IllegalStateException(
                                "보안등 API 호출에 실패했습니다. pageNo=" + pageNo,
                                e
                        );

                log.warn(
                        "보안등 API 호출 실패. pageNo={}, attempt={}/{}, message={}",
                        pageNo,
                        attempt,
                        config.getMaxRetry(),
                        e.getMessage()
                );

                sleep(500L * attempt);
            }
        }

        throw lastError;
    }

    private List<RawFacility> toFacilities(
            JsonNode itemNode
    ) {
        List<RawFacility> facilities = new ArrayList<>();

        if (itemNode == null || !itemNode.isArray()) {
            return facilities;
        }

        for (JsonNode item : itemNode) {
            Double latitude =
                    CoordinateSupport.parseCoordinate(
                            item.path("latitude").asText(null)
                    );

            Double longitude =
                    CoordinateSupport.parseCoordinate(
                            item.path("longitude").asText(null)
                    );

            /*
             * 지오코딩은 지번주소가 도로명주소보다 잘 맞아서 지번을 먼저 씁니다.
             */
            String address =
                    CoordinateSupport.clean(
                            item.path("lnmadr").asText(null)
                    );

            if (address == null) {
                address =
                        CoordinateSupport.clean(
                                item.path("rdnmadr").asText(null)
                        );
            }

            facilities.add(
                    new RawFacility(
                            latitude,
                            longitude,
                            address,
                            CoordinateSupport.parseCount(
                                    item.path("installationCo").asText(null)
                            ),
                            CoordinateSupport.extractSido(address),
                            CoordinateSupport.clean(
                                    item.path("lmpLcNm").asText(null)
                            ),
                            null
                    )
            );
        }

        return facilities;
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(
                Duration.ofSeconds(10)
        );

        factory.setReadTimeout(
                Duration.ofSeconds(60)
        );

        return new RestTemplate(factory);
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
                    "수집 작업이 중단되었습니다.",
                    e
            );
        }
    }
}
