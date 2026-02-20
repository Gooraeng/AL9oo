package com.back.global.security.oauth2.resolver;

import com.back.domain.user.auth.useCase.AccountLinkCase;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.back.global.security.oauth2.util.LinkCodeStateCodec;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

/**
 * OAuth2 인증 요청을 커스터마이징하는 Resolver.
 *
 * <p>계정 연동 시 linkCode를 감지하고, OAuth2 state에 인코딩하여 전달합니다.
 * Cookie 기반 전달 방식에서 State 기반으로 변경되었습니다.</p>
 */
@Component
@Slf4j
public class CustomOAuth2AuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver defaultResolver;
    private final AccountLinkCase accountLinkCase;
    private final LinkCodeStateCodec linkCodeStateCodec;

    @Autowired
    public CustomOAuth2AuthorizationRequestResolver(
            ClientRegistrationRepository repo,
            AccountLinkCase accountLinkCase,
            LinkCodeStateCodec linkCodeStateCodec
    ) {
        this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(
                repo, "/oauth2/authorization");
        this.accountLinkCase = accountLinkCase;
        this.linkCodeStateCodec = linkCodeStateCodec;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest authRequest = defaultResolver.resolve(request);
        return customize(authRequest, request);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest authRequest = defaultResolver.resolve(request, clientRegistrationId);
        return customize(authRequest, request);
    }

    private OAuth2AuthorizationRequest customize(
            OAuth2AuthorizationRequest authRequest,
            HttpServletRequest request
    ) {
        if (authRequest == null) return null;

        String linkCode = request.getParameter("linkCode");

        // 계정 연동 모드: linkCode가 있으면 state에 인코딩
        if (linkCode != null && !linkCode.isBlank()) {
            return handleAccountLinkMode(authRequest, linkCode);
        }

        // 일반 로그인
        return authRequest;
    }

    private OAuth2AuthorizationRequest handleAccountLinkMode(
            OAuth2AuthorizationRequest authRequest,
            String linkCode
    ) {
        try {
            // Redis에서 linkCode 존재 확인
            accountLinkCase.verifyLinkCode(linkCode);

            // state에 linkCode 인코딩
            String originalState = authRequest.getState();
            String encodedState = linkCodeStateCodec.encode(linkCode, originalState);

            log.debug("Account link mode: linkCode={}, encodedState={}", linkCode, encodedState);

            return OAuth2AuthorizationRequest.from(authRequest)
                    .state(encodedState)
                    .build();

        } catch (ApiException ex) {
            log.error("Invalid linkCode: {}", linkCode);
            throw new ApiException(ErrorCode.SESSION_EXPIRED, "Link session expired or invalid.");
        } catch (Exception ex) {
            log.error("Error handling account link mode", ex);
            throw new ApiException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Failed to process account linking request");
        }
    }
}
