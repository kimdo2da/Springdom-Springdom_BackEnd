package com.example.lightsafe.post;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostAttachmentRepository
        extends JpaRepository<PostAttachment, Long> {

    List<PostAttachment>
    findByPostPostIdOrderByCreatedAtAsc(
            Long postId
    );

    void deleteByPostPostId(
            Long postId
    );

    @EntityGraph(
            attributePaths = {
                    "post",
                    "post.user"
            }
    )
    List<PostAttachment>
    findByPost_User_UserId(
            Long userId
    );

    @Modifying(flushAutomatically = true)
    @Query("""
            DELETE FROM PostAttachment attachment
             WHERE attachment.post.user.userId = :userId
            """)
    int deleteAllByPostAuthorId(
            @Param("userId")
            Long userId
    );
}