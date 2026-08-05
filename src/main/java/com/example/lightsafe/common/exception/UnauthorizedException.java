package com.example.lightsafe.common.exception;

public class UnauthorizedException
        extends BaseException {

    public UnauthorizedException(
            String message
    ) {
        super(
                ErrorCode.UNAUTHORIZED,
                message
        );
    }
}