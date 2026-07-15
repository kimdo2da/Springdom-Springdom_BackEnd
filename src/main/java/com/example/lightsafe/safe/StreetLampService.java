package com.example.lightsafe.safe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class StreetLampService {

    // 공공데이터포털 > 마이페이지 > 활용신청 내역에서 발급받는 "일반 인증키(Decoding)"
    // 절대 이 값을 소스코드에 직접 적지 말고, 환경변수(PUBLIC_DATA_SERVICE_KEY)로 주입하세요.
    @Value("${publicdata.service-key:}")
    private String serviceKey;

    private static final String BASE_URL = "https://api.data.go.kr/openapi/tn_pubr_public_scrty_lmp_api";
    private static final int PAGE_SIZE = 1000; // data.go.kr numOfRows 최대값

    private final List<StreetLampDto> cachedStreetLamps = new ArrayList<>();
    // ⚠️ 타임아웃 없는 기본 RestTemplate은 응답이 안 오면 무한정 기다려서 서버 시작 자체가 멈춰버림 → 타임아웃 필수
    private final RestTemplate restTemplate = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 5초
        factory.setReadTimeout(15000);   // 15초
        return new RestTemplate(factory);
    }
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void initStreetLampData() {
        if (serviceKey == null || serviceKey.isBlank()) {
            System.err.println("🚨 publicdata.service-key 가 설정되지 않아 보안등 데이터를 불러오지 않습니다.");
            return;
        }

        System.out.println("⏳ 전국 보안등 데이터 로딩 시작 (전국 단위라 시간이 다소 걸립니다)...");
        Set<String> uniqueLocations = new HashSet<>();

        try {
            int pageNo = 1;
            int totalCount = Integer.MAX_VALUE;
            long fetched = 0;

            while (fetched < totalCount) {
                URI uri = UriComponentsBuilder.fromUriString(BASE_URL)
                        .queryParam("serviceKey", serviceKey)
                        .queryParam("pageNo", pageNo)
                        .queryParam("numOfRows", PAGE_SIZE)
                        .queryParam("type", "json")
                        .build(true)
                        .toUri();

                String rawResponse = restTemplate.getForObject(uri, String.class);
                JsonNode root = objectMapper.readTree(rawResponse);
                JsonNode body = root.path("response").path("body");

                if (pageNo == 1) {
                    totalCount = body.path("totalCount").asInt(0);
                    if (totalCount == 0) {
                        System.err.println("🚨 보안등 API 응답에 데이터가 없습니다. 인증키/파라미터를 확인하세요.");
                        System.err.println("응답 원문 일부: " + rawResponse.substring(0, Math.min(300, rawResponse.length())));
                        break;
                    }
                }

                JsonNode items = body.path("items");
                if (!items.isArray() || items.isEmpty()) {
                    break;
                }

                for (JsonNode item : items) {
                    String roadAddr = item.path("RDNMADR").asText("").trim();
                    String lotAddr = item.path("LNMADR").asText("").trim();
                    String address = roadAddr.isEmpty() ? lotAddr : roadAddr;

                    double lat = item.path("LATITUDE").asDouble(0);
                    double lng = item.path("LONGITUDE").asDouble(0);
                    if (lat == 0 || lng == 0 || address.isEmpty()) continue;

                    String locationKey = lat + "_" + lng;
                    if (!uniqueLocations.add(locationKey)) continue;

                    StreetLampDto dto = new StreetLampDto();
                    dto.setLampId((long) (cachedStreetLamps.size() + 1));
                    dto.setLampName(item.path("LMP_LC_NM").asText(""));
                    dto.setAddress(address);
                    dto.setLatitude(lat);
                    dto.setLongitude(lng);
                    dto.setInstallYear(item.path("INSTALLATION_YEAR").asText(""));
                    dto.setInstitutionName(item.path("INSTITUTION_NM").asText(""));

                    cachedStreetLamps.add(dto);
                }

                fetched += items.size();
                System.out.println("   → " + fetched + " / " + totalCount + " 건 로딩 중... (page " + pageNo + ")");
                pageNo++;
            }
        } catch (Exception e) {
            System.err.println("🚨 보안등 데이터 로딩 중 오류 발생: " + e.getMessage());
        }

        System.out.println("✅ 보안등 데이터 로딩 완료! 총 " + cachedStreetLamps.size() + "개의 위치가 메모리에 장착되었습니다.");
    }

    public List<StreetLampDto> getStreetLampData() {
        return cachedStreetLamps;
    }
}
