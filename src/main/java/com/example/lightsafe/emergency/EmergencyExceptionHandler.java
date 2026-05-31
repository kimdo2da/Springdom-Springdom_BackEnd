package com.example.lightsafe.emergency;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.example.lightsafe.emergency")
public class EmergencyExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<EmergencyApiErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        String message = e.getMessage() == null ? "잘못된 요청입니다." : e.getMessage();

        if (message.contains("존재하지")) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(EmergencyApiErrorResponse.of(404, message));
        }

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(EmergencyApiErrorResponse.of(400, message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<EmergencyApiErrorResponse> handleException(Exception e) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(EmergencyApiErrorResponse.of(500, "서버 오류"));
    }
}