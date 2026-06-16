package com.example.lightsafe.post;

import java.time.LocalDateTime;

public record PostListResponse(
        Long postId,
        String title,
        String category,
        Long userId,
        String nickname,
        Integer viewCount,
        Integer likeCount,
        Integer commentCount,
        LocalDateTime createdAt
) {
    public static PostListResponse from(Post post) {
        return new PostListResponse(
                post.getPostId(),
                post.getTitle(),
                post.getCategory(),
                post.getUser().getUserId(),
                post.getUser().getNickname(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getCreatedAt()
        );
    }
}
// 목록아이템(요약)용