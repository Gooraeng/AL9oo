package com.back.domain.user.member.dto.response;

import com.back.domain.user.member.entity.Member;

import java.time.LocalDateTime;

public record PrivateMemberDataResponseDto(
        Long id,
        String displayName,
        String profileImageUrl,
        LocalDateTime createdDate
) {
    public static PrivateMemberDataResponseDto fromMember(Member member) {
        return new PrivateMemberDataResponseDto(
                member.getId(),
                member.getDisplayName(),
                member.getProfileImageUrl(),
                member.getCreatedDate()
        );
    }
}
