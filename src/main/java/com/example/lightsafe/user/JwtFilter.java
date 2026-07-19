package com.example.lightsafe.user;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter
        extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    private final JwtAuthenticationEntryPoint
            authenticationEntryPoint;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        /*
         * DEBUG 로그가 활성화된 경우
         * 요청마다 필터 실행 횟수를 확인할 수 있습니다.
         */
        log.debug(
                "JwtFilter 실행: method={}, uri={}",
                request.getMethod(),
                request.getRequestURI()
        );

        String authorization =
                request.getHeader(
                        HttpHeaders.AUTHORIZATION
                );

        /*
         * Authorization 헤더가 아예 없는 경우:
         *
         * 공개 API라면 그대로 통과하고,
         * 보호 API라면 이후 Spring Security가
         * AuthenticationEntryPoint를 호출합니다.
         */
        if (authorization == null
                || authorization.isBlank()) {

            filterChain.doFilter(
                    request,
                    response
            );

            return;
        }

        /*
         * Authorization 헤더는 있지만
         * Bearer 형식이 아닌 경우입니다.
         */
        if (!authorization.startsWith("Bearer ")) {
            sendUnauthorized(
                    request,
                    response,
                    "Authorization 헤더는 Bearer 형식이어야 합니다."
            );

            return;
        }

        String token =
                authorization.substring(7).trim();

        if (token.isBlank()) {
            sendUnauthorized(
                    request,
                    response,
                    "인증 토큰이 비어 있습니다."
            );

            return;
        }

        try {
            Long userId =
                    jwtUtil.getUserIdFromToken(
                            token
                    );

            String role =
                    jwtUtil.getRoleFromToken(
                            token
                    );

            if (role == null
                    || role.isBlank()) {

                role = "USER";
            }

            /*
             * 다른 인증 필터가 먼저 인증을 저장한 경우
             * 기존 인증을 덮어쓰지 않습니다.
             */
            if (SecurityContextHolder
                    .getContext()
                    .getAuthentication() == null) {

                String authorityName =
                        role.startsWith("ROLE_")
                                ? role
                                : "ROLE_" + role;

                List<SimpleGrantedAuthority> authorities =
                        List.of(
                                new SimpleGrantedAuthority(
                                        authorityName
                                )
                        );

                UsernamePasswordAuthenticationToken
                        authentication =
                        new UsernamePasswordAuthenticationToken(
                                userId,
                                null,
                                authorities
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(request)
                );

                SecurityContextHolder
                        .getContext()
                        .setAuthentication(
                                authentication
                        );
            }

            filterChain.doFilter(
                    request,
                    response
            );

        } catch (ExpiredJwtException e) {
            SecurityContextHolder.clearContext();

            sendUnauthorized(
                    request,
                    response,
                    "인증 토큰이 만료되었습니다."
            );

        } catch (JwtException
                 | IllegalArgumentException e) {

            SecurityContextHolder.clearContext();

            sendUnauthorized(
                    request,
                    response,
                    "유효하지 않은 인증 토큰입니다."
            );
        }
    }

    private void sendUnauthorized(
            HttpServletRequest request,
            HttpServletResponse response,
            String message
    ) throws IOException, ServletException {

        request.setAttribute(
                JwtAuthenticationEntryPoint
                        .JWT_ERROR_MESSAGE,
                message
        );

        authenticationEntryPoint.commence(
                request,
                response,
                new BadCredentialsException(
                        message
                )
        );
    }
}