package com.back.domain.user.auth.controller;

import com.back.domain.user.auth.dto.response.AccountLinkIssueResponseDto;
import com.back.domain.user.auth.dto.response.AccountLinkStatusResponseDto;
import com.back.domain.user.auth.entity.AuthAccount;
import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.auth.useCase.AccountLinkCase;
import com.back.domain.user.member.entity.Member;
import com.back.domain.user.member.type.MemberRole;
import com.back.domain.user.member.type.MemberStatus;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.back.global.security.TestSecurityConfig;
import com.back.global.security.member.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountLinkControllerV1.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("AccountLinkControllerV1 웹 레이어 테스트")
class AccountLinkControllerV1Test {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AccountLinkCase accountLinkCase;

    private CustomUserDetails testUserDetails;

    @BeforeEach
    void setUp() {
        Member member = Member.builder()
                .displayName("testUser")
                .memberStatus(MemberStatus.ACTIVE)
                .essentialTermsAgreed(true)
                .role(MemberRole.GENERAL)
                .build();
        ReflectionTestUtils.setField(member, "id", 1L);

        AuthAccount account = AuthAccount.builder()
                .member(member)
                .authProvider(AuthProvider.GOOGLE)
                .issuer("google")
                .subject("123456")
                .email("test@example.com")
                .build();
        ReflectionTestUtils.setField(account, "id", 1L);

        testUserDetails = new CustomUserDetails(account);
    }

    @Nested
    @DisplayName("GET /api/v1/auth/account-link")
    class GetAccountLinkStatusTests {

        @Test
        @DisplayName("성공 - 200 - 계정 상태 리스트 반환")
        void success_return_account_status_list() throws Exception {
            // Given
            List<AccountLinkStatusResponseDto> mockResponse = List.of(
                    new AccountLinkStatusResponseDto("GOOGLE", LocalDateTime.now(), true),
                    new AccountLinkStatusResponseDto("DISCORD", null, false)
            );

            given(accountLinkCase.getCurrentAccountLinkStatus(anyLong(), eq(AuthProvider.GOOGLE)))
                    .willReturn(mockResponse);

            // When & Then
            mockMvc.perform(get("/api/v1/auth/account-link")
                            .with(user(testUserDetails)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].provider").exists())
                    .andExpect(jsonPath("$[0].linkedAt").exists())
                    .andExpect(jsonPath("$[0].currentProvider").exists());
        }

        @Test
        @DisplayName("실패 - 401 - 인증되지 않은 요청")
        void fail_401_not_authenticated() throws Exception {
            // Given: 인증 없음
            // @WebMvcTest 기본 Security는 인증 없는 요청을 401

            // When & Then
            mockMvc.perform(get("/api/v1/auth/account-link"))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/account-link/start")
    class StartAccountLinkingTests {

        @Test
        @DisplayName("성공 - 200 - redirectPath 반환")
        void success_return_redirect_path() throws Exception {
            // Given
            String provider = "DISCORD";
            AccountLinkIssueResponseDto mockResponse = new AccountLinkIssueResponseDto(
                    "/oauth2/authorization/discord?linkCode=test-link-code"
            );

            given(accountLinkCase.startAccountLinking(anyLong(), eq(provider)))
                    .willReturn(mockResponse);

            // When & Then
            mockMvc.perform(post("/api/v1/auth/account-link/start")
                            .param("provider", provider)
                            .with(user(testUserDetails)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.redirectPath").exists())
                    .andExpect(jsonPath("$.redirectPath").value(containsString("discord")))
                    .andExpect(jsonPath("$.redirectPath").value(containsString("linkCode=")));
        }

        @Test
        @DisplayName("실패 - 400 - provider 파라미터 누락")
        void fail_400_provider_param_missing() throws Exception {
            // Given: provider 파라미터 누락

            // When & Then
            mockMvc.perform(post("/api/v1/auth/account-link/start")
                            .with(user(testUserDetails)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패 - 403 - 접근 거부 예외 발생")
        void fail_403_access_denied_exception() throws Exception {
            // Given
            String provider = "GOOGLE";

            given(accountLinkCase.startAccountLinking(anyLong(), eq(provider)))
                    .willThrow(new ApiException(ErrorCode.ACCESS_DENIED));

            // When & Then
            mockMvc.perform(post("/api/v1/auth/account-link/start")
                            .param("provider", provider)
                            .with(user(testUserDetails)))
                    .andDo(print())
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/auth/account-link/unlink")
    class UnlinkAccountTests {

        @Test
        @DisplayName("성공 - 200 - 계정 연동 해제")
        void success_unlink_account() throws Exception {
            // Given: google로 가입한 member
            doNothing().when(accountLinkCase).unlinkAccount(anyLong(), eq("discord"));

            // When & Then
            mockMvc.perform(delete("/api/v1/auth/account-link/unlink?provider=discord")
                            .with(user(testUserDetails)))
                    .andDo(print())
                    .andExpect(status().isOk());

            verify(accountLinkCase).unlinkAccount(anyLong(), eq("discord"));
        }

        @Test
        @DisplayName("실패 - 403 - 단일 계정으로 연동 해제 불가")
        void fail_403_unlink_single_account() throws Exception {
            // Given: google로 가입한 member
            doThrow(new ApiException(ErrorCode.ACCESS_DENIED, "You must link at least one provider."))
                    .when(accountLinkCase).unlinkAccount(anyLong(), eq("google"));

            // When & Then
            mockMvc.perform(delete("/api/v1/auth/account-link/unlink?provider=google")
                            .with(user(testUserDetails)))
                    .andDo(print())
                    .andExpect(status().isForbidden());

            verify(accountLinkCase).unlinkAccount(anyLong(), eq("google"));
        }
    }
}
