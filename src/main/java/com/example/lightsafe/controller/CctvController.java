package com.example.lightsafe.controller;

import com.example.lightsafe.dto.ApiResponse; // 🔥 1. 새롭게 만든 공통 응답 DTO 임포트
import com.example.lightsafe.dto.CctvDto;
import com.example.lightsafe.service.CctvService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CctvController {

    private final CctvService cctvService;

    // 스프링이 Service를 자동으로 연결해주는 생성자입니다.
    public CctvController(CctvService cctvService) {
        this.cctvService = cctvService;
    }

    // 🔥 2. 명세서에 맞게 엔드포인트 주소를 /cctv -> /cctvs 로 변경합니다.
    @GetMapping("/cctvs")
    public ApiResponse<List<CctvDto>> getCctvs() {

        // 서비스에서 리스트 데이터를 먼저 가져옵니다.
        List<CctvDto> data = cctvService.getCctvData();

        // 🔥 3. 가져온 데이터를 ApiResponse에 담아서 반환합니다. (성공여부, 데이터, 메시지)
        return new ApiResponse<>(true, data, "CCTV 전체 조회 성공");
    }
}