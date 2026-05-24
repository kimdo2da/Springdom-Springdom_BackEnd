package com.example.lightsafe.user.dto;

public class ApiResponse<T> {
    private boolean success;
    private T data;
    private String message;
    private ErrorDetail error;

    // 성공 응답용 생성자
    public ApiResponse(boolean success, T data, String message) {
        this.success = success;
        this.data = data;
        this.message = message;
    }

    // 실패 응답용 생성자
    public ApiResponse(boolean success, String code, String message) {
        this.success = success;
        this.error = new ErrorDetail(code, message);
    }

    // Getter들 (Lombok이 없으므로 필수!)
    public boolean isSuccess() { return success; }
    public T getData() { return data; }
    public String getMessage() { return message; }
    public ErrorDetail getError() { return error; }

    public static class ErrorDetail {
        private String code;
        private String message;

        public ErrorDetail(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public String getCode() { return code; }
        public String getMessage() { return message; }
    }
}