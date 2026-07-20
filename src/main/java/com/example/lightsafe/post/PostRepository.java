package com.example.lightsafe.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

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

    // 특정 유저가 쓴 글을 작성일 기준 내림차순(최신순)으로 조회
    List<Post> findByUser_UserIdOrderByCreatedAtDesc(Long userId);

    // 시작 시각 이상, 종료 시각 미만에 작성된 전체 게시글 수
    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            LocalDateTime startDateTime,
            LocalDateTime endDateTime
    );
}
