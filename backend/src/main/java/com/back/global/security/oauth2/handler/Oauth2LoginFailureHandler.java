package com.back.global.security.oauth2.handler;

import com.back.domain.user.auth.exception.AccountLinkRequiredException;
import com.back.global.property.UrlProperty;
import com.back.global.security.constant.SecurityConstants;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class Oauth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final UrlProperty urlProperty;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException, ServletException {
        log.error("OAuth2 Login Failure: {}", exception.getMessage());

        String redirectUrl;

        if (exception.getCause() instanceof AccountLinkRequiredException linkException) {
            log.debug("Account Link Required: email={}, newProvider={}, existingProvider={}",
                    linkException.getEmail(),
                    linkException.getNewProvider(),
                    linkException.getExistingProvider());

            redirectUrl = linkException.getRedirectUrl();
            response.sendRedirect(redirectUrl);
            return;
        }

        String linkCode = (String) request.getAttribute(SecurityConstants.LINK_CODE_ATTRIBUTE);
        if (linkCode != null && exception instanceof OAuth2AuthenticationException oauth2Ex) {
            String errorCode = mapLinkErrorToApiCode(oauth2Ex.getError().getErrorCode());
            redirectUrl = UriComponentsBuilder
                    .fromUriString(urlProperty.getFrontUrl())
                    .path("/account/linked")
                    .queryParam("success", "false")
                    .queryParam("error", errorCode)
                    .build()
                    .toUriString();
            response.sendRedirect(redirectUrl);
            return;
        }

        String code = mapToErrorCode(exception);

        redirectUrl = UriComponentsBuilder.fromUriString(urlProperty.getFrontUrl())
                .queryParam("status", "error")
                .queryParam("code", code)
                .build(true)
                .toUriString();

        response.sendRedirect(redirectUrl);
    }

    private String mapToErrorCode(AuthenticationException ex) {
        String msg = (ex.getMessage() == null ? "" : ex.getMessage()).toLowerCase();

        if (msg.contains("access_denied")) return "ACCESS_DENIED";
        if (msg.contains("email")) return "EMAIL_MISSING";
        if (msg.contains("invalid_state") || msg.contains("state")) return "INVALID_STATE";
        if (msg.contains("temporarily_unavailable") || msg.contains("server_error")) return "PROVIDER_UNAVAILABLE";
        if (msg.contains("invalid_client") || msg.contains("unauthorized_client")) return "CLIENT_CONFIG_ERROR";
        return "OAUTH2_FAILURE";
    }

    private String mapLinkErrorToApiCode(String oauth2ErrorCode) {
        return switch (oauth2ErrorCode) {
            case "INVALID_LINK_REQUEST" -> "AUTH014";
            case "INVALID_LINK_CODE"    -> "AUTH006";
            default                     -> "COM001";
        };
    }
}
