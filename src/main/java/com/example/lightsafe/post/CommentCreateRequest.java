package com.example.lightsafe.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CommentCreateRequest(
        @NotBlank(message = "content는 필수입니다")
        String content,

        // null이면 일반 댓글, 값 있으면 대댓글
        Long parentId,

        @NotNull(message = "userId는 필수입니다")
        Long userId
) {}
