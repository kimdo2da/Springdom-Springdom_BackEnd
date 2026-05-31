package com.example.lightsafe.user;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component // 스프링(Spring)에게 "이 클래스는 네가 관리해줘!"라고 맡기는 표시입니다.
public class JwtUtil {

    // 1. 토큰의 위조를 막기 위한 '서버만의 비밀 도장(암호)'입니다.
    // 이 문자열이 유출되면 누구나 토큰을 만들 수 있으니 길고 복잡하게 설정해야 합니다.
    private final String SECRET = "MySuperSecretKeyForGraduationProjectMustBeLongEnough";

    // 2. 위의 문자열 도장을 암호화 알고리즘에 맞춰 진짜 '열쇠(Key)' 객체로 만듭니다.
    private final Key key = Keys.hmacShaKeyFor(SECRET.getBytes());

    // 3. 토큰(출입증)의 유효기간입니다. (1000밀리초 * 60초 * 60분 * 24시간 = 하루)
    private final long EXPIRATION_TIME = 1000 * 60 * 60 * 24;

    /**
     * [토큰 발급기]
     * 유저가 로그인에 성공했을 때, 그 유저의 '번호'와 '직급'을 담아 디지털 출입증(JWT)을 만들어줍니다.
     */
    public String generateToken(Long userId, String role) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId)) // 출입증의 주인 이름(Subject) 자리에 유저 번호를 적습니다.
                .claim("role", role) // claim은 '추가 정보'입니다. 여기에 직급(ADMIN 또는 USER)을 적어 넣습니다.
                .setIssuedAt(new Date()) // 토큰이 발급된 현재 시간을 기록합니다.
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME)) // 지금부터 24시간 뒤에 만료되도록 유통기한을 설정합니다.
                .signWith(key, SignatureAlgorithm.HS256) // 우리 서버의 비밀 열쇠로 '위조 방지 도장'을 꽝! 찍습니다.
                .compact(); // 이 모든 정보를 압축해서 하나의 긴 문자열(토큰)로 완성하여 돌려줍니다.
    }

    /**
     * [유저 번호 해독기]
     * 클라이언트(프론트엔드)가 보낸 출입증을 읽어서, 주인이 몇 번 유저인지 알아냅니다.
     */
    public Long getUserIdFromToken(String token) {
        String subject = Jwts.parserBuilder()
                .setSigningKey(key) // 우리 서버의 비밀 열쇠를 가져와서, 진짜 우리가 발급한 토큰이 맞는지 검사합니다.
                .build()
                .parseClaimsJws(token) // 토큰의 압축을 풀고 내용물을 엽니다. (이때 위조됐거나 만료됐으면 자동으로 에러가 터집니다)
                .getBody() // 내용물(Body)을 꺼냅니다.
                .getSubject(); // 발급할 때 세팅했던 유저 번호(Subject)를 가져옵니다.

        // 꺼낸 유저 번호는 문자열(String) 형태이므로, 숫자(Long)로 변환해서 돌려줍니다.
        return Long.parseLong(subject);
    }

    /**
     * [유저 직급 해독기]
     * 클라이언트가 보낸 출입증을 읽어서, 이 사람의 직급(ADMIN/USER)이 무엇인지 알아냅니다.
     */
    public String getRoleFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key) // 마찬가지로 비밀 열쇠로 유효성을 검증합니다.
                .build()
                .parseClaimsJws(token) // 토큰을 해독합니다.
                .getBody() // 내용물을 꺼냅니다.
                .get("role", String.class); // 발급할 때 'claim'에 넣었던 "role"이라는 이름의 정보를 문자열(String) 형태로 쏙 뽑아옵니다.
    }
}