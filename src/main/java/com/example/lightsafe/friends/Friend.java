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

    // 친구 요청을 보낸 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 친구 요청을 받은 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "friend_user_id", nullable = false)
    private User friendUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FriendStatus status;

    /*
     * user 사용자가 friendUser 사용자에게
     * 자기 긴급 위치를 공유할지 여부
     */
    @Builder.Default
    @Column(
            name = "user_emergency_allowed",
            nullable = false
    )
    private boolean userEmergencyAllowed = false;

    /*
     * friendUser 사용자가 user 사용자에게
     * 자기 긴급 위치를 공유할지 여부
     */
    @Builder.Default
    @Column(
            name = "friend_user_emergency_allowed",
            nullable = false
    )
    private boolean friendUserEmergencyAllowed = false;

    @CreationTimestamp
    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;
}