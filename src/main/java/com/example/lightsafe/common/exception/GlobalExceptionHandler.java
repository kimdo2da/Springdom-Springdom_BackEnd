package com.example.lightsafe.common.exception;

import com.example.lightsafe.common.response.ApiResponse;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.NoSuchElementException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(
                    GlobalExceptionHandler.class
            );

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleBaseException(
            BaseException e
    ) {
        return buildResponse(
                e.getErrorCode(),
                e.getMessage()
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleAuthenticationException(
            AuthenticationException e
    ) {
        return buildResponse(
                ErrorCode.UNAUTHORIZED,
                "로그인이 필요합니다."
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleAccessDeniedException(
            AccessDeniedException e
    ) {
        return buildResponse(
                ErrorCode.FORBIDDEN,
                "해당 요청을 실행할 권한이 없습니다."
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e
    ) {
        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> {
                    String defaultMessage =
                            error.getDefaultMessage();

                    if (defaultMessage == null
                            || defaultMessage.isBlank()) {

                        return error.getField()
                                + " 값이 올바르지 않습니다.";
                    }

                    return defaultMessage;
                })
                .findFirst()
                .orElse(
                        ErrorCode.VALIDATION_ERROR
                                .getDefaultMessage()
                );

        return buildResponse(
                ErrorCode.VALIDATION_ERROR,
                message
        );
    }

    @ExceptionHandler({
            HandlerMethodValidationException.class,
            ConstraintViolationException.class,
            BindException.class
    })
    public ResponseEntity<ApiResponse<Void>>
    handleValidationException(
            Exception e
    ) {
        return buildResponse(
                ErrorCode.VALIDATION_ERROR,
                ErrorCode.VALIDATION_ERROR
                        .getDefaultMessage()
        );
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiResponse<Void>>
    handleInvalidRequestException(
            Exception e
    ) {
        return buildResponse(
                ErrorCode.BAD_REQUEST,
                "요청 형식 또는 요청 값이 올바르지 않습니다."
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException e
    ) {
        return buildResponse(
                ErrorCode.BAD_REQUEST,
                "업로드 가능한 파일 크기를 초과했습니다."
        );
    }

    @ExceptionHandler({
            NoSuchElementException.class,
            EntityNotFoundException.class
    })
    public ResponseEntity<ApiResponse<Void>>
    handleNotFoundException(
            RuntimeException e
    ) {
        String message =
                e.getMessage() == null
                        ? ErrorCode.NOT_FOUND
                        .getDefaultMessage()
                        : e.getMessage();

        return buildResponse(
                ErrorCode.NOT_FOUND,
                message
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleDataIntegrityViolationException(
            DataIntegrityViolationException e
    ) {
        return buildResponse(
                ErrorCode.CONFLICT,
                "이미 사용 중인 값이거나 데이터가 충돌합니다."
        );
    }

    /*
     * 기존 코드를 한 번에 옮기는 동안 남아 있는
     * SecurityException을 위한 임시 공통 처리입니다.
     */
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleSecurityException(
            SecurityException e
    ) {
        return buildResponse(
                ErrorCode.FORBIDDEN,
                e.getMessage()
        );
    }

    /*
     * 기존 IllegalArgumentException은 모두 400으로 처리합니다.
     * 404가 필요한 서비스는 NotFoundException으로 교체합니다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleIllegalArgumentException(
            IllegalArgumentException e
    ) {
        return buildResponse(
                ErrorCode.BAD_REQUEST,
                e.getMessage()
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>>
    handleIllegalStateException(
            IllegalStateException e
    ) {
        return buildResponse(
                ErrorCode.CONFLICT,
                e.getMessage()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>>
    handleException(
            Exception e
    ) {
        log.error(
                "처리되지 않은 서버 오류",
                e
        );

        return buildResponse(
                ErrorCode.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR
                        .getDefaultMessage()
        );
    }

    private ResponseEntity<ApiResponse<Void>>
    buildResponse(
            ErrorCode errorCode,
            String message
    ) {
        String safeMessage =
                message == null || message.isBlank()
                        ? errorCode.getDefaultMessage()
                        : message;

        return ResponseEntity
                .status(
                        errorCode.getStatus()
                )
                .body(
                        ApiResponse.fail(
                                errorCode,
                                safeMessage
                        )
                );
    }
}