package com.example.lightsafe.emergency;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EmergencyApiErrorResponse {

    private boolean success;
    private ErrorBody error;

    @Getter
    @AllArgsConstructor
    public static class ErrorBody {
        private int code;
        private String message;
    }

    public static EmergencyApiErrorResponse of(int code, String message) {
        return new EmergencyApiErrorResponse(false, new ErrorBody(code, message));
    }
}