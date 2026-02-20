package com.back.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;


@RequiredArgsConstructor
@Getter
public enum ErrorCode {

    // Auth
    INVALID_JWT(HttpStatus.UNAUTHORIZED, "AUTH001", "Invalid JWT token"),
    EXPIRED_JWT(HttpStatus.UNAUTHORIZED, "AUTH002", "Expired JWT token"),
    UNSUPPORTED_JWT(HttpStatus.UNAUTHORIZED, "AUTH003", "Unsupported JWT token"),
    ILLEGAL_VALUE_JWT(HttpStatus.UNAUTHORIZED, "AUTH004", "Illegal JWT token"),
    TERMS_NOT_ACCEPTED(HttpStatus.BAD_REQUEST, "AUTH005", "Terms and conditions not accepted"),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH006", "Session expired"),

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COM001", "Invalid input"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "COM002", "Access Denied"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "COM003", "Not found"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COM004", "Internal server error"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COM005", "This request is invalid."),
    CONFLICT(HttpStatus.CONFLICT, "COM006", "Conflict"),
    UNSUPPORTED_WAY(HttpStatus.UNPROCESSABLE_CONTENT, "COM007" , "Unsupported way" ),

    // Car
    CAR_NOT_FOUND(HttpStatus.NOT_FOUND, "CAR001", "Car not found"),
    CAR_ALREADY_EXISTS(HttpStatus.CONFLICT, "CAR002", "Car already exists"),
    CAR_ALREADY_DELETED(HttpStatus.CONFLICT, "CAR003", "Car was already deleted"),

    // Manufacturer
    MANUFACTURER_NOT_FOUND(HttpStatus.NOT_FOUND, "MAN001", "Manufacturer not found"),
    MANUFACTURER_EXISTS(HttpStatus.CONFLICT, "MAN002", "Duplicate manufacturer"),
    MANUFACTURER_HAS_CARS(HttpStatus.CONFLICT, "MAN003", "Cannot delete manufacturer with existing cars"),

    // Track

    // Member
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEM001", "Member not found"),

    // Account
    ACCOUNT_LINK_REQUIRED(HttpStatus.CONFLICT, "AUTH007", "Account Linking is required."),
    ACCOUNT_LINK_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH008", "계정 연결 토큰이 만료되었습니다"),
    ACCOUNT_LINK_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "AUTH009", "유효하지 않은 계정 연결 토큰입니다"),
    ACCOUNT_LINK_REJECTED(HttpStatus.BAD_REQUEST, "AUTH010", "사용자가 계정 연결을 거부했습니다"),
    ACCOUNT_LINK_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "AUTH011", "계정 연결 요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),
    ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, "AUTH012", "이미 다른 계정에 연결된 OAuth 계정입니다"),
    NO_STRATEGY_FOUND(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH013", "No OAuth2 processing strategy found"),
    INVALID_LINK_REQUEST(HttpStatus.BAD_REQUEST, "AUTH014", "Invalid account linking request"),

    // Reference

    // Email,
    EMAIL_NOT_VERIFIED(HttpStatus.UNAUTHORIZED, "EMA001", "Email verification failed"),
    EMAIL_CODE_NOT_EXIST(HttpStatus.NOT_FOUND, "EMA002", "Email code was expired"),
    EMAIL_ALREADY_IN_USE(HttpStatus.CONFLICT, "EMA003", "Email is already in use."),
    EMAIL_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "EMA003", "Email send failed");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

}
