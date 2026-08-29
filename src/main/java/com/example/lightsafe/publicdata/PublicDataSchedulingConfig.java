package com.example.lightsafe.publicdata;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 공공데이터 수집을 예약 실행하기 위한 설정.
 *
 * 매월 5일 자동 수집(@Scheduled)과, 서버 기동 직후 1회 수집(@Async)이
 * 동작하려면 이 두 기능이 켜져 있어야 합니다.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class PublicDataSchedulingConfig {
}
