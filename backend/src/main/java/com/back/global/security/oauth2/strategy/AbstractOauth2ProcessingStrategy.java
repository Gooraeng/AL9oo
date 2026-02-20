package com.back.global.security.oauth2.strategy;

import com.back.global.security.oauth2.attr.Oauth2Attributes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public abstract class AbstractOauth2ProcessingStrategy implements Oauth2ProcessingStrategy {

    private final DefaultOAuth2UserService defaultService = new DefaultOAuth2UserService();

    protected HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes requestAttributes =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return requestAttributes.getRequest();
    }

    protected void validateOAuthAttributes(Oauth2Attributes attrs) {
        if (attrs.email() == null || attrs.email().isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("EMAIL_NOT_FOUND", "Email not found from OAuth2 provider", null)
            );
        }
        if (attrs.issuer() == null || attrs.subject() == null) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("INVALID_OAUTH_DATA", "Issuer or subject missing from OAuth response", null)
            );
        }
    }

    protected DefaultOAuth2UserService getDefaultService() {
        return defaultService;
    }
}
