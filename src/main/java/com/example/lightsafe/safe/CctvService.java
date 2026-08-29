package com.example.lightsafe.safe;

import com.example.lightsafe.emergency.Cctv;
import com.example.lightsafe.emergency.CctvRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * CCTV 조회.
 *
 * 예전에는 서울시 CSV 한 개를 서버 기동 때 메모리에 올려 두고 4만 건을 통째로 내려줬습니다.
 * 전국 데이터로 바꾸면서 25만 건이 되어, 지금은 표에 저장해 두고
 * 화면 범위만큼만 잘라서 꺼냅니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CctvService {

    private static final int MAX_RESULT_SIZE = 20000;

    private final CctvRepository cctvRepository;

    @Transactional(readOnly = true)
    public List<CctvDto> getCctvsInBounds(
            MapBounds bounds
    ) {
        List<Cctv> cctvs =
                cctvRepository.findInBounds(
                        bounds.minLatitude(),
                        bounds.maxLatitude(),
                        bounds.minLongitude(),
                        bounds.maxLongitude(),
                        PageRequest.of(0, MAX_RESULT_SIZE)
                );

        List<CctvDto> results = new ArrayList<>(cctvs.size());

        for (Cctv cctv : cctvs) {
            CctvDto dto = new CctvDto();

            dto.setCctvId(cctv.getCctvId());
            dto.setCctvName(
                    cctv.getAddress() != null
                            ? cctv.getAddress()
                            : cctv.getCctvName()
            );
            dto.setLatitude(cctv.getLatitude().doubleValue());
            dto.setLongitude(cctv.getLongitude().doubleValue());
            dto.setPurpose(cctv.getPurpose());

            results.add(dto);
        }

        return results;
    }

    /**
     * 경로 주변 탐색용. 좌표만 필요해 가볍게 꺼냅니다.
     */
    @Transactional(readOnly = true)
    public List<LocationDto> getCctvLocationsForRoute(
            MapBounds bounds
    ) {
        return cctvRepository.findLocationsInBounds(
                bounds.minLatitude(),
                bounds.maxLatitude(),
                bounds.minLongitude(),
                bounds.maxLongitude(),
                PageRequest.of(0, MAX_RESULT_SIZE * 5)
        );
    }

    @Transactional(readOnly = true)
    public long countAll() {
        return cctvRepository.count();
    }
}
