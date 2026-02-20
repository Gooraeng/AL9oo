package com.back.global.security.oauth2.service;

//import com.back.domain.user.auth.entity.AuthAccount;
//import com.back.domain.user.auth.enums.AuthProvider;
//import com.back.domain.user.auth.exception.AccountLinkRequiredException;
//import com.back.domain.user.auth.service.AuthService;
//import com.back.global.security.member.CustomUserDetails;
//import com.back.global.security.oauth2.attr.Oauth2Attributes;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
//import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
//import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
//import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
//import org.springframework.security.oauth2.core.OAuth2Error;
//import org.springframework.security.oauth2.core.user.OAuth2User;
//import org.springframework.stereotype.Service;
//
///**
// * @deprecated {@link StrategyOAuth2UserService}를 사용하세요.
// * Strategy Pattern을 사용하여 일반 로그인과 계정 연동을 분리합니다.
// */
//@Deprecated(since = "2026-01-24", forRemoval = true)
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class CustomOauth2MemberService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
//
//    private final DefaultOAuth2UserService defaultService = new DefaultOAuth2UserService();
//    private final AuthService authService;
//
//    @Override
//    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
//        try {
//            OAuth2User user = defaultService.loadUser(request);
//
//            String registrationId = request.getClientRegistration().getRegistrationId();
//            String userNameAttr = request.getClientRegistration()
//                    .getProviderDetails()
//                    .getUserInfoEndpoint()
//                    .getUserNameAttributeName();
//
//            // OAuth 속성 추출 (issuer, subject 포함)
//            Oauth2Attributes attrs = Oauth2Attributes.of(
//                    registrationId,
//                    userNameAttr,
//                    user.getAttributes()
//            );
//
//            // 필수 필드 검증
//            validateOAuthAttributes(attrs);
//
//            // Member + AuthAccount 찾기 또는 생성
//            AuthAccount account = authService.findOrCreateOAuthAccount(
//                attrs.email(),
//                attrs.name(),
//                attrs.issuer(),
//                attrs.subject(),
//                AuthProvider.of(registrationId)
//            );
//
//            return CustomUserDetails.fromOAuth(account, user.getAttributes());
//
//        } catch (AccountLinkRequiredException ex) {
//            // Spring Security를 위해 OAuth2AuthenticationException으로 래핑
//            throw new OAuth2AuthenticationException(
//                    new OAuth2Error("ACCOUNT_LINK_REQUIRED", ex.getMessage(), null),
//                    ex
//            );
//        } catch (OAuth2AuthenticationException ex) {
//            throw ex;
//        } catch (Exception ex) {
//            log.error("Error during OAuth2 user loading", ex);
//            throw new OAuth2AuthenticationException(
//                    new OAuth2Error("SERVER_ERROR", "Failed to load OAuth2 user", null),
//                    ex
//            );
//        }
//    }
//
//    private void validateOAuthAttributes(Oauth2Attributes attrs) {
//        if (attrs.email() == null || attrs.email().isBlank()) {
//            throw new OAuth2AuthenticationException(
//                    new OAuth2Error("EMAIL_NOT_FOUND", "Email not found from OAuth2 provider", null)
//            );
//        }
//        if (attrs.issuer() == null || attrs.subject() == null) {
//            throw new OAuth2AuthenticationException(
//                    new OAuth2Error("INVALID_OAUTH_DATA", "Issuer or subject missing from OAuth response", null)
//            );
//        }
//    }
//}
