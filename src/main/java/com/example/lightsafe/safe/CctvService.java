package com.example.lightsafe.safe;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class CctvService {

    private final List<CctvDto> cachedCctvList = new ArrayList<>();

    @PostConstruct
    public void initCctvData() {
        System.out.println("⏳ 서버 시작 중... CCTV 데이터 파일을 1회 로딩합니다!");
        Set<String> uniqueLocations = new HashSet<>();

        try {
            ClassPathResource resource = new ClassPathResource("CCTV정보_서울특별시.csv");
            BufferedReader br = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));

            String line;
            boolean isFirstLine = true;

            while ((line = br.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }

                String[] columns = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                if (columns.length < 14) continue;

                String roadAddr = columns[3].replace("\"", "").trim();
                String lotAddr = columns[4].replace("\"", "").trim();
                String address = roadAddr.isEmpty() ? lotAddr : roadAddr;

                String purpose = columns[5].replace("\"", "").trim();
                String latStr = columns[12].replace("\"", "").trim();
                String lngStr = columns[13].replace("\"", "").trim();

                if (latStr.isEmpty() || lngStr.isEmpty() || address.isEmpty()) continue;

                // 위치 중복 제거 로직
                String locationKey = latStr + "_" + lngStr;
                if (!uniqueLocations.add(locationKey)) continue;

                CctvDto dto = new CctvDto();
                dto.setCctvId((long) (cachedCctvList.size() + 1));
                dto.setCctvName(address);
                dto.setLatitude(Double.parseDouble(latStr));
                dto.setLongitude(Double.parseDouble(lngStr));
                dto.setPurpose(purpose);

                cachedCctvList.add(dto);
            }
            br.close();
        } catch (Exception e) {
            System.err.println("🚨 CSV 파일 읽기 중 오류 발생!");
        }

        System.out.println("✅ CCTV 데이터 로딩 완료! 총 " + cachedCctvList.size() + "개의 위치가 메모리에 장착되었습니다.");
    }

    public List<CctvDto> getCctvData() {
        return cachedCctvList;
    }
}