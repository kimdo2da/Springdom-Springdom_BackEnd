package com.example.lightsafe.service;

import com.example.lightsafe.dto.CctvDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@Service
public class CctvService {

    private final String BASE_URL = "https://apis.data.go.kr/1741000/cctv_info/info";
    private final String SERVICE_KEY = "**********공공api키자리입니다.";

    public List<CctvDto> getCctvData() {

        List<CctvDto> list = new ArrayList<>();
        RestTemplate restTemplate = new RestTemplate();
        ObjectMapper mapper = new ObjectMapper();

        try {
            int page = 1;
            int maxPage = 1; // 첫 요청 후 실제 최대 페이지로 업데이트 됨
            int numOfRows = 1000;

            while (page <= maxPage) {
                String url = UriComponentsBuilder
                        .fromUriString(BASE_URL)
                        .queryParam("serviceKey", SERVICE_KEY)
                        .queryParam("pageNo", page)
                        .queryParam("numOfRows", numOfRows)
                        .queryParam("type", "json")
                        .build(false)
                        .toUriString();

                String response = restTemplate.getForObject(url, String.class);
                JsonNode root = mapper.readTree(response);
                JsonNode body = root.path("response").path("body");

                // 🔥 1. 처음 한 번만 전체 데이터 개수를 파악하여 끝 페이지(maxPage) 계산
                if (page == 1) {
                    int totalCount = body.path("totalCount").asInt(0);
                    if (totalCount > 0) {
                        maxPage = (int) Math.ceil((double) totalCount / numOfRows);
                        System.out.println("✅ 전국 CCTV 총 개수: " + totalCount + "개 (총 " + maxPage + "페이지 탐색 시작)");
                    }
                }

                JsonNode items = body.path("items").path("item");
                if (items.isMissingNode() || items.isNull()) break;

                // 🔥 2. 답답함 해소! 10페이지 탐색할 때마다 콘솔에 진행 상황 출력
                if (page % 10 == 0) {
                    System.out.println("⏳ 데이터 수집 중... 현재 " + page + " / " + maxPage + " 페이지 완료");
                }

                for (JsonNode item : items) {
                    // 🔥 3. 지번주소(LCTN_LOTNO_ADDR)가 비어있으면 도로명주소(LCTN_ROAD_NM_ADDR) 가져오기
                    String address = item.path("LCTN_LOTNO_ADDR").asText("");
                    if (address.isEmpty()) {
                        address = item.path("LCTN_ROAD_NM_ADDR").asText("");
                    }

                    String lat = item.path("WGS84_LAT").asText("");
                    String lng = item.path("WGS84_LOT").asText("");

                    if (lat.isEmpty() || lng.isEmpty() || address.isEmpty()) continue;

                    // 🔥 4. 서울 필터 적용
                    if (!(address.contains("서울") || address.contains("Seoul"))) continue;

                    CctvDto dto = new CctvDto();
                    dto.setLat(lat);
                    dto.setLng(lng);
                    dto.setAddress(address);
                    dto.setPurpose(item.path("INSTL_PRPS_SE_NM").asText(""));

                    list.add(dto);
                }

                // API 서버 과부하 및 차단 방지를 위한 휴식 시간 (0.1초)
                Thread.sleep(100);
                page++;
            }

        } catch (Exception e) {
            System.err.println("데이터 조회 중 오류 발생: " + e.getMessage());
        }

        // 이 로그가 찍혀야 프론트엔드로 데이터가 넘어갑니다!
        System.out.println("🔥 최종 서울 CCTV 개수: " + list.size());
        return list;
    }
}