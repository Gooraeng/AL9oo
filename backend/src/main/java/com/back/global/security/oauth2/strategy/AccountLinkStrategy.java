package com.back.global.security.oauth2.strategy;

import com.back.domain.user.auth.entity.AccountLinkRedisHash;
import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.auth.useCase.AccountLinkCase;
import com.back.global.security.constant.SecurityConstants;
import com.back.global.security.member.CustomUserDetails;
import com.back.global.security.oauth2.attr.Oauth2Attributes;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * 계정 연동을 처리하는 Strategy 구현체.
 *
 * <p>로그인된 사용자가 다른 OAuth provider를 연동하는 플로우를 담당합니다.
 * request attribute에서 linkCode를 확인하여 계정 연동 여부를 판단합니다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountLinkStrategy extends AbstractOauth2ProcessingStrategy {

    private final AccountLinkCase accountLinkCase;

    @Override
    public OAuth2User processUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 1. registrationId 추출 (HTTP 불필요)
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        // 2. request attribute에서 linkCode 추출
        HttpServletRequest request = getCurrentRequest();
        Object linkCode = request.getAttribute(SecurityConstants.LINK_CODE_ATTRIBUTE);

        if (linkCode == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("INVALID_LINK_REQUEST", "Link code not found in request", null));
        }

        String linkCodeStr = (String) linkCode;

        // 3. linkCode 검증 및 linkData 획득 (Redis)
        AccountLinkRedisHash linkData;
        try {
            linkData = accountLinkCase.verifyLinkCode(linkCodeStr);
        } catch (Exception ex) {
            log.error("Link code verification failed: {}", ex.getMessage());
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("INVALID_LINK_CODE", ex.getMessage(), null),
                    ex
            );
        }

        // 4. provider 검증: HTTP 호출 전 fail-fast
        if (!linkData.getRequestedProvider().equalsIgnoreCase(registrationId)) {
            log.warn("Provider mismatch: requested={}, actual={}",
                    linkData.getRequestedProvider(), registrationId);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("INVALID_LINK_REQUEST",
                            "OAuth provider does not match the requested provider", null));
        }

        // 5. loadUser() → HTTP 호출
        OAuth2User oAuth2User = getDefaultService().loadUser(userRequest);

        String userNameAttr = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        // 6. attrs 파싱 및 검증
        Oauth2Attributes attrs = Oauth2Attributes.of(registrationId, userNameAttr, oAuth2User.getAttributes());
        validateOAuthAttributes(attrs);

        Long memberId = linkData.getMemberId();
        log.info("Account link strategy: Validated linkCode for member: {}", memberId);

        // 7. CustomUserDetails 반환 (실제 연동은 SuccessHandler에서)
        return CustomUserDetails.builder()
                .memberId(memberId)
                .email(attrs.email())
                .displayName(attrs.name())
                .issuerValue(attrs.issuer())
                .subjectValue(attrs.subject())
                .role(null)
                .provider(AuthProvider.of(registrationId))
                .attributes(oAuth2User.getAttributes())
                .build();
    }

    @Override
    public boolean canHandle(OAuth2UserRequest userRequest) {
        HttpServletRequest request = getCurrentRequest();
        String linkCode = (String) request.getAttribute(SecurityConstants.LINK_CODE_ATTRIBUTE);
        return linkCode != null;
    }
}
