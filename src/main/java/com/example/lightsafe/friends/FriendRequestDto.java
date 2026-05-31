package com.example.lightsafe.friends;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FriendRequestDto {
    private Long targetUserId; // 친구 요청을 보낼 대상의 유저 ID
}