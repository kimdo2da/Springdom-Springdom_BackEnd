package com.example.lightsafe.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    // 1. 서버 전용 특수 비밀키 (최소 32바이트 이상)
    private final String SECRET = "MySuperSecretKeyForGraduationProjectMustBeLongEnough";
    private final Key key = Keys.hmacShaKeyFor(SECRET.getBytes());

    // 2. 토큰 유효 시간 (1시간)
    private final long EXPIRATION_TIME = 1000 * 60 * 60;

    // 3. 유저 정보를 담은 JWT 토큰 생성
    public String generateToken(Long userId, String username) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId)) // 유저 고유 번호 저장
                .claim("username", username)        // 유저 이름 저장
                .setIssuedAt(new Date())            // 토큰 발급 시간
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME)) // 토큰 만료 시간
                .signWith(key, SignatureAlgorithm.HS256) // 비밀키로 암호화
                .compact();
    }

    // 4. 토큰 위조 검사 및 유저 번호 추출
    public Long extractUserId(String token) {
        return Long.parseLong(Jwts.parserBuilder()
                .setSigningKey(key)         // 비밀키로 검증
                .build()
                .parseClaimsJws(token)      // 포장지 해독
                .getBody()
                .getSubject());             // 유저 번호 반환
    }
}