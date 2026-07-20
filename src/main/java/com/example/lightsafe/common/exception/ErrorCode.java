package com.example.lightsafe.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    BAD_REQUEST(
            HttpStatus.BAD_REQUEST,
            "잘못된 요청입니다."
    ),

    VALIDATION_ERROR(
            HttpStatus.BAD_REQUEST,
            "요청 값 검증에 실패했습니다."
    ),

    UNAUTHORIZED(
            HttpStatus.UNAUTHORIZED,
            "로그인이 필요합니다."
    ),

    FORBIDDEN(
            HttpStatus.FORBIDDEN,
            "해당 요청을 실행할 권한이 없습니다."
    ),

    NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "요청한 데이터를 찾을 수 없습니다."
    ),

    CONFLICT(
            HttpStatus.CONFLICT,
            "이미 존재하거나 충돌하는 데이터입니다."
    ),

    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "서버 내부 오류가 발생했습니다."
    );

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(
            HttpStatus status,
            String defaultMessage
    ) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return name();
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}