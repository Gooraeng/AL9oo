package com.back.global.security.oauth2.attr;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.util.Map;

/**
 * Dto Class that used
 */
public record Oauth2Attributes(
        String email,
        String name,
        String issuer,
        String subject,
        String nameAttrKey,
        Map<String, Object> attributes
) {
    public static Oauth2Attributes of(
            String registrationId,
            String userNameAttributeName,
            Map<String, Object> attributes
    ) {
        return switch (registrationId.toLowerCase()) {
            case "discord" -> ofDiscord(userNameAttributeName, attributes);
            case "google" -> ofGoogle(userNameAttributeName, attributes);
            default -> throw new OAuth2AuthenticationException(
                    new OAuth2Error("UNSUPPORTED_PROVIDER", "지원하지 않는 제공자: " + registrationId, null)
            );
        };
    }

    private static Oauth2Attributes ofGoogle(
            String userNameAttributeName,
            Map<String, Object> attributes
    ) {
        return new Oauth2Attributes(
                (String) attributes.get("email"),
                (String) attributes.get("name"),
                "https://accounts.google.com",  // Google 표준 issuer
                (String) attributes.get("sub"),          // OIDC standard subject
                userNameAttributeName,
                attributes
        );
    }

    private static Oauth2Attributes ofDiscord(
            String userNameAttributeName,
            Map<String, Object> attributes
    ) {
        return new Oauth2Attributes(
                (String) attributes.get("email"),
                (String) attributes.get("username"),
                "https://discord.com/api",       // Discord issuer
                String.valueOf(attributes.get("id")),    // Discord user ID
                userNameAttributeName,
                attributes
        );
    }
}
