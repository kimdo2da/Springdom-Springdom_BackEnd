package com.example.lightsafe.post;

import java.time.LocalDateTime;
import java.util.List;

public record PostDetailResponse(
        Long postId,
        String title,
        String content,
        String category,
        Long userId,
        String nickname,
        Integer viewCount,
        Integer likeCount,
        Integer commentCount,
        Boolean isLiked,
        LocalDateTime createdAt,
        List<CommentResponse> comments,
        List<AttachmentResponse> attachments
) {}

//댓글 포함 상세용
