package com.example.lightsafe.emergency;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class EmergencyApiResponse<T> {

    private boolean success;
    private T data;
    private String message;

    public static <T> EmergencyApiResponse<T> ok(T data) {
        return new EmergencyApiResponse<>(true, data, "OK");
    }

    public static <T> EmergencyApiResponse<T> ok(T data, String message) {
        return new EmergencyApiResponse<>(true, data, message);
    }
}