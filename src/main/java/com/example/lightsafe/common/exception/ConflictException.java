package com.example.lightsafe.common.exception;

public class ConflictException
        extends BaseException {

    public ConflictException(
            String message
    ) {
        super(
                ErrorCode.CONFLICT,
                message
        );
    }
}