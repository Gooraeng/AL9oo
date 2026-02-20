package com.back.domain.user.member.service;

import com.back.domain.user.auth.dto.response.NickNameCheckResultResponseDto;
import com.back.domain.user.member.entity.Member;
import com.back.domain.user.member.repository.MemberRepository;
import com.back.domain.user.member.type.MemberRole;
import com.back.domain.user.member.type.MemberStatus;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    public Member findMemberById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Member not found."));
    }

    public Member findMemberByValidation(Long memberId) {
        Member member = findMemberById(memberId);

        memberValidation(member);

        return member;
    }

    public void memberValidation(Member member) {
        if (member.actuallyInActive())
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "This member is not active.");
    }

    public NickNameCheckResultResponseDto checkDisplayNameDuplication(@NotNull String displayName) {
        boolean result = memberRepository.existsMemberByDisplayName(displayName);

        return new NickNameCheckResultResponseDto(!result);
    }

    public Optional<Member> findByIdWithAuthAccounts(Long memberId) {
        return memberRepository.findByIdWithAuthAccounts(memberId);
    }

    @Transactional
    public Member createMember(
            String displayName,
            MemberRole role,
            MemberStatus status,
            boolean essentialTermsAgreed
    ) {
        Member member = Member.builder()
                .displayName(displayName)
                .role(role)
                .memberStatus(status)
                .essentialTermsAgreed(essentialTermsAgreed)
                .build();

        return saveMember(member);
    }

    @Transactional
    public void withdrawMember(Long memberId) {
        Member member = findMemberById(memberId);

        member.withdraw();
        saveMember(member);
    }

    @Transactional
    public Member saveMember(Member member) {
        return memberRepository.save(member);
    }
}
