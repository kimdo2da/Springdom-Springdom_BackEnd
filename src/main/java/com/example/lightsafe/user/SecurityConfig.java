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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

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
     * 프론트엔드에서 백엔드 API를 호출할 수 있도록
     * CORS 허용 주소를 설정합니다.
     *
     * - localhost:5173
     *   로컬 Vite 개발 환경
     *
     * - safelight-two.vercel.app
     *   실제 Vercel 배포 환경
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration =
                new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of(
                        "http://localhost:5173",
                        "https://safelight-two.vercel.app"
                )
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "PATCH",
                        "DELETE",
                        "OPTIONS"
                )
        );

        /*
         * Authorization: Bearer ...
         * Content-Type: application/json
         * 등의 요청 헤더를 허용합니다.
         */
        configuration.setAllowedHeaders(
                List.of("*")
        );

        /*
         * 현재 JWT를 쿠키가 아니라
         * Authorization 헤더로 사용하므로 false로 둡니다.
         */
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
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
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {

        http
                /*
                 * Vercel 프론트엔드와
                 * localhost 프론트엔드의 요청을 허용합니다.
                 */
                .cors(cors ->
                        cors.configurationSource(
                                corsConfigurationSource
                        )
                )

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
                                /*
                                 * CORS preflight 요청 허용
                                 */
                                .requestMatchers(
                                        HttpMethod.OPTIONS,
                                        "/**"
                                )
                                .permitAll()

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
                                        "/security-lights",
                                        "/security-lights/**",
                                        "/police-facilities",
                                        "/police-facilities/**",
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