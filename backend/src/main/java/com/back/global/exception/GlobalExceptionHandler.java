package com.back.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;


/**
 * Global Exception Handler
 * Returns Problem Details according to RFC 7807
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex, HttpServletRequest request) {
        logException(ex, ex.getErrorCode());
        return ProblemDetailCustom.of(ex.getErrorCode(), request, ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, HttpServletRequest request) {
        logException(ex, ErrorCode.INVALID_INPUT);
        return ProblemDetailCustom.of(ErrorCode.INVALID_INPUT, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValidException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getAllErrors()
                .reversed()
                .stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "));
        logException(ex, ErrorCode.INVALID_INPUT);
        return ProblemDetailCustom.of(ErrorCode.INVALID_INPUT, request, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        logException(ex, ErrorCode.INVALID_INPUT);
        return ProblemDetailCustom.of(ErrorCode.INVALID_INPUT, request, "Please check your input.");
    }

    private void logException(Exception ex, ErrorCode code) {
        log.error(
           """
           \n* Exception : {}
           * Status : {}
           * Message : {}
           """, ex.getClass(), code.getHttpStatus().value(), ex.getMessage(), ex);
    }

}
