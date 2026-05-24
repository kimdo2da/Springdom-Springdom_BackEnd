package com.example.lightsafe.controller;

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

    // 👈 이 부분이 질문자님이 요청하신 리스트 반환 변경 부분입니다!
    @GetMapping("/cctv")
    public List<CctvDto> getCctv() {
        return cctvService.getCctvData();
    }
}
