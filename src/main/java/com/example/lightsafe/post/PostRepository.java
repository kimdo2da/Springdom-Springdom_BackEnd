package com.example.lightsafe.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    // ✅ 커뮤니티 목록(공지 제외) - 최신순은 Pageable에서 Sort로 처리
    @EntityGraph(attributePaths = {"user"})
    Page<Post> findByIsNoticeFalse(Pageable pageable);

    // ✅ 공지 3개 (최신순)
    @EntityGraph(attributePaths = {"user"})
    List<Post> findTop3ByIsNoticeTrueOrderByCreatedAtDesc();

    // ✅ 카테고리별 최신글 5개
    @EntityGraph(attributePaths = {"user"})
    List<Post> findTop5ByCategoryOrderByCreatedAtDesc(String category);

    // ✅ 카테고리별 페이징 목록
    @EntityGraph(attributePaths = {"user"})
    Page<Post> findByCategory(String category, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    Page<Post> findByTitleContainingIgnoreCase(String keyword, Pageable pageable);
}
