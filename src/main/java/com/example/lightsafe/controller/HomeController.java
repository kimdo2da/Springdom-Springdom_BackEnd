package com.example.lightsafe.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

@RestController
public class HomeController {

    @GetMapping("/")
    public Resource home() {
        // templates 폴더 안의 index.html 파일을 직접 반환합니다.
        return new ClassPathResource("templates/index.html");
    }
}
