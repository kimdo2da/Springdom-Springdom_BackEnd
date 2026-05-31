package com.example.lightsafe.post;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostApiResponse<T> {
    private boolean success;
    private T data;
    private String message;

    public static <T> PostApiResponse<T> ok(T data) {
        return new PostApiResponse<>(true, data, "OK");
    }

    public static <T> PostApiResponse<T> ok(T data, String message) {
        return new PostApiResponse<>(true, data, message);
    }
}
