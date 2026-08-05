package com.example.lightsafe.post;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CommentCreateRequest(

        @NotBlank(
                message = "content는 필수입니다"
        )
        String content,

        /*
         * null이면 일반 댓글,
         * 값이 있으면 해당 댓글의 대댓글입니다.
         */
        Long parentId

) {
}