package com.back.global.security.oauth2.service;

import com.back.global.security.oauth2.strategy.AccountLinkStrategy;
import com.back.global.security.oauth2.strategy.LoginStrategy;
import com.back.global.security.oauth2.strategy.Oauth2ProcessingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Strategy Pattern을 사용하여 OAuth2 사용자 인증을 처리하는 서비스.
 *
 * <p>등록된 모든 {@link Oauth2ProcessingStrategy} 구현체를 조회하고,
 * 현재 요청에 적합한 Strategy를 선택하여 위임합니다.</p>
 *
 * <p>지원하는 Strategy:</p>
 * <ul>
 *   <li>{@link LoginStrategy} - 일반 로그인</li>
 *   <li>{@link AccountLinkStrategy} - 계정 연동</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StrategyOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final List<Oauth2ProcessingStrategy> strategies;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();

        log.debug("Processing OAuth2 user request for provider: {}", registrationId);

        // 적합한 Strategy 찾기
        Oauth2ProcessingStrategy strategy = strategies.stream()
                .filter(s -> s.canHandle(userRequest))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("No OAuth2 processing strategy found for provider: {}", registrationId);
                    return new OAuth2AuthenticationException(
                            new OAuth2Error("NO_STRATEGY_FOUND",
                                    "No OAuth2 processing strategy found for the request",
                                    null)
                    );
                });

        log.debug("Selected strategy: {}", strategy.getClass().getSimpleName());

        // Strategy에 위임
        return strategy.processUser(userRequest);
    }
}
