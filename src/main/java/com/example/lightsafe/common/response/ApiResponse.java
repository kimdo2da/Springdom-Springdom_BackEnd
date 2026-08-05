package com.example.lightsafe.common.response;

import com.example.lightsafe.common.exception.ErrorCode;

public final class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final String message;
    private final ApiError error;

    private ApiResponse(
            boolean success,
            T data,
            String message,
            ApiError error
    ) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.error = error;
    }

    public static <T> ApiResponse<T> ok(
            T data
    ) {
        return ok(
                data,
                "OK"
        );
    }

    public static <T> ApiResponse<T> ok(
            T data,
            String message
    ) {
        return new ApiResponse<>(
                true,
                data,
                message,
                null
        );
    }

    public static <T> ApiResponse<T> fail(
            ErrorCode errorCode
    ) {
        return fail(
                errorCode,
                errorCode.getDefaultMessage()
        );
    }

    public static <T> ApiResponse<T> fail(
            ErrorCode errorCode,
            String message
    ) {
        return fail(
                errorCode.getCode(),
                message
        );
    }

    public static <T> ApiResponse<T> fail(
            String code,
            String message
    ) {
        return new ApiResponse<>(
                false,
                null,
                null,
                new ApiError(
                        code,
                        message
                )
        );
    }

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public String getMessage() {
        return message;
    }

    public ApiError getError() {
        return error;
    }
}