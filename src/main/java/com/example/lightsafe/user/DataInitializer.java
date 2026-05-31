package com.example.lightsafe.user; // 패키지 경로는 본인 프로젝트에 맞게 확인하세요

import com.example.lightsafe.user.User;
import com.example.lightsafe.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        // "admin"이라는 아이디가 없을 때만 생성 (서버 켤 때마다 중복 생성되는 것 방지)
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("1234")) // ⭐️ 여기서 안전하게 암호화!
                    .nickname("최고관리자")
                    .email("admin@example.com")
                    .phone("010-0000-0000")
                    .falseReportCount(0)
                    .isBlacklisted(false)
                    .role("ADMIN") // ⭐️ 관리자 권한 부여
                    .build();

            userRepository.save(admin);
            System.out.println("✅ 서버 시작: 최고관리자(admin) 계정이 자동 생성되었습니다.");
        }
    }
}