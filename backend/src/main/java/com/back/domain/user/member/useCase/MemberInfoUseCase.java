package com.back.domain.user.member.useCase;

import com.back.domain.user.member.dto.MemberUpdateRequestDto;
import com.back.domain.user.member.dto.response.PrivateMemberDataResponseDto;
import com.back.domain.user.member.dto.response.PublicMemberDataResponseDto;
import com.back.domain.user.member.entity.Member;
import com.back.domain.user.member.service.MemberService;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberInfoUseCase {

    private final MemberService memberService;

    public PublicMemberDataResponseDto getPublicMemberInfo(Long targetMemberId) {
        return PublicMemberDataResponseDto.convertToPublic(getPrivateMemberInfo(targetMemberId));
    }

    public PrivateMemberDataResponseDto getPrivateMemberInfo(Long targetMemberId) {
        Member member = memberService.findMemberById(targetMemberId);

        if (member.isDeleted())
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "This member is deleted.");

        return PrivateMemberDataResponseDto.fromMember(member);
    }

    public void updateMemberInfo(Long memberId, MemberUpdateRequestDto dto) {
        Member member = memberService.findMemberById(memberId);

        if (member.isDeleted())
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "This member is deleted.");

        if (!memberInfoChanged(member, dto)) return;

        memberService.saveMember(member);
    }

    private boolean memberInfoChanged(Member member, MemberUpdateRequestDto dto) {
        boolean hasUpdate = false;

        // Nickname
        String newDisplayName = dto.displayName();
        if (!newDisplayName.equals(member.getDisplayName())) {
            if (!memberService.checkDisplayNameDuplication(newDisplayName).available()) {
                throw new ApiException(ErrorCode.CONFLICT, "Same nickname is being used");
            }
            hasUpdate = true;
            member.setDisplayName(newDisplayName);
        }

        return hasUpdate;
    }

    public void withdrawUsingService(Long memberId) {
        memberService.withdrawMember(memberId);
    }
}
