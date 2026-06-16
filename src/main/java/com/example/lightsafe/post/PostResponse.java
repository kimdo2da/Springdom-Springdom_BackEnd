package com.example.lightsafe.post;

import com.example.lightsafe.post.Post;
//import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

//@Schema(description = "게시글 응답")
public record PostResponse(
  //      @Schema(description = "게시글 ID", example = "1")
        Long postId,

    //    @Schema(description = "게시글 제목", example = "Spring Boot 학습 후기")
        String title,

      //  @Schema(description = "게시글 내용", example = "Spring Boot를 이용해 REST API를 만들어보았습니다.")
        String content,

       // @Schema(description = "작성자 닉네임", example = "개발자123")
        String authorName,

        //@Schema(description = "작성자 사용자 ID", example = "1")
        Long userId,

       // @Schema(description = "댓글 수", example = "5")
        Integer commentCount,

    //    @Schema(description = "조회수", example = "100")
        Integer viewCount,

     //   @Schema(description = "좋아요 수", example = "15")
        Integer likeCount,

     //   @Schema(description = "작성 시간", example = "2024-08-16T12:30:00")
        LocalDateTime createdAt
) {
    public static PostResponse from(Post post) {
        return new PostResponse(
                post.getPostId(),
                post.getTitle(),
                post.getContent(),
                post.getUser().getNickname(),
                post.getUser().getUserId(),
                post.getCommentCount(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getCreatedAt()
        );
    }
}
// 상세(내용 포함)용