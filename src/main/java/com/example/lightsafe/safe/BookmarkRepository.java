package com.example.lightsafe.safe;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookmarkRepository
        extends JpaRepository<Bookmark, Long> {

    List<Bookmark>
    findAllByUser_UserIdOrderByIdDesc(
            Long userId
    );

    Optional<Bookmark>
    findByIdAndUser_UserId(
            Long id,
            Long userId
    );
    @Modifying(flushAutomatically = true)
    @Query("""
        DELETE FROM Bookmark bookmark
         WHERE bookmark.user.userId = :userId
        """)
    int deleteAllByUserId(
            @Param("userId")
            Long userId
    );
}