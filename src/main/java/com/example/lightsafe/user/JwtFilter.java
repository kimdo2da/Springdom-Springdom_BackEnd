package com.example.lightsafe.user;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 방문객이 가져온 출입증(Header)을 확인합니다.
        String authorization = request.getHeader("Authorization");

        // 2. 출입증이 있고, "Bearer "로 정상적으로 시작한다면 검사를 시작합니다.
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7); // "Bearer " 글자를 떼어내고 순수 토큰만 남깁니다.

            try {
                // 3. 우리가 만든 해독기(JwtUtil)를 써서 유저 번호와 직급을 알아냅니다.
                Long userId = jwtUtil.getUserIdFromToken(token);
                String role = jwtUtil.getRoleFromToken(token); // ⭐️ 새롭게 추가된 부분!

                // 방어 코드: 옛날에 발급받은 토큰이라서 role 정보가 없다면 기본값인 "USER"로 취급합니다.
                if (role == null) {
                    role = "USER";
                }

                // 4. ⭐️ 스프링 시큐리티의 절대 규칙: 권한 이름 앞에는 반드시 "ROLE_"을 붙여야 합니다.
                // (예: ADMIN -> ROLE_ADMIN, USER -> ROLE_USER)
                List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));

                // 5. 검사를 무사히 통과했으니, 건물 출입 장부(SecurityContext)에 "이 사람 O번 유저고, 직급은 OOO입니다" 라고 쾅쾅 도장을 찍어줍니다.
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (Exception e) {
                // 토큰이 만료되었거나(ExpiredJwtException) 위조되었다면 출입 장부에 적지 않고 쫓아냅니다.
                System.out.println("🚨 [JwtFilter] 토큰 해독 중 에러 발생: " + e.getMessage());
            }
        }

        // 6. 다음 안내데스크(Controller)로 방문객을 넘겨줍니다.
        filterChain.doFilter(request, response);
    }
}