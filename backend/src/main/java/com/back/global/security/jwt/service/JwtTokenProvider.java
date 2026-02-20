package com.back.global.security.jwt.service;

import com.back.domain.user.member.type.MemberRole;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.back.global.property.JwtProperty;
import com.back.global.security.jwt.dto.AccessTokenDto;
import com.back.global.security.jwt.dto.RefreshTokenDto;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final JwtProperty jwtProperty;
    private SecretKey key;

    @PostConstruct
    private SecretKey getSignKey() {
        if (this.key == null) {
            String secretKey = jwtProperty.getSecretKey();
            if (secretKey == null || secretKey.isBlank()) {
                log.error("Secret key is not set. Terminating..");
                System.exit(1);
            } else {
                byte[] keyBytes = Decoders.BASE64.decode(secretKey);
                this.key = Keys.hmacShaKeyFor(keyBytes);
            }
        }

        return this.key;
    }

    // CREATE TOKENS
    public String generateAccessToken(Long memberId, String role) {
        long ttl = jwtProperty.getAccessToken().getDuration().toSeconds() * 1000;

        return constructBaseJwtValues(memberId, ttl)
                .claim("type", "access")
                .claim("role", role)
                .compact();
    }

    public String generateRefreshToken(Long memberId, @NotBlank String deviceId) {
        long ttl = jwtProperty.getRefreshToken()
                .getDuration()
                .toSeconds() * 1000;

        return constructBaseJwtValues(memberId, ttl)
                .claim("type", "refresh")
                .claim("sid", deviceId)
                .compact();
    }

    private JwtBuilder constructBaseJwtValues(Long memberId, Long ttl) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + ttl);

        return Jwts.builder()
                .header().type("JWT").and()
                .issuer(jwtProperty.getIssuer())
                .audience().add(jwtProperty.getAudience()).and()
                .subject(memberId.toString())
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSignKey());
    }

    // Get DTOs
    public AccessTokenDto getAccessTokenDto(String token) {
        Claims claims = getAllClaimsFromToken(token);

        String jti = getJtiFromClaims(claims);
        Long memberId = getMemberIdFromClaims(claims);
        String role = getRoleFromClaims(claims);

        return new AccessTokenDto(jti, memberId, MemberRole.of(role));
    }

    public RefreshTokenDto getRefreshTokenDto(String token) {
        Claims claims = getAllClaimsFromToken(token);

        String jti = getJtiFromClaims(claims);
        Long memberId = getMemberIdFromClaims(claims);
        String deviceId = getDeviceIdFromClaims(claims);

        return new RefreshTokenDto(jti, memberId, deviceId);
    }

    // Validate TOKENS
    private Jws<Claims> parseToken(String token) {
        return Jwts.parser()
                .requireIssuer(jwtProperty.getIssuer())
                .requireAudience(jwtProperty.getAudience())
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
    }

    /**
    * 토큰에서 모든 클레임(claims) 추출
    */
    private Claims getAllClaimsFromToken(String token) {
        try {
            return parseToken(token)
                    .getPayload();
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.info("잘못된 JWT 서명입니다.");
            throw new ApiException(ErrorCode.INVALID_JWT);
        } catch (MissingClaimException | IncorrectClaimException e) {
            log.info("JWT claims 검증 실패: {}", e.getMessage());
            throw new ApiException(ErrorCode.INVALID_JWT);
        } catch (ExpiredJwtException e) {
            log.info("만료된 JWT 토큰입니다.");
            throw new ApiException(ErrorCode.EXPIRED_JWT);
        } catch (UnsupportedJwtException e) {
            log.info("지원되지 않는 JWT 토큰입니다.");
            throw new ApiException(ErrorCode.UNSUPPORTED_JWT);
        } catch (Exception e) {
            log.info("JWT 토큰이 잘못되었습니다.");
            throw new ApiException(ErrorCode.ILLEGAL_VALUE_JWT);
        }
    }

    /**
     * 토큰에서 사용자 ID 추출
     */
    private Long getMemberIdFromClaims(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 토큰에서 사용자명 추출
     */
    private String getJtiFromClaims(Claims claims) {
        return claims.get("jti", String.class);
    }

    /**
     * 토큰에서 역할(Role) 추출
     */
    private String getRoleFromClaims(Claims claims) {
        return claims.get("role", String.class);
    }

    /**
     * 토큰에서 Device ID(sid) 추출
     */
    private String getDeviceIdFromClaims(Claims claims) {
        return claims.get("sid", String.class);
    }

}
