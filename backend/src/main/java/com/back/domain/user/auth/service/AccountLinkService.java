package com.back.domain.user.auth.service;

import com.back.domain.user.auth.dto.response.AccountLinkIssueResponseDto;
import com.back.domain.user.auth.entity.AccountLinkRedisHash;
import com.back.domain.user.auth.repository.AccountLinkCrudRepository;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.back.global.property.UrlProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 계정 연동 linkCode를 Redis에 발급·조회·삭제하는 서비스.
 *
 * <p>linkCode는 UUID 형식으로 생성되며 OAuth2 state 파라미터를 통해 전달됩니다.
 * TTL은 {@code SecurityConstants.ACCOUNT_LINK_TTL_SECONDS}에 따라 자동 만료됩니다.</p>
 */
@Service
@RequiredArgsConstructor
public class AccountLinkService {

    private final AccountLinkCrudRepository accountLinkCrudRepository;
    private final UrlProperty urlProperty;

    /**
     * 동일 Member가 발급한 모든 linkCode를 삭제합니다.
     *
     * <p>새 연동 요청 전 기존 세션을 정리하여 중복 세션을 방지합니다.</p>
     *
     * @param memberId 삭제할 linkCode의 소유자 Member ID
     */
    public void removeAllLinkCodeByMemberId(Long memberId) {
        accountLinkCrudRepository.deleteAllByMemberId(memberId);
    }

    /**
     * 새 linkCode를 발급하고 OAuth 인가 URL을 반환합니다.
     *
     * <p>반환되는 {@code redirectPath}에 {@code linkCode} 쿼리 파라미터가 포함되어 있습니다.
     * 클라이언트는 이 URL로 리다이렉트하면 됩니다.</p>
     *
     * @param memberId          연동을 요청하는 Member ID
     * @param requestedProvider 연동할 OAuth provider (소문자, 예: "google")
     * @return linkCode가 포함된 OAuth 인가 URL 응답
     */
    public AccountLinkIssueResponseDto issueLinkCode(Long memberId, String requestedProvider) {
        AccountLinkRedisHash newLinkCode = createNewLinkCode(memberId, requestedProvider);

        String redirectPath = urlProperty.getBackUrl() + "/oauth2/authorization/" + requestedProvider +
                "?linkCode=" + newLinkCode.getLinkCode();

        return new AccountLinkIssueResponseDto(redirectPath);
    }

    /**
     * linkCode가 유효한지 확인하고 연동 세션 데이터를 반환합니다.
     *
     * @param linkCode 검증할 linkCode
     * @return 연동 세션 데이터
     * @throws ApiException linkCode가 만료되었거나 존재하지 않으면 {@code ErrorCode.SESSION_EXPIRED}
     */
    public AccountLinkRedisHash verifyLinkCode(String linkCode) {
        return findAccountLinkByLinkCode(linkCode)
                .orElseThrow(() -> new ApiException(ErrorCode.SESSION_EXPIRED, "Link session expired."));
    }

    private AccountLinkRedisHash createNewLinkCode(Long memberId, String requestedProvider) {
        AccountLinkRedisHash temp = AccountLinkRedisHash.builder()
                .memberId(memberId)
                .requestedProvider(requestedProvider)
                .build();

        return accountLinkCrudRepository.save(temp);
    }

    private Optional<AccountLinkRedisHash> findAccountLinkByLinkCode(String LinkCode) {
        return accountLinkCrudRepository.findById(LinkCode);
    }

}
