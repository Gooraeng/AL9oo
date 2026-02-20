package com.back.global.security.jwt.refreshToken.service;

import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.back.global.property.JwtProperty;
import com.back.global.property.RedisProperty;
import com.back.global.security.constant.SecurityConstants;
import com.back.global.security.jwt.refreshToken.domain.RefreshTokenDomain;
import com.back.global.security.jwt.refreshToken.domain.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 리프레시 토큰의 CRUD를 수행합니다.
 *
 * <p>
 * {@code RefreshTokenRepository} 의 구현체를 활용하세요.
 * </p>
 *
 * [Reference] {@link com.back.global.security.jwt.refreshToken.crud.RefreshTokenRepositoryAdapter}
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenManager {

    private final RefreshTokenRepository refreshTokenRepository;

    // Properties
    private final JwtProperty jwtProperty;
    private final RedisProperty redisProperty;

    public RefreshTokenDomain findRefreshTokenByDeviceId(String key) {
        return refreshTokenRepository.findById(key)
                .orElseThrow(() -> new ApiException(ErrorCode.SESSION_EXPIRED));
    }

    public RefreshTokenDomain issueOrUpdateRefreshToken(Long memberId, String deviceId) {
        boolean deviceIdIsValid = deviceId != null && !deviceId.isBlank();

        RefreshTokenFactors factors = generateRtFactors(deviceId, deviceIdIsValid);

        RefreshTokenDomain rt = refreshTokenRepository.findById(factors.key())
                .map(existing -> {
                    existing.check();
                    return existing;
                })
                .orElseGet(() -> generateRefreshTokenDomain(
                        factors.key, memberId, factors.deviceId));

        if (!deviceIdIsValid) invalidateOldSession(memberId, factors.deviceId);

        return saveRefreshToken(rt);
    }

    public void invalidateOldSession(Long memberId, String currentDeviceId) {
        List<RefreshTokenDomain> sessions = findAllRefreshTokens(memberId);

        if (sessions.isEmpty()) return;

        int cnt = sessions.size() - SecurityConstants.MAX_DEVICES_PER_MEMBER;

        List<RefreshTokenDomain> toRemove = sessions.stream()
                .sorted(Comparator
                        .comparing(RefreshTokenDomain::getLastCheckedDate)
                        .thenComparing(RefreshTokenDomain::getCreatedDate))
                .filter(token -> !token.getDeviceId().equals(currentDeviceId))
                .limit(cnt)
                .toList();

        deleteRefreshTokens(toRemove);
    }

    public List<RefreshTokenDomain> findAllRefreshTokens(Long memberId) {
        return refreshTokenRepository.findAllByMemberId(memberId);
    }

    public RefreshTokenDomain saveRefreshToken(RefreshTokenDomain refreshTokenDomain) {
        return refreshTokenRepository.save(refreshTokenDomain);
    }

    public void deleteAllRefreshTokensByMemberId(Long memberId) {
        refreshTokenRepository.deleteAllByMemberId(memberId);
    }

    public void deleteRefreshTokenWithMemberId(String deviceId, Long memberId) {
        String key = getKey(deviceId);

        RefreshTokenDomain existing = refreshTokenRepository.findById(key)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found."));

        if (!existing.getMemberId().equals(memberId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Resource not found.");
        }

        refreshTokenRepository.deleteById(key);
    }

    public void deleteRefreshTokens(List<RefreshTokenDomain> tokens) {
        refreshTokenRepository.deleteRefreshTokenContaining(tokens);
    }

    private RefreshTokenDomain generateRefreshTokenDomain(
            String key,
            Long memberId,
            String deviceId
    ) {
        return RefreshTokenDomain.initial(
                key,
                memberId,
                UUID.randomUUID().toString(),
                deviceId,
                jwtProperty.getRefreshToken().getDuration().toSeconds()
        );
    }

    private String getKey(String deviceId) {
        String base = redisProperty.getRefreshTokenKeyPrefix();

        if (deviceId != null && !deviceId.isBlank()) {
            base += ":" + deviceId;
        }

        return base;
    }

    private RefreshTokenFactors generateRtFactors(String deviceId, boolean deviceIdIsValid) {
        String finalKey;
        String finalDeviceId;

        if (deviceIdIsValid) {
            // deviceId가 유효하면 기존 키 사용 (prefix + ":" + deviceId)
            finalKey = getKey(deviceId);
            finalDeviceId = deviceId;
        } else {
            // deviceId가 유효하지 않으면 새 deviceId 생성 및 키 설정
            do {
                finalDeviceId = UUID.randomUUID().toString();
                finalKey = getKey(finalDeviceId);
            } while (refreshTokenRepository.existsById(finalKey));
        }

        return new RefreshTokenFactors(finalKey, finalDeviceId);
    }

    private record RefreshTokenFactors(
            String key,
            String deviceId
    ) {}
}
