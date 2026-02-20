package com.back.global.security.oauth2.strategy;

import com.back.domain.user.auth.entity.AccountLinkRedisHash;
import com.back.domain.user.auth.useCase.AccountLinkCase;
import com.back.global.security.constant.SecurityConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountLinkStrategy 테스트")
class AccountLinkStrategyTest {

    @Mock
    private AccountLinkCase accountLinkCase;

    @InjectMocks
    private AccountLinkStrategy strategy;

    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private OAuth2UserRequest mockUserRequest(String registrationId) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
                .clientId("client-id")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://example.com/oauth2/authorize")
                .tokenUri("https://example.com/oauth2/token")
                .userInfoUri("https://example.com/oauth2/userinfo")
                .userNameAttributeName("sub")
                .build();

        OAuth2UserRequest userRequest = mock(OAuth2UserRequest.class);
        given(userRequest.getClientRegistration()).willReturn(registration);
        return userRequest;
    }

    @Test
    @DisplayName("canHandle: LINK_CODE_ATTRIBUTE 존재 시 true 반환")
    void canHandle_withLinkCode_returnsTrue() {
        request.setAttribute(SecurityConstants.LINK_CODE_ATTRIBUTE, "test-link-code");
        // canHandle은 userRequest를 사용하지 않으므로 null 전달
        assertThat(strategy.canHandle(null)).isTrue();
    }

    @Test
    @DisplayName("canHandle: LINK_CODE_ATTRIBUTE 없을 시 false 반환")
    void canHandle_withoutLinkCode_returnsFalse() {
        // canHandle은 userRequest를 사용하지 않으므로 null 전달
        assertThat(strategy.canHandle(null)).isFalse();
    }

    @Test
    @DisplayName("processUser: provider mismatch 시 INVALID_LINK_REQUEST 예외 발생 (loadUser 미호출)")
    void processUser_providerMismatch_throwsInvalidLinkRequest() {
        // Given: linkCode는 google 연동 요청이지만 실제 콜백은 discord
        String linkCode = "test-link-code";
        request.setAttribute(SecurityConstants.LINK_CODE_ATTRIBUTE, linkCode);

        AccountLinkRedisHash linkData = AccountLinkRedisHash.builder()
                .memberId(1L)
                .requestedProvider("google")
                .build();
        ReflectionTestUtils.setField(linkData, "linkCode", linkCode);

        given(accountLinkCase.verifyLinkCode(linkCode)).willReturn(linkData);

        OAuth2UserRequest userRequest = mockUserRequest("discord");

        // When & Then: provider 검증이 loadUser() 이전에 실행되므로 HTTP 호출 없이 예외 발생
        assertThatThrownBy(() -> strategy.processUser(userRequest))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> {
                    OAuth2AuthenticationException oauth2Ex = (OAuth2AuthenticationException) ex;
                    assertThat(oauth2Ex.getError().getErrorCode()).isEqualTo("INVALID_LINK_REQUEST");
                });
    }

    @Test
    @DisplayName("processUser: linkCode 없을 시 INVALID_LINK_REQUEST 예외 발생")
    void processUser_noLinkCode_throwsInvalidLinkRequest() {
        // Given: request attribute에 linkCode 없음
        OAuth2UserRequest userRequest = mockUserRequest("google");

        // When & Then
        assertThatThrownBy(() -> strategy.processUser(userRequest))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(ex -> {
                    OAuth2AuthenticationException oauth2Ex = (OAuth2AuthenticationException) ex;
                    assertThat(oauth2Ex.getError().getErrorCode()).isEqualTo("INVALID_LINK_REQUEST");
                });

        verify(accountLinkCase, never()).verifyLinkCode(any());
    }
}
