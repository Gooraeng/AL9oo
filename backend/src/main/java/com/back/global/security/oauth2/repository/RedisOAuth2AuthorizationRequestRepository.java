package com.back.global.security.oauth2.repository;

import com.back.global.security.constant.SecurityConstants;
import com.back.global.security.oauth2.util.LinkCodeStateCodec;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 기반 OAuth2 Authorization Request 저장소.
 *
 * <p>HTTP Session 대신 Redis를 사용하여 STATELESS 세션 정책과 호환됩니다.
 * 계정 연동 시 linkCode를 request attribute에 설정합니다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final String OAUTH2_AUTHORIZATION_REQUEST_PREFIX = "oauth2:authRequest:";
    private static final Duration TTL = Duration.ofMinutes(15);
    private final RedisTemplate<String, Object> redisTemplate;

    private final LinkCodeStateCodec linkCodeStateCodec;

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = getStateParameter(request);
        if (state == null) {
            return null;
        }

        String originalState = extractOriginalState(state);
        return getAuthorizationRequest(originalState);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (authorizationRequest == null) {
            removeAuthorizationRequest(request, response);
            return;
        }

        String state = authorizationRequest.getState();
        String originalState = extractOriginalState(state);
        String key = getRedisKey(originalState);

        redisTemplate.opsForValue().set(key, authorizationRequest, TTL);
        log.debug("Saved OAuth2 authorization request: state={}", originalState);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String state = getStateParameter(request);
        if (state == null) {
            return null;
        }

        // state에서 linkCode 추출하여 request attribute에 설정
        String[] decoded = linkCodeStateCodec.decode(state);
        if (decoded != null) {
            String linkCode = decoded[0];
            request.setAttribute(SecurityConstants.LINK_CODE_ATTRIBUTE, linkCode);
            log.debug("Account link detected: linkCode={}", linkCode);
        }

        String originalState = extractOriginalState(state);
        String key = getRedisKey(originalState);

        OAuth2AuthorizationRequest authorizationRequest = getAuthorizationRequest(originalState);
        if (authorizationRequest != null) {
            redisTemplate.delete(key);
            log.debug("Removed OAuth2 authorization request: state={}", originalState);
        }

        return authorizationRequest;
    }

    private String getStateParameter(HttpServletRequest request) {
        return request.getParameter("state");
    }

    private String extractOriginalState(String state) {
        String[] decoded = linkCodeStateCodec.decode(state);
        if (decoded != null) {
            return decoded[1];
        }
        return state;
    }

    private String getRedisKey(String state) {
        return OAUTH2_AUTHORIZATION_REQUEST_PREFIX + state;
    }

    private OAuth2AuthorizationRequest getAuthorizationRequest(String state) {
        String key = getRedisKey(state);
        Object obj = redisTemplate.opsForValue().get(key);

        if (obj instanceof OAuth2AuthorizationRequest oar) return oar;
        return null;
    }
}
