package com.back.global.security.jwt.dto;

import com.back.domain.user.member.type.MemberRole;

public record AccessTokenDto(
        String jti,
        Long memberId,
        MemberRole role
) {
}
