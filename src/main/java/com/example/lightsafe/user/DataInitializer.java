package com.example.lightsafe.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("1234"))
                    .nickname("최고관리자")
                    .email("admin@example.com")
                    .phone("010-0000-0000")
                    .falseReportCount(0)
                    .isBlacklisted(false)
                    .role("ADMIN")
                    .deleted(false)
                    .build();

            userRepository.save(admin);

            log.info("서버 시작: 최고관리자(admin) 계정이 자동 생성되었습니다.");
        }
    }
}