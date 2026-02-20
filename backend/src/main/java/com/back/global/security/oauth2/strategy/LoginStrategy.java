package com.back.global.security.oauth2.strategy;

import com.back.domain.user.auth.entity.AuthAccount;
import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.auth.exception.AccountLinkRequiredException;
import com.back.domain.user.auth.useCase.Oauth2SignUpOrLoginCase;
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
 * 일반 OAuth2 로그인을 처리하는 Strategy 구현체.
 *
 * <p>계정 연동이 아닌 일반 로그인 플로우를 담당합니다.
 * 신규 사용자의 경우 Member와 AuthAccount를 생성하고,
 * 기존 사용자의 경우 해당 계정을 반환합니다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LoginStrategy extends AbstractOauth2ProcessingStrategy {

    private final Oauth2SignUpOrLoginCase oauth2SignUpOrLoginCase;

    @Override
    public OAuth2User processUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        try {
            OAuth2User oAuth2User = getDefaultService().loadUser(userRequest);

            String registrationId = userRequest.getClientRegistration().getRegistrationId();
            String userNameAttr = userRequest.getClientRegistration()
                    .getProviderDetails()
                    .getUserInfoEndpoint()
                    .getUserNameAttributeName();

            // OAuth 속성 추출 (issuer, subject 포함)
            Oauth2Attributes attrs = Oauth2Attributes.of(
                    registrationId,
                    userNameAttr,
                    oAuth2User.getAttributes()
            );

            // 필수 필드 검증
            validateOAuthAttributes(attrs);

            // AuthAccount 찾기
            AuthAccount account = oauth2SignUpOrLoginCase.findOrCreateOAuth2Account(
                    attrs.email(),
                    attrs.issuer(),
                    attrs.subject(),
                    AuthProvider.of(registrationId));

            log.info("Login strategy: OAuth login successful for email: {}", attrs.email());
            return CustomUserDetails.fromOAuth(account, oAuth2User.getAttributes());

        } catch (AccountLinkRequiredException ex) {
            // Spring Security를 위해 OAuth2AuthenticationException으로 래핑
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("ACCOUNT_LINK_REQUIRED", ex.getMessage(), null),
                    ex
            );
        } catch (OAuth2AuthenticationException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error during OAuth2 login", ex);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("SERVER_ERROR", "Failed to process OAuth2 login", null),
                    ex
            );
        }
    }

    @Override
    public boolean canHandle(OAuth2UserRequest userRequest) {
        // linkCode가 없으면 일반 로그인으로 처리
        HttpServletRequest request = getCurrentRequest();
        String linkCode = (String) request.getAttribute(SecurityConstants.LINK_CODE_ATTRIBUTE);
        return linkCode == null;
    }
}
