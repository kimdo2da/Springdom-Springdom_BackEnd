package com.example.lightsafe.common.exception;

public class ForbiddenException
        extends BaseException {

    public ForbiddenException(
            String message
    ) {
        super(
                ErrorCode.FORBIDDEN,
                message
        );
    }
}