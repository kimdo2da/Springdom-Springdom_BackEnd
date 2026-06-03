package com.example.lightsafe.user;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtUtil jwtUtil;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // ... 앞부분 생략 ...
                .authorizeHttpRequests(auth -> auth
                                .requestMatchers(
                                        "/users/register",
                                        "/users/login",
                                        "/map.html",
                                        "/cctvs",
                                        "/cctvs/**",
                                        "/routes",
                                        "/bookmarks",
                                        "/bookmarks/**"  // 🔥 이 줄을 꼭 추가해 주세요! (뒤에 뭐가 오든 다 허용)
                                ).permitAll()
// ... 뒷부분 생략 ...
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}