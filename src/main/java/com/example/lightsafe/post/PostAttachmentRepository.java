package com.example.lightsafe.post;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostAttachmentRepository extends JpaRepository<PostAttachment, Long> {
    List<PostAttachment> findByPostPostIdOrderByCreatedAtAsc(Long postId);
    void deleteByPostPostId(Long postId);
}
