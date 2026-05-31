package com.example.lightsafe.post;

import java.util.List;

public record PostListPageResponse(
        List<PostListResponse> items,
        PostPageInfo pageInfo
) {}
//목록+pageinfo