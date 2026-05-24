package com.example.lightsafe.config;

import com.example.lightsafe.util.JwtUtil; // ⭐️ 추가됨
import lombok.RequiredArgsConstructor; // ⭐️ 추가됨
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter; // ⭐️ 추가됨

@Configuration
@RequiredArgsConstructor // ⭐️ 1. 스프링에게 "내가 필요한 부품 알아서 챙겨와!" 라고 명령합니다.
public class SecurityConfig {

    private final JwtUtil jwtUtil; // ⭐️ 2. 문지기에게 쥐여줄 해독기(JwtUtil)를 준비합니다.

    // 1. 비밀번호 분쇄기(BCrypt)를 스프링에 등록해 줍니다.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 2. 시큐리티 설정
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // 포스트맨 테스트용
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/users/register", "/users/login").permitAll() // 가입, 로그인은 아무나 통과!
                        .anyRequest().authenticated() // 나머지는 전부 팔찌(토큰) 검사해!
                )
                // ⭐️ 3. 해결: 이제 빈손이 아니라 해독기(jwtUtil)를 쥐여주고 문지기를 세웁니다!
                .addFilterBefore(new JwtFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}