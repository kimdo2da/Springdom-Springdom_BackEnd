package com.example.lightsafe.safe;

import com.example.lightsafe.common.exception.NotFoundException;
import com.example.lightsafe.user.CurrentUserService;
import com.example.lightsafe.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final CurrentUserService currentUserService;

    @Transactional
    public BookmarkResponse saveBookmark(
            BookmarkRequestDto request
    ) {
        User loginUser =
                currentUserService.getCurrentUser();

        Bookmark bookmark =
                new Bookmark();

        bookmark.setUser(
                loginUser
        );
        bookmark.setRouteName(
                request.getRouteName().trim()
        );
        bookmark.setStartLatitude(
                request.getStartLatitude()
        );
        bookmark.setStartLongitude(
                request.getStartLongitude()
        );
        bookmark.setEndLatitude(
                request.getEndLatitude()
        );
        bookmark.setEndLongitude(
                request.getEndLongitude()
        );
        bookmark.setSafetyScore(
                request.getSafetyScore()
        );

        Bookmark savedBookmark =
                bookmarkRepository.save(
                        bookmark
                );

        return BookmarkResponse.from(
                savedBookmark
        );
    }

    public List<BookmarkResponse>
    getMyBookmarks() {

        Long loginUserId =
                currentUserService.getCurrentUserId();

        return bookmarkRepository
                .findAllByUser_UserIdOrderByIdDesc(
                        loginUserId
                )
                .stream()
                .map(
                        BookmarkResponse::from
                )
                .toList();
    }

    @Transactional
    public void deleteMyBookmark(
            Long bookmarkId
    ) {
        Long loginUserId =
                currentUserService.getCurrentUserId();

        Bookmark bookmark =
                bookmarkRepository
                        .findByIdAndUser_UserId(
                                bookmarkId,
                                loginUserId
                        )
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "존재하지 않거나 삭제 권한이 없는 북마크입니다. id="
                                                + bookmarkId
                                )
                        );

        bookmarkRepository.delete(
                bookmark
        );
    }
}