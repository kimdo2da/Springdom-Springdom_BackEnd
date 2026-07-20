package com.example.lightsafe.common.exception;

public abstract class BaseException
        extends RuntimeException {

    private final ErrorCode errorCode;

    protected BaseException(
            ErrorCode errorCode,
            String message
    ) {
        super(
                message == null || message.isBlank()
                        ? errorCode.getDefaultMessage()
                        : message
        );

        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}