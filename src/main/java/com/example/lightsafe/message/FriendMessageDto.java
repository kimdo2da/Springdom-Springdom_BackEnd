package com.example.lightsafe.message;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FriendMessageDto {

    @NotBlank(message = "쪽지 내용은 필수입니다.")
    private String content;
}