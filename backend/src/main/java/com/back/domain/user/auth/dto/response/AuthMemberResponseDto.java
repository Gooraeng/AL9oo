package com.back.domain.user.auth.dto.response;

import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.member.type.MemberRole;
import com.back.domain.user.member.type.MemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 인증된 회원의 기본 정보 응답 DTO.
 */
public record AuthMemberResponseDto(
        @Schema(description = "회원 고유 ID", example = "1")
        Long id,

        @Schema(description = "회원 닉네임", example = "레이서_123")
        String displayName,

        @Schema(description = "회원 권한 (GUEST, USER, ADMIN)")
        MemberRole role,

        @Schema(description = "현재 로그인에 사용된 OAuth provider")
        AuthProvider loginProvider,

        @Schema(description = "회원 상태 (ACTIVE, INACTIVE, BANNED)")
        MemberStatus status,

        @Schema(description = "계정 활성화 가능 여부")
        boolean canActive
) {
}
