package com.back.domain.user.member.dto.response;

import com.back.domain.user.member.entity.Member;

import java.time.LocalDateTime;

public record PublicMemberDataResponseDto(
        String displayName,
        String profileImageUrl,
        LocalDateTime createdDate
) {
    public static PublicMemberDataResponseDto fromMember(Member member) {
        return new PublicMemberDataResponseDto(
                member.getDisplayName(),
                member.getProfileImageUrl(),
                member.getCreatedDate()
        );
    }

    public static PublicMemberDataResponseDto convertToPublic(PrivateMemberDataResponseDto dto) {
        return new PublicMemberDataResponseDto(
                dto.displayName(),
                dto.profileImageUrl(),
                dto.createdDate()
        );
    }

}
