package com.back.domain.user.auth.useCase;

import com.back.domain.user.auth.dto.request.AuthAccountCreationRequestDto;
import com.back.domain.user.auth.dto.response.AccountLinkIssueResponseDto;
import com.back.domain.user.auth.dto.response.AccountLinkStatusResponseDto;
import com.back.domain.user.auth.entity.AccountLinkRedisHash;
import com.back.domain.user.auth.entity.AuthAccount;
import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.auth.service.AccountLinkService;
import com.back.domain.user.auth.service.AuthAccountService;
import com.back.domain.user.member.entity.Member;
import com.back.domain.user.member.service.MemberService;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 계정 연동 서비스.
 *
 * <p>로그인된 사용자가 다른 OAuth provider를 연동할 때 사용합니다.</p>
 *
 * <p>L3 충돌 (같은 이메일로 다른 provider에서 각각 가입한 경우) 처리는 추가 논의 후 구현 예정입니다.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountLinkCase {

    private final AccountLinkService accountLinkService;
    private final MemberService memberService;
    private final AuthAccountService authAccountService;

    /**
     * 현재 로그인된 Member에 새로운 OAuth 계정을 연동합니다.
     *
     * @param currentMemberId 현재 로그인된 Member ID
     * @param provider        새로 연동할 OAuth provider
     * @param issuer          OAuth issuer
     * @param subject         OAuth subject (provider user id)
     * @param email           OAuth 이메일
     * @return 생성된 AuthAccount
     */
    @Transactional
    public AuthAccount linkOauth2Account(
            Long currentMemberId,
            AuthProvider provider,
            String issuer,
            String subject,
            String email
    ) {
        // 이미 연동된 계정인지 확인
        Optional<AuthAccount> foundAccount = authAccountService
                .findByAuthProviderAndIssuerAndSubject(provider, issuer, subject);

        // 기존 AuthAccount 존재 시
        if (foundAccount.isPresent()) {
            AuthAccount account = foundAccount.get();
            Member owner = account.getMember();

            // 소유자가 다르면 예외 발생
            if (owner != null && !currentMemberId.equals(owner.getId()))
                throw new ApiException(ErrorCode.ACCESS_DENIED,
                        "This OAuth account is already linked to another member");

            // 멤버가 활동 중이어야 link 가능
            if (owner == null) {
                owner = memberService.findMemberByValidation(currentMemberId);
                account.setMember(owner);
            } else {
                memberService.memberValidation(owner);
            }

            log.debug("Account linked: provider={}, accountId={}", provider, account.getId());
            return account;
        }

        Member member = memberService.findMemberByValidation(currentMemberId);
        AuthAccount savedAccount = authAccountService.createAuthAccount(
                AuthAccountCreationRequestDto.builder()
                        .member(member)
                        .provider(provider)
                        .issuer(issuer)
                        .subject(subject)
                        .email(email)
                        .build()
        );

        log.debug("Account linked: provider={}, accountId={}", provider, savedAccount.getId());
        return savedAccount;
    }

    /**
     * 계정 연동 상태에 대해 반환합니다.
     *
     * @param memberId 현재 접속 중인 memberId
     * @param currentProvider 현재 접속 중인 member의 provider
     * @return 계정 연결 상태 관련 리스트
    */
    public List<AccountLinkStatusResponseDto> getCurrentAccountLinkStatus(
            Long memberId, AuthProvider currentProvider
    ) {
        List<AuthAccount> currentAccount = authAccountService.findAllAuthAccountsByMemberId(memberId);

        if (currentAccount.isEmpty()) throw new ApiException(ErrorCode.SESSION_EXPIRED);

        Map<AuthProvider, AuthAccount> linkedAccMap = currentAccount
                .stream()
                .collect(Collectors.toMap(AuthAccount::getAuthProvider, Function.identity())
        );

        return AuthProvider.oAuth2Providers.stream()
                .map(provider -> {
                    AuthAccount acc = linkedAccMap.getOrDefault(provider, null);

                    return acc != null
                            ? AccountLinkStatusResponseDto.linked(acc, provider.equals(currentProvider))
                            : AccountLinkStatusResponseDto.notLinked(provider);
                })
                .toList();
    }

    /**
     * 계정 연동을 시작합니다.
     *
     * <p>동일 사용자의 기존 연동 세션을 삭제하고 새로운 linkCode를 발급합니다.
     * 반환되는 oauthUrl에 linkCode가 쿼리 파라미터로 포함됩니다.</p>
     *
     * @param memberId          현재 로그인된 Member ID
     * @param requestedProvider 연동하려는 OAuth provider
     * @return linkCode와 OAuth URL을 포함한 응답
     */
    public AccountLinkIssueResponseDto startAccountLinking(Long memberId, String requestedProvider) {
        validateAccountLink(memberId, requestedProvider, true);

        String providerLowerCase = requestedProvider.toLowerCase();

        // linkCode 발급
        return accountLinkService.issueLinkCode(memberId, providerLowerCase);
    }

    /**
     * linkCode를 검증하고 연동 세션 데이터를 반환합니다.
     *
     * @param linkCode 검증할 linkCode
     * @return 연동 세션 데이터
     * @throws ApiException linkCode가 유효하지 않거나 만료된 경우
     */
    public AccountLinkRedisHash verifyLinkCode(String linkCode) {
        return accountLinkService.verifyLinkCode(linkCode);
    }

    /**
     * linkCode를 소비(삭제)합니다.
     *
     * @param memberId 삭제할 linkCode
     */
    public void consumeLinkCodeByMemberId(Long memberId) {
        accountLinkService.removeAllLinkCodeByMemberId(memberId);
    }

    /**
     * 특정 OAuth provider와의 계정 연동을 해제합니다.
     *
     * <p>마지막 남은 provider인 경우 연동 해제가 거부됩니다.
     * 최소 하나의 인증 수단을 유지해야 합니다.</p>
     *
     * @param memberId Member ID
     * @param provider 연동 해제할 OAuth provider 문자열
     * @throws ApiException provider가 1개뿐인 경우 {@code ErrorCode.ACCESS_DENIED}
     */
    @Transactional
    public void unlinkAccount(Long memberId, String provider) {
        validateAccountLink(memberId, provider, false);
        authAccountService.removeAuthAccountFromMember(memberId, provider);
    }

    private void validateAccountLink(Long memberId, String requestedProvider, boolean doLink) {
        Member member = memberService.findByIdWithAuthAccounts(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCESS_DENIED, "Access denied."));

        memberService.memberValidation(member);

        List<AuthAccount> authAccounts = member.getAuthAccounts();

        if (authAccounts.isEmpty())
            throw new ApiException(ErrorCode.ACCESS_DENIED, "You must link at least one provider.");

        AuthProvider foundAp = AuthProvider.of(requestedProvider);

        if (doLink) {
            authAccounts.forEach(acc -> {
                if (foundAp.equals(acc.getAuthProvider()))
                    throw new ApiException(ErrorCode.ACCESS_DENIED, "You are already linked to this account.");
            });
        } else {
            if (authAccounts.size() <= 1)
                throw new ApiException(ErrorCode.ACCESS_DENIED, "You must link at least one provider.");
        }
    }

}
