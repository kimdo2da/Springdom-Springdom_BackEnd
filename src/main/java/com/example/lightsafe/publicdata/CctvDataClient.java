package com.example.lightsafe.publicdata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 전국CCTV표준데이터 수집기.
 *
 * 이 데이터셋은 오픈 API 가 아니라 배포 파일(CSV)로 제공됩니다.
 * https://file.localdata.go.kr/file/download/cctv_info/info
 * 약 79MB, CP949, 37만 행이며 인증키가 필요 없습니다.
 *
 * 공공데이터포털의 표준 오픈 API(tn_pubr_public_cctv_api)를 쓰려면
 * 해당 데이터셋에 활용신청이 승인돼야 합니다. 승인되면
 * publicdata.cctv.mode=api 로 바꾸기만 하면 됩니다.
 *
 * 파일이 79MB 라 통째로 메모리에 올리지 않고 한 줄씩 읽어 넘깁니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CctvDataClient {

    private static final String MODE_API = "api";

    private static final String RESULT_CODE_SUCCESS = "00";

    private static final String CSV_SPLIT_PATTERN =
            ",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)";

    private static final int BATCH_SIZE = 5000;

    /**
     * 배포 파일이 브라우저 요청만 받아 주기 때문에 UA 를 채워 보냅니다.
     */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0 Safari/537.36";

    private final PublicDataProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public int fetchAll(
            Consumer<List<RawFacility>> pageConsumer
    ) {
        PublicDataProperties.Cctv config =
                properties.getCctv();

        if (MODE_API.equalsIgnoreCase(config.getMode())) {
            return fetchFromApi(config, pageConsumer);
        }

        return fetchFromFile(config, pageConsumer);
    }

    // ------------------------------------------------------------------
    // 배포 파일(CSV) 방식 — 기본값
    // ------------------------------------------------------------------

    private int fetchFromFile(
            PublicDataProperties.Cctv config,
            Consumer<List<RawFacility>> pageConsumer
    ) {
        log.info(
                "CCTV 배포 파일을 내려받습니다. url={}",
                config.getFileUrl()
        );

        int fetched = 0;
        HttpURLConnection connection = null;

        try {
            connection = openConnection(config);

            Charset charset =
                    Charset.forName(config.getFileCharset());

            try (InputStream input = connection.getInputStream();
                 BufferedReader reader =
                         new BufferedReader(
                                 new InputStreamReader(input, charset),
                                 1 << 16
                         )) {

                String headerLine = reader.readLine();

                if (headerLine == null) {
                    throw new IllegalStateException(
                            "CCTV 배포 파일이 비어 있습니다."
                    );
                }

                CsvColumns columns =
                        CsvColumns.from(headerLine);

                List<RawFacility> batch =
                        new ArrayList<>(BATCH_SIZE);

                String line;

                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }

                    RawFacility facility =
                            columns.toFacility(line);

                    if (facility == null) {
                        continue;
                    }

                    batch.add(facility);
                    fetched++;

                    if (batch.size() >= BATCH_SIZE) {
                        pageConsumer.accept(batch);
                        batch = new ArrayList<>(BATCH_SIZE);
                    }
                }

                if (!batch.isEmpty()) {
                    pageConsumer.accept(batch);
                }
            }

        } catch (Exception e) {
            throw new IllegalStateException(
                    "CCTV 배포 파일을 읽는 중 오류가 발생했습니다.",
                    e
            );
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        log.info(
                "CCTV 배포 파일 읽기 완료. 총 {}행",
                fetched
        );

        return fetched;
    }

    private HttpURLConnection openConnection(
            PublicDataProperties.Cctv config
    ) throws Exception {

        String url = config.getFileUrl();

        /*
         * 배포 서버가 302 로 실제 파일 위치를 알려 주는데,
         * HttpURLConnection 은 http -> https 처럼 프로토콜이 바뀌면 따라가지 않습니다.
         * 그래서 최대 5번까지 직접 따라갑니다.
         */
        for (int redirect = 0; redirect < 5; redirect++) {
            HttpURLConnection connection =
                    (HttpURLConnection) URI.create(url).toURL().openConnection();

            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty(
                    "Referer",
                    "https://file.localdata.go.kr/file/cctv_info/info"
            );
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(config.getDownloadTimeoutMillis());

            int status = connection.getResponseCode();

            if (status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_SEE_OTHER
                    || status == 307
                    || status == 308) {

                String location =
                        connection.getHeaderField("Location");

                connection.disconnect();

                if (location == null || location.isBlank()) {
                    throw new IllegalStateException(
                            "CCTV 배포 파일이 이동 응답을 줬지만 위치가 없습니다."
                    );
                }

                url = URI.create(url).resolve(location).toString();
                continue;
            }

            if (status != HttpURLConnection.HTTP_OK) {
                connection.disconnect();
                throw new IllegalStateException(
                        "CCTV 배포 파일 응답이 정상이 아닙니다. status=" + status
                );
            }

            return connection;
        }

        throw new IllegalStateException(
                "CCTV 배포 파일 주소가 계속 이동합니다."
        );
    }

    /**
     * 헤더 이름으로 칸 번호를 찾아 둡니다.
     *
     * 공공데이터가 칸 순서를 바꿔도 헤더 이름만 같으면 그대로 읽힙니다.
     */
    private record CsvColumns(
            int latitudeIndex,
            int longitudeIndex,
            int roadAddressIndex,
            int lotAddressIndex,
            int purposeIndex,
            int cameraCountIndex,
            int institutionIndex
    ) {

        private static CsvColumns from(String headerLine) {
            String header =
                    headerLine.startsWith("﻿")
                            ? headerLine.substring(1)
                            : headerLine;

            String[] names =
                    header.split(CSV_SPLIT_PATTERN);

            for (int i = 0; i < names.length; i++) {
                names[i] = names[i].replace("\"", "").trim();
            }

            CsvColumns columns =
                    new CsvColumns(
                            indexOf(names, "WGS84위도", "위도"),
                            indexOf(names, "WGS84경도", "경도"),
                            indexOf(names, "소재지도로명주소"),
                            indexOf(names, "소재지지번주소"),
                            indexOf(names, "설치목적구분"),
                            indexOf(names, "카메라대수"),
                            indexOf(names, "관리기관명")
                    );

            if (columns.latitudeIndex < 0 || columns.longitudeIndex < 0) {
                throw new IllegalStateException(
                        "CCTV 배포 파일에서 위도/경도 칸을 찾지 못했습니다. header=" + header
                );
            }

            return columns;
        }

        private static int indexOf(
                String[] names,
                String... candidates
        ) {
            for (String candidate : candidates) {
                for (int i = 0; i < names.length; i++) {
                    if (names[i].equals(candidate)) {
                        return i;
                    }
                }
            }
            return -1;
        }

        private String at(String[] columns, int index) {
            if (index < 0 || index >= columns.length) {
                return null;
            }
            return CoordinateSupport.clean(columns[index]);
        }

        private RawFacility toFacility(String line) {
            String[] columns =
                    line.split(CSV_SPLIT_PATTERN);

            if (columns.length <= Math.max(latitudeIndex, longitudeIndex)) {
                return null;
            }

            String roadAddress = at(columns, roadAddressIndex);
            String lotAddress = at(columns, lotAddressIndex);

            /*
             * CCTV 는 지도에 위치를 그리는 용도라 사람이 읽기 좋은 도로명을 먼저 씁니다.
             */
            String address =
                    roadAddress != null ? roadAddress : lotAddress;

            return new RawFacility(
                    CoordinateSupport.parseCoordinate(columns[latitudeIndex]),
                    CoordinateSupport.parseCoordinate(columns[longitudeIndex]),
                    address,
                    CoordinateSupport.parseCount(at(columns, cameraCountIndex)),
                    CoordinateSupport.extractSido(address),
                    at(columns, institutionIndex),
                    at(columns, purposeIndex)
            );
        }
    }

    // ------------------------------------------------------------------
    // 표준 오픈 API 방식 — 활용신청이 승인되면 mode=api 로 사용
    // ------------------------------------------------------------------

    private int fetchFromApi(
            PublicDataProperties.Cctv config,
            Consumer<List<RawFacility>> pageConsumer
    ) {
        if (config.getServiceKey() == null
                || config.getServiceKey().isBlank()) {

            throw new IllegalStateException(
                    "publicdata.cctv.service-key 가 설정되어 있지 않습니다."
            );
        }

        org.springframework.web.client.RestTemplate restTemplate =
                new org.springframework.web.client.RestTemplate();

        int totalCount = 0;
        int fetched = 0;
        int pageNo = 1;

        while (true) {
            if (config.getMaxPages() > 0 && pageNo > config.getMaxPages()) {
                break;
            }

            String url =
                    config.getEndpoint()
                            + "?serviceKey=" + config.getServiceKey()
                            + "&pageNo=" + pageNo
                            + "&numOfRows=" + config.getPageSize()
                            + "&type=json";

            String response =
                    restTemplate.getForObject(
                            URI.create(url),
                            String.class
                    );

            JsonNode root;

            try {
                root = objectMapper.readTree(response);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "CCTV 표준 API 응답을 해석하지 못했습니다. pageNo=" + pageNo,
                        e
                );
            }

            String resultCode =
                    root.path("header").path("resultCode").asText("");

            if (!RESULT_CODE_SUCCESS.equals(resultCode)) {
                throw new IllegalStateException(
                        "CCTV 표준 API 오류 응답입니다. resultCode=" + resultCode
                                + ", resultMsg="
                                + root.path("header").path("resultMsg").asText("")
                );
            }

            JsonNode body = root.path("body");

            if (totalCount == 0) {
                totalCount = body.path("totalCount").asInt(0);
            }

            JsonNode items =
                    body.path("items").path("item");

            if (!items.isArray() || items.isEmpty()) {
                break;
            }

            List<RawFacility> page = new ArrayList<>();

            for (JsonNode item : items) {
                /*
                 * 표준데이터 계열은 필드 이름이 데이터셋마다 조금씩 달라서
                 * 있을 법한 이름을 차례로 찾습니다.
                 */
                String address =
                        firstText(item, "rdnmadr", "lnmadr", "address");

                page.add(
                        new RawFacility(
                                CoordinateSupport.parseCoordinate(
                                        firstText(item, "latitude", "lat")
                                ),
                                CoordinateSupport.parseCoordinate(
                                        firstText(item, "longitude", "lot", "lon")
                                ),
                                address,
                                CoordinateSupport.parseCount(
                                        firstText(item, "cameraCo", "installationCo")
                                ),
                                CoordinateSupport.extractSido(address),
                                firstText(item, "institutionNm", "insttNm"),
                                firstText(item, "itlpc", "installationPurpose")
                        )
                );
            }

            pageConsumer.accept(page);
            fetched += page.size();

            if (totalCount > 0 && fetched >= totalCount) {
                break;
            }

            pageNo++;
        }

        return fetched;
    }

    private String firstText(
            JsonNode item,
            String... fieldNames
    ) {
        for (String fieldName : fieldNames) {
            JsonNode node = item.path(fieldName);

            if (!node.isMissingNode() && !node.isNull()) {
                String text = CoordinateSupport.clean(node.asText(null));

                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }
}
