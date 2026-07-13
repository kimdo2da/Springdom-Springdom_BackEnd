package com.example.lightsafe.post;

//import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

//@Schema(description = "게시글 작성 요청")
public record PostCreateRequest(
    //    @Schema(description = "게시글 제목", example = "Spring Boot 학습 후기")
        @NotBlank(message = "제목은 필수입니다")
        String title,

   //     @Schema(description = "게시글 내용", example = "Spring Boot를 이용해 REST API를 만들어보았습니다.")
        @NotBlank(message = "내용은 필수입니다")
        String content,

  //      @Schema(description = "카테고리 (NOTICE, QUESTION, INFO)", example = "INFO")
        String category
) {
}
