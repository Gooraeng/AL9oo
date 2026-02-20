package com.back.domain.user.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 계정 연동 시작 응답 DTO.
 *
 * <p>클라이언트는 {@code redirectPath}로 리다이렉트하여 OAuth 인가 흐름을 시작합니다.</p>
 */
public record AccountLinkIssueResponseDto(
        @Schema(description = "linkCode가 포함된 OAuth 인가 URL. 클라이언트는 이 URL로 리다이렉트해야 합니다.",
                example = "https://api.example.com/oauth2/authorization/google?linkCode=550e8400-e29b-41d4-a716-446655440000")
        String redirectPath
) {
}
