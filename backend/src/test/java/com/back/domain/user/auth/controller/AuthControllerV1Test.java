package com.back.domain.user.auth.controller;

import com.back.domain.user.auth.dto.request.Oauth2SignUpRequestDto;
import com.back.domain.user.auth.dto.request.SignUpRequestDto;
import com.back.domain.user.auth.dto.response.NickNameCheckResultResponseDto;
import com.back.domain.user.auth.dto.response.SignUpResponseDto;
import com.back.domain.user.auth.entity.AuthAccount;
import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.auth.useCase.Oauth2SignUpOrLoginCase;
import com.back.domain.user.member.entity.Member;
import com.back.domain.user.member.service.MemberService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthControllerV1.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("AuthControllerV1 웹 레이어 테스트")
class AuthControllerV1Test {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private Oauth2SignUpOrLoginCase oAuth2SignUpOrLoginCase;
    @MockitoBean private MemberService memberService;

    private CustomUserDetails activeTestUser;
    private final Long activeMemberId = 1L;

    @Nested
    @DisplayName("공통 테스트")
    class CommonTests {

        @Nested
        @DisplayName("닉네임 중복 검사 테스트")
        class NicknameDuplicationTests {

            @Test
            @DisplayName("성공 - 200 - 중복되지 않은 닉네임 사용 가능")
            void success_200_nickname_available() throws Exception {
                String testNickname = "UniqueNickname";

                given(memberService.checkDisplayNameDuplication(eq(testNickname)))
                        .willReturn(new NickNameCheckResultResponseDto(true));

                mockMvc.perform(get("/api/v1/auth/check-display-name/{name}", testNickname)
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.available").exists())
                        .andExpect(jsonPath("$.available").isBoolean())
                        .andExpect(jsonPath("$.available").value(true));

                verify(memberService).checkDisplayNameDuplication(eq(testNickname));
            }

            @Test
            @DisplayName("실패 - 200 - 중복된 닉네임 확인")
            void fail_200_nickname_already_used() throws Exception {
                String testNickname = "AlreadyUsedNickname";

                given(memberService.checkDisplayNameDuplication(eq(testNickname)))
                        .willReturn(new NickNameCheckResultResponseDto(false));

                mockMvc.perform(get("/api/v1/auth/check-display-name/{name}", testNickname))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.available").exists())
                        .andExpect(jsonPath("$.available").isBoolean())
                        .andExpect(jsonPath("$.available").value(false));

                verify(memberService).checkDisplayNameDuplication(eq(testNickname));
            }
        }
    }

    @Nested
    @DisplayName("Oauth2 테스트")
    class Oauth2MemberTest {

        @Nested
        @DisplayName("POST /api/v1/auth/oauth2/signup")
        class SignUpTests {

            @Test
            @DisplayName("성공 - 200 - Guest 회원 가입 완료")
            void success_signup_guest_member() throws Exception {
                SignUpResponseDto mockResponse = new SignUpResponseDto(
                        "Welcome, CompleteMember!");

                Oauth2SignUpRequestDto originRequest = new Oauth2SignUpRequestDto(
                        "CompleteMember", true);

                given(oAuth2SignUpOrLoginCase.doSignUp(any(SignUpRequestDto.class)))
                        .willReturn(mockResponse);

                mockMvc.perform(post("/api/v1/auth/oauth2/signup")
                                .with(user(activeTestUser))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(originRequest)))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.message").exists())
                        .andExpect(jsonPath("$.message").isString())
                        .andExpect(jsonPath("$.message").value("Welcome, CompleteMember!"));

                verify(oAuth2SignUpOrLoginCase).doSignUp(any(SignUpRequestDto.class));
            }

            @Test
            @DisplayName("실패 - 403 - 필수 약관 미동의")
            void fail_403_essential_terms_not_agreed() throws Exception {
                Oauth2SignUpRequestDto originRequest = new Oauth2SignUpRequestDto(
                        "CompleteMember", false);

                doThrow(new ApiException(ErrorCode.ACCESS_DENIED))
                        .when(oAuth2SignUpOrLoginCase).doSignUp(any(SignUpRequestDto.class));

                mockMvc.perform(post("/api/v1/auth/oauth2/signup")
                                .with(user(activeTestUser))
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(originRequest)))
                        .andDo(print())
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.message").doesNotExist());

                verify(oAuth2SignUpOrLoginCase).doSignUp(any(SignUpRequestDto.class));
            }

            @Test
            @DisplayName("실패 - 404 - 이미 활성화된 회원의 재가입 시도")
            void fail_404_member_already_active() throws Exception {
                Oauth2SignUpRequestDto originRequest = new Oauth2SignUpRequestDto(
                        "CompleteMember", true);

                doThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND))
                        .when(oAuth2SignUpOrLoginCase).doSignUp(any(SignUpRequestDto.class));

                mockMvc.perform(post("/api/v1/auth/oauth2/signup")
                                .with(user(activeTestUser))
                                .contentType(MediaType.APPLICATION_JSON)
                                .accept(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(originRequest)))
                        .andDo(print())
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.message").doesNotExist());

                verify(oAuth2SignUpOrLoginCase).doSignUp(any(SignUpRequestDto.class));
            }

        }

    }

    /**
     * Generate Pending Member
     * Actual Oauth2 login is limited
     */
    @BeforeEach
    void setUp() {
        Member member = Member.builder()
                .displayName("testUser")
                .memberStatus(MemberStatus.ACTIVE)
                .essentialTermsAgreed(true)
                .role(MemberRole.GENERAL)
                .build();
        ReflectionTestUtils.setField(member, "id", activeMemberId);

        AuthAccount account = AuthAccount.builder()
                .member(member)
                .authProvider(AuthProvider.GOOGLE)
                .issuer("google")
                .subject("1")
                .email("activeMember@example.com")
                .build();
        ReflectionTestUtils.setField(account, "id", activeMemberId);

        activeTestUser = new CustomUserDetails(account);
    }
}