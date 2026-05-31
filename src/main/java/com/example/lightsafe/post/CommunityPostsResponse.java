package com.example.lightsafe.post;

import java.util.List;

public record CommunityPostsResponse(
        List<PostListResponse> notices,  // 공지 3개 고정
        List<PostListResponse> items,    // 일반글 페이지
        PostPageInfo pageInfo
) {}
