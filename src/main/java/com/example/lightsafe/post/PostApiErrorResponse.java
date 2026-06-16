package com.example.lightsafe.post;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostApiErrorResponse {
    private boolean success;
    private ErrorBody error;

    @Getter
    @AllArgsConstructor
    public static class ErrorBody {
        private int code;
        private String message;
    }

    public static PostApiErrorResponse of(int code, String message) {
        return new PostApiErrorResponse(false, new ErrorBody(code, message));
    }
}
