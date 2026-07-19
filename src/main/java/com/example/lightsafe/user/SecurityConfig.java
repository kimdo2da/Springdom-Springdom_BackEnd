package com.example.lightsafe.user;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;

    private final JwtAuthenticationEntryPoint
            authenticationEntryPoint;

    private final JwtAccessDeniedHandler
            accessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * JwtFilter가 일반 서블릿 필터와
     * Spring Security 필터로 중복 등록되는 것을 방지합니다.
     */
    @Bean
    public FilterRegistrationBean<JwtFilter>
    jwtFilterRegistration(
            JwtFilter jwtFilter
    ) {
        FilterRegistrationBean<JwtFilter> registration =
                new FilterRegistrationBean<>(jwtFilter);

        registration.setEnabled(false);

        return registration;
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http
    ) throws Exception {

        http
                // JWT 방식이므로 CSRF 비활성화
                .csrf(csrf ->
                        csrf.disable()
                )

                // 서버 세션을 사용하지 않음
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // Spring 기본 로그인 화면 사용 안 함
                .formLogin(formLogin ->
                        formLogin.disable()
                )

                // HTTP Basic 인증 사용 안 함
                .httpBasic(httpBasic ->
                        httpBasic.disable()
                )

                // 인증 실패 401, 권한 부족 403 공통 처리
                .exceptionHandling(exception ->
                        exception
                                .authenticationEntryPoint(
                                        authenticationEntryPoint
                                )
                                .accessDeniedHandler(
                                        accessDeniedHandler
                                )
                )

                .authorizeHttpRequests(auth ->
                        auth
                                // 회원가입·로그인 공개
                                .requestMatchers(
                                        "/users/register",
                                        "/users/login"
                                )
                                .permitAll()

                                // 지도·CCTV·경로 공개
                                .requestMatchers(
                                        "/map.html",
                                        "/cctvs",
                                        "/cctvs/**",
                                        "/routes"
                                )
                                .permitAll()

                                /*
                                 * 공개 위험구역 조회
                                 *
                                 * GET /danger-zones
                                 * GET /danger-zones/{dangerZoneId}
                                 *
                                 * /danger-zones/{id}/reports는
                                 * 두 단계 주소라 여기에 포함되지 않습니다.
                                 */
                                .requestMatchers(
                                        HttpMethod.GET,
                                        "/danger-zones",
                                        "/danger-zones/*"
                                )
                                .permitAll()

                                // 그 외 API는 로그인 필요
                                .anyRequest()
                                .authenticated()
                )

                /*
                 * 직접 new JwtFilter(...) 하지 않고
                 * Spring이 관리하는 JwtFilter Bean을 등록합니다.
                 */
                .addFilterBefore(
                        jwtFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}