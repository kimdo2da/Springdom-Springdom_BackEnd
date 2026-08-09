package com.example.lightsafe.friends;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FriendRequestDto {

    private Long targetUserId; // 기존 방식 유지

    private String targetUsername; // 아이디(username)로 친구 요청
}