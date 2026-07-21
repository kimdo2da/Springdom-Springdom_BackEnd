package com.example.lightsafe.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {

    boolean existsByPostPostIdAndUserUserId(Long postId, Long userId);

    void deleteByPostPostIdAndUserUserId(Long postId, Long userId);

    //게시글 삭제할때 FK충돌 대비 posts를 지울때 likes에 FK남아있으면 db가 막음 즉 게시글 삭제 전 좋아요 먼저 정리
    void deleteByPostPostId(Long postId);
    @Query("""
        SELECT postLike.post.postId
          FROM PostLike postLike
         WHERE postLike.user.userId = :userId
        """)
    List<Long> findLikedPostIdsByUserId(
            @Param("userId")
            Long userId
    );

    @Modifying(flushAutomatically = true)
    @Query("""
        DELETE FROM PostLike postLike
         WHERE postLike.user.userId = :userId
        """)
    int deleteAllByUserId(
            @Param("userId")
            Long userId
    );

    long countByPost_PostId(
            Long postId
    );
}
