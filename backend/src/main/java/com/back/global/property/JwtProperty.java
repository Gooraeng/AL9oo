package com.back.global.property;

import com.back.global.security.cookie.BaseCookieValues;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "custom.cookie.jwt")
@Getter
@Setter
public class JwtProperty {

    private String secretKey;
    private String issuer;
    private String audience;
    private AccessToken accessToken = new AccessToken();
    private RefreshToken refreshToken = new RefreshToken();

    public static class AccessToken extends BaseCookieValues {}

    public static class RefreshToken extends BaseCookieValues {}
}
