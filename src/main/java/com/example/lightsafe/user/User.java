package com.example.lightsafe.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users") // 메모에 작성된 테이블 이름과 매칭
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 255)
    private String password;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(length = 20)
    private String phone; // 새로 추가됨

    @Column(name = "false_report_count", nullable = false)
    @ColumnDefault("0")
    private int falseReportCount = 0; // 새로 추가됨

    @Column(name = "is_blacklisted", nullable = false)
    @ColumnDefault("0")
    private boolean isBlacklisted = false; // 새로 추가됨 (TINYINT 매핑)

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; // 자동 생성 시간

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt; // 자동 수정 시간

    @Builder.Default
    @Column(
            nullable = false,
            length = 20
    )
    private String role = "USER";

    @Builder.Default
    @Column(
            name = "is_deleted",
            nullable = false
    )
    @ColumnDefault("0")
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt; // 기본값 USER (관리자는 직접 DB에서 'ADMIN'으로 수정)
}