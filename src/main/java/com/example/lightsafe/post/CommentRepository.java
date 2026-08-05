package com.example.lightsafe.post;

// import jakarta.transaction.Transactional; 제거 추천 service랑 안맞음
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // ✅ 특정 게시글의 댓글 전체 (작성순)
    // user / parent 같이 당겨서 N+1 줄이기
    @EntityGraph(attributePaths = {"user", "parent"})
    List<Comment> findByPostPostIdOrderByCreatedAtAsc(Long postId);
    //좋아요 먼저 지우기와 마찬가지로 게시글 지울때 댓글이 fk로 막을수있음
    //댓글 먼저지우기
    @Modifying // 환경차 안전빵
    //@Transactional
    void deleteByPostPostId(Long postId);
    @Modifying(
            flushAutomatically = true,
            clearAutomatically = true
    )
    @Query("""
        UPDATE Comment comment
           SET comment.content = :deletedContent
         WHERE comment.user.userId = :userId
        """)
    int anonymizeCommentsByUserId(
            @Param("userId")
            Long userId,

            @Param("deletedContent")
            String deletedContent
    );
}
