package com.example.lightsafe.safe;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class SecurityLightService {

    private final List<LocationDto> cachedLights = new ArrayList<>();

    @PostConstruct
    public void initSecurityLightData() {
        log.info("서버 시작 중 여러 구청의 보안등 CSV 데이터들을 로딩합니다.");

        Set<String> uniqueLocations = new HashSet<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        try {
            // 💡 src/main/resources/security_lights 폴더 안의 모든 .csv 파일을 찾아옵니다!
            Resource[] resources = resolver.getResources("classpath:security_lights/*.csv");

            for (Resource resource : resources) {
                int fileLightCount = 0;

                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                    String headerLine = br.readLine();
                    if (headerLine == null) continue;

                    // UTF-8 BOM(눈에 보이지 않는 특수문자) 제거
                    if (headerLine.startsWith("\uFEFF")) {
                        headerLine = headerLine.substring(1);
                    }

                    // 헤더에서 '위도'와 '경도'가 몇 번째 칸에 있는지 자동으로 찾습니다.
                    String[] headers = headerLine.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
                    int latIdx = -1;
                    int lonIdx = -1;

                    for (int i = 0; i < headers.length; i++) {
                        String h = headers[i].replace("\"", "").trim();
                        if (h.equals("위도")) latIdx = i;
                        if (h.equals("경도")) lonIdx = i;
                    }

                    // 위도, 경도 컬럼이 없는 이상한 파일이면 건너뜁니다.
                    if (latIdx == -1 || lonIdx == -1) {
                        log.warn("{} 파일에 위도/경도 컬럼이 없어 건너뜁니다.", resource.getFilename());
                        continue;
                    }

                    String line;
                    while ((line = br.readLine()) != null) {
                        String[] columns = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

                        // 데이터 칸 수가 부족하면 건너뜀
                        if (columns.length <= Math.max(latIdx, lonIdx)) continue;

                        try {
                            String latStr = columns[latIdx].replace("\"", "").trim();
                            String lngStr = columns[lonIdx].replace("\"", "").trim();

                            if (latStr.isEmpty() || lngStr.isEmpty()) continue;

                            double lat = Double.parseDouble(latStr);
                            double lon = Double.parseDouble(lngStr);

                            // 유효한 좌표라면 캐싱 (중복 위치는 필터링)
                            if (lat != 0.0 && lon != 0.0) {
                                String locationKey = lat + "_" + lon;
                                if (uniqueLocations.add(locationKey)) {
                                    cachedLights.add(new LocationDto(lat, lon));
                                    fileLightCount++;
                                }
                            }
                        } catch (NumberFormatException e) {
                            // 위경도가 숫자가 아닌 오류 데이터 줄은 조용히 무시
                            continue;
                        }
                    }
                } catch (Exception e) {
                    log.error("{} 파일 처리 중 오류 발생", resource.getFilename(), e);
                }
                log.info("✅ {} 파일 로딩 완료 (보안등 {}개 추가)", resource.getFilename(), fileLightCount);
            }

        } catch (Exception e) {
            log.error("보안등 폴더(security_lights) 접근 중 오류가 발생했습니다.", e);
        }

        log.info("🌟 보안등 데이터 최종 로딩 완료! 총 {}개의 보안등이 메모리에 장착되었습니다.", cachedLights.size());
    }

    public List<LocationDto> getSecurityLightData() {
        return cachedLights;
    }
}