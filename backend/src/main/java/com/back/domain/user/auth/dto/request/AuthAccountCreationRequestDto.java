package com.back.domain.user.auth.dto.request;

import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.member.entity.Member;
import lombok.Builder;

/**
 * AuthAccount 생성 데이터 DTO.
 *
 * <p>서비스 계층 내부에서만 사용합니다.
 * OAuth 계정 생성 시 {@code password}는 null로 전달합니다.</p>
 */
@Builder
public record AuthAccountCreationRequestDto(
        Member member,
        /** 로컬 인증 전용 평문 비밀번호. OAuth 계정은 null. */
        String password,
        AuthProvider provider,
        String issuer,
        String subject,
        String email
) {
}
