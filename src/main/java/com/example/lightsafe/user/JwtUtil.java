package com.example.lightsafe.user;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {

    private final String SECRET = "MySuperSecretKeyForGraduationProjectMustBeLongEnough";
    private final Key key = Keys.hmacShaKeyFor(SECRET.getBytes());
    private final long EXPIRATION_TIME = 1000 * 60 * 60;

    public String generateToken(Long userId, String username) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Long extractUserId(String token) {
        return Long.parseLong(Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject());
    }

    public Long getUserIdFromToken(String token) {
        // 1. 토큰을 파싱해서 Subject(우리가 userId를 넣었던 곳)를 꺼냄
        String subject = Jwts.parserBuilder()
                .setSigningKey(key) // 여기서 key는 generateToken에서 쓴 것과 동일한 변수
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject(); // ⭐️ 여기서 userId를 꺼냅니다!

        // 2. String으로 되어 있는 subject를 Long으로 변환
        return Long.parseLong(subject);
    }
}