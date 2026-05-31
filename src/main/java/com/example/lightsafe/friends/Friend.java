package com.example.lightsafe.friends;

import com.example.lightsafe.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "friends")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Friend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "friends_id")
    private Long friendsId;

    // 1. 친구 요청을 보낸 사람 (user_id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 2. 친구 요청을 받은 사람 (friend_user_id)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "friend_user_id", nullable = false)
    private User friendUser;

    // 3. 친구 상태 (대기/수락/거절)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendStatus status;

    // 4. 긴급 위치 공유 허용 여부 (기본값: false)
    @Column(name = "is_emergency_allowed", nullable = false)
    private boolean isEmergencyAllowed;

    // 5. 요청 생성 시간
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
