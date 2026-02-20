package com.back.domain.user.auth.useCase;

import com.back.domain.user.auth.dto.request.AuthAccountCreationRequestDto;
import com.back.domain.user.auth.dto.request.SignUpRequestDto;
import com.back.domain.user.auth.dto.response.SignUpResponseDto;
import com.back.domain.user.auth.entity.AuthAccount;
import com.back.domain.user.auth.service.AuthAccountService;
import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.member.entity.Member;
import com.back.domain.user.member.service.MemberService;
import com.back.domain.user.member.type.MemberRole;
import com.back.domain.user.member.type.MemberStatus;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * OAuth2 계정 탐색 또는 신규 생성, 가입 완료 처리 유스케이스.
 *
 * <p>OAuth2 로그인 시 기존 계정이 있으면 반환하고, 없으면 GUEST 역할의 신규 Member와
 * AuthAccount를 생성합니다. {@link #doSignUp}을 통해 GUEST → USER 역할 전환 및
 * 닉네임 설정 등 가입 완료 단계를 처리합니다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class Oauth2SignUpOrLoginCase implements SignUpCase {

    private final AuthAccountService authAccountService;
    private final MemberService memberService;

    /**
     * OAuth 계정으로 로그인 시 AuthAccount를 찾거나 생성합니다.
     */
    @Transactional
    public AuthAccount findOrCreateOAuth2Account(
            String email,
            String issuer,
            String subject,
            AuthProvider provider
    ) {
        if (provider.equals(AuthProvider.LOCAL))
            throw new ApiException(ErrorCode.UNSUPPORTED_WAY, "This method is only for OAuth providers.");

        // 1. (provider, issuer, subject)로 기존 계정 확인
        Optional<AuthAccount> existing = authAccountService
                .findByAuthProviderAndIssuerAndSubject(provider, issuer, subject);

        if (existing.isPresent()) return existing.get();

        // 2. 신규 사용자 - 새 Member + AuthAccount 생성
        Member newMember = memberService.createMember(
                provider.name() + "-" + subject,
                MemberRole.GUEST, MemberStatus.ACTIVE,
                false
        );

        log.info("OAuth 계정용 새 Member 생성: {}", newMember.getId());

        return authAccountService.createAuthAccount(
                AuthAccountCreationRequestDto.builder()
                        .member(newMember)
                        .provider(provider)
                        .issuer(issuer)
                        .subject(subject)
                        .email(email)
                        .build()
        );
    }

    /**
     * OAuth2 가입 완료 단계를 처리합니다.
     *
     * <p>GUEST 상태의 Member가 닉네임을 입력하고 약관에 동의하면 USER 역할로 전환됩니다.
     * 이미 가입 완료된 경우(GUEST가 아닌 경우) CONFLICT 예외가 발생합니다.</p>
     *
     * @param dto 닉네임, 약관 동의, memberId를 포함한 가입 요청
     * @return 환영 메시지를 포함한 가입 완료 응답
     * @throws ApiException 약관 미동의, 닉네임 중복, 이미 가입 완료된 경우 예외 발생
     */
    @Override
    public SignUpResponseDto doSignUp(SignUpRequestDto dto) {
        if (!dto.isEssentialTermsAgreed())
            throw new ApiException(ErrorCode.TERMS_NOT_ACCEPTED, "You must agree to the terms and privacy policy to sign up.");

        if (!memberService.checkDisplayNameDuplication(dto.getDisplayName()).available())
            throw new ApiException(ErrorCode.CONFLICT, "Same nickname is being used");

        Member member = memberService.findMemberById(dto.getMemberId());

        if (!member.getRole().equals(MemberRole.GUEST)) {
            throw new ApiException(ErrorCode.CONFLICT, "You have already completed the sign-up process.");
        }

        member.setDisplayName(dto.getDisplayName());
        member.completeSignUp();

        Member editedMember = memberService.saveMember(member);

        String message = "Welcome, " + editedMember.getDisplayName() + "!";

        return new SignUpResponseDto(message);
    }
}
