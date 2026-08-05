package com.example.lightsafe.safe;

import com.example.lightsafe.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/bookmarks")
@CrossOrigin(origins = "*")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    @PostMapping
    public ResponseEntity<
            ApiResponse<BookmarkResponse>
            > saveBookmark(
            @RequestBody
            @Valid
            BookmarkRequestDto request
    ) {
        BookmarkResponse data =
                bookmarkService.saveBookmark(
                        request
                );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        data,
                        "북마크가 성공적으로 저장되었습니다."
                )
        );
    }

    @GetMapping
    public ResponseEntity<
            ApiResponse<List<BookmarkResponse>>
            > getMyBookmarks() {

        List<BookmarkResponse> data =
                bookmarkService.getMyBookmarks();

        return ResponseEntity.ok(
                ApiResponse.ok(
                        data,
                        "내 북마크 목록 조회 성공"
                )
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>>
    deleteBookmark(
            @PathVariable Long id
    ) {
        bookmarkService.deleteMyBookmark(
                id
        );

        return ResponseEntity.ok(
                ApiResponse.ok(
                        null,
                        "북마크가 성공적으로 삭제되었습니다."
                )
        );
    }
}