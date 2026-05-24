package com.example.lightsafe.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter // 1. 모든 Getter 메서드를 알아서 만들어 줍니다!
@Setter // 2. 모든 Setter 메서드를 알아서 만들어 줍니다!
@NoArgsConstructor // 3. 텅 빈 기본 생성자 `public User() {}` 를 알아서 만들어 줍니다!
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    private String username;
    private String email;
    private String password;
    private String nickname;
    private String bio;
    private String githubUrl;
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ⭐️ UserController에서 사용 중인 회원가입용 맞춤 생성자는 그대로 둡니다.
    // (롬복이 모든 필드를 다 넣은 생성자를 만들 수는 있지만, 우리가 원하는 딱 이 모양은 아니기 때문입니다!)
    public User(Long userId, String username, String email, String password, String nickname) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.password = password;
        this.nickname = nickname;
    }

    // ⭐️ 정보 수정 로직은 단순한 Setter가 아니라 '선택적 변경' 로직이므로 남겨둡니다.
    public void updateInfo(String password, String nickname, String bio, String githubUrl) {
        if(password != null) this.password = password;
        if(nickname != null) this.nickname = nickname;
        if(bio != null) this.bio = bio;
        if(githubUrl != null) this.githubUrl = githubUrl;
    }
}