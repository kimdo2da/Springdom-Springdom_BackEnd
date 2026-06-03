package com.example.lightsafe.safe;

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
    public ResponseEntity<ApiResponse> saveBookmark(@RequestBody BookmarkRequestDto request) {
        Bookmark bookmark = new Bookmark();
        bookmark.setRouteName(request.getRouteName());
        // 💡 통일된 변수명 적용
        bookmark.setStartLatitude(request.getStartLatitude());
        bookmark.setStartLongitude(request.getStartLongitude());
        bookmark.setEndLatitude(request.getEndLatitude());
        bookmark.setEndLongitude(request.getEndLongitude());
        bookmark.setSafetyScore(request.getSafetyScore());

        bookmarkRepository.save(bookmark);

        return ResponseEntity.ok(new ApiResponse<>(true, null, "북마크가 성공적으로 저장되었습니다."));
    }

    @GetMapping("/bookmarks")
    public ResponseEntity<ApiResponse<List<Bookmark>>> getAllBookmarks() {
        // 💡 규칙 위반 수정: ApiResponse 봉투에 예쁘게 담아서 반환!
        List<Bookmark> list = bookmarkRepository.findAll();
        return ResponseEntity.ok(new ApiResponse<>(true, list, "북마크 목록 조회 성공"));
    }

    @DeleteMapping("/bookmarks/{id}")
    public ResponseEntity<ApiResponse> deleteBookmark(@PathVariable Long id) {
        try {
            bookmarkRepository.deleteById(id);
            return ResponseEntity.ok(new ApiResponse<>(true, null, "북마크가 성공적으로 삭제되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new ApiResponse<>(false, null, "삭제 중 오류가 발생했습니다."));
        }
    }
}