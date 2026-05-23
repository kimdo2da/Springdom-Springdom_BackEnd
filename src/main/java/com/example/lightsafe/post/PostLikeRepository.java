package com.example.lightsafe.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {

    boolean existsByPostPostIdAndUserUserId(Long postId, Long userId);

    void deleteByPostPostIdAndUserUserId(Long postId, Long userId);

    //게시글 삭제할때 FK충돌 대비 posts를 지울때 likes에 FK남아있으면 db가 막음 즉 게시글 삭제 전 좋아요 먼저 정리
    void deleteByPostPostId(Long postId);
}
