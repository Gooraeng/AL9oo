package com.back.global.security.oauth2.strategy;

import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * OAuth2 사용자 인증 처리를 위한 Strategy 인터페이스.
 *
 * <p>일반 로그인과 계정 연동 등 다양한 OAuth2 플로우를 Strategy Pattern으로 처리합니다.
 * 각 구현체는 특정 시나리오에 맞는 사용자 처리 로직을 제공합니다.</p>
 */
public interface Oauth2ProcessingStrategy {

    /**
     * OAuth2 사용자 요청을 처리하고 인증된 사용자 정보를 반환합니다.
     *
     * @param userRequest OAuth2 사용자 요청 정보
     * @return 인증된 OAuth2 사용자 객체
     * @throws OAuth2AuthenticationException 인증 처리 중 오류 발생 시
     */
    OAuth2User processUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException;

    /**
     * 이 Strategy가 주어진 OAuth2 요청을 처리할 수 있는지 확인합니다.
     *
     * @param userRequest OAuth2 사용자 요청 정보
     * @return 처리 가능 여부
     */
    boolean canHandle(OAuth2UserRequest userRequest);
}
