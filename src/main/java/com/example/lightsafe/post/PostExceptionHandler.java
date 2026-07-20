package com.example.lightsafe.post;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;

// ✅ post 패키지에만 적용되도록 제한
@RestControllerAdvice(basePackages = "com.example.lightsafe.post")
public class PostExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<PostApiErrorResponse>
    handleAccessDeniedException(
            AccessDeniedException e
    ) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(
                        PostApiErrorResponse.of(
                                403,
                                "관리자 권한이 필요합니다."
                        )
                );
    }
    // 404: "존재하지 않는 ~" 류 메시지면 Not Found로 내려줌
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<PostApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        String msg = (e.getMessage() == null) ? "" : e.getMessage();

        // 너가 지금 서비스에서 던지는 메시지 패턴에 맞춰서 최소 분기
        if (msg.contains("존재하지") || msg.contains("not found")) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(PostApiErrorResponse.of(404, msg));
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(PostApiErrorResponse.of(400, msg));
    }
    //403 오류 권한 문제
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<PostApiErrorResponse>
    handleSecurityException(SecurityException e) {
        String message = e.getMessage() == null
                ? "접근 권한이 없습니다."
                : e.getMessage();
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(
                        PostApiErrorResponse.of(
                                403,
                                message
                        )
                );
    }

    // 500: 그 외 전부 서버 오류
    @ExceptionHandler(Exception.class)
    public ResponseEntity<PostApiErrorResponse> handleServerError(Exception e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(PostApiErrorResponse.of(500, "서버 오류"));
    }
}
//존재하지 않는 게시글 404 그외 400 나머지 500