package com.example.lightsafe.safe;

import com.example.lightsafe.common.exception.NotFoundException;
import com.example.lightsafe.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class BookmarkController {

    private final BookmarkRepository bookmarkRepository;

    @PostMapping("/bookmarks")
    public ResponseEntity<ApiResponse<Void>>
    saveBookmark(
            @RequestBody BookmarkRequestDto request
    ) {
        Bookmark bookmark = new Bookmark();

        bookmark.setRouteName(
                request.getRouteName()
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

        bookmarkRepository.save(
                bookmark
        );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        null,
                        "북마크가 성공적으로 저장되었습니다."
                )
        );
    }

    @GetMapping("/bookmarks")
    public ResponseEntity<
            ApiResponse<List<Bookmark>>
            > getAllBookmarks() {

        List<Bookmark> list =
                bookmarkRepository.findAll();

        return ResponseEntity.ok(
                ApiResponse.ok(
                        list,
                        "북마크 목록 조회 성공"
                )
        );
    }

    @DeleteMapping("/bookmarks/{id}")
    public ResponseEntity<ApiResponse<Void>>
    deleteBookmark(
            @PathVariable Long id
    ) {
        if (!bookmarkRepository.existsById(id)) {
            throw new NotFoundException(
                    "존재하지 않는 북마크입니다. id=" + id
            );
        }

        bookmarkRepository.deleteById(id);

        return ResponseEntity.ok(
                ApiResponse.ok(
                        null,
                        "북마크가 성공적으로 삭제되었습니다."
                )
        );
    }
}