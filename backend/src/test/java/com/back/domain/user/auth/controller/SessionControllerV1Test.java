package com.back.domain.user.auth.controller;

import com.back.domain.user.auth.dto.response.SessionStatusResponseDto;
import com.back.domain.user.auth.service.SessionManagementService;
import com.back.domain.user.member.type.MemberRole;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import com.back.global.security.TestSecurityConfig;
import com.back.global.security.jwt.JwtCookieHelper;
import com.back.global.security.jwt.dto.CookieIssueResult;
import com.back.global.security.member.CustomUserDetails;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SessionControllerV1.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("SessionControllerV1 웹 레이어 테스트")
class SessionControllerV1Test {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SessionManagementService sessionManagementService;

    private static final Long TEST_MEMBER_ID = 1L;
    private static final Long ANOTHER_TEST_MEMBER_ID = 2L;
    private static final String TEST_DEVICE_ID = "device-123";

    private CustomUserDetails testUser;
    private CustomUserDetails anotherTestUser;
    private CookieIssueResult mockCookieResult;
    private List<SessionStatusResponseDto> mockSessionList;

    private Cookie mockAccessCookie;
    private Cookie mockRefreshCookie;
    private final String mockAccessToken = "dev-pat";
    private final String mockRefreshToken = "dev-prt";

    @BeforeEach
    void setUp() {
        testUser = CustomUserDetails.fromJwt(TEST_MEMBER_ID, MemberRole.GENERAL);
        anotherTestUser = CustomUserDetails.fromJwt(ANOTHER_TEST_MEMBER_ID, MemberRole.GENERAL);

        mockAccessCookie = new Cookie(mockAccessToken, "mock-access-token");
        mockRefreshCookie = new Cookie(mockRefreshToken, "mock-refresh-token");
        mockCookieResult = new CookieIssueResult(mockAccessCookie, mockRefreshCookie);

        mockSessionList = List.of(
                new SessionStatusResponseDto(TEST_DEVICE_ID, LocalDateTime.now(), true),
                new SessionStatusResponseDto("device-456", LocalDateTime.now().minusDays(1), false)
        );
    }

    @Nested
    @DisplayName("POST /api/v1/auth/session/logout")
    class LogoutTest {

        @Test
        @DisplayName("성공 - 200 - 인증된 사용자 로그아웃 시 쿠키 무효화")
        void success_logout() throws Exception {
            // given
            Cookie invalidAccessCookie = new Cookie(mockAccessToken, "");
            invalidAccessCookie.setMaxAge(0);
            Cookie invalidRefreshCookie = new Cookie(mockRefreshToken, "");
            invalidRefreshCookie.setMaxAge(0);

            doAnswer(invocation -> {
                HttpServletResponse response = invocation.getArgument(2);
                response.addCookie(invalidAccessCookie);
                response.addCookie(invalidRefreshCookie);
                return null;
            }).when(sessionManagementService).logoutCurrentDevice(eq(TEST_MEMBER_ID), any(HttpServletRequest.class), any(HttpServletResponse.class));

            // when & then
            mockMvc.perform(post("/api/v1/auth/session/logout")
                            .with(user(testUser)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Logout successful"))
                    .andExpect(cookie().exists(mockAccessToken))
                    .andExpect(cookie().maxAge(mockAccessToken, 0))
                    .andExpect(cookie().exists(mockRefreshToken))
                    .andExpect(cookie().maxAge(mockRefreshToken, 0));

            verify(sessionManagementService).logoutCurrentDevice(eq(TEST_MEMBER_ID), any(HttpServletRequest.class), any(HttpServletResponse.class));
        }

        @Test
        @DisplayName("실패 - 401 - 비인증 요청")
        void fail_401_unauthenticated() throws Exception {
            mockMvc.perform(post("/api/v1/auth/session/logout"))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/session/refresh-token")
    class RefreshTokenTest {

        @Test
        @DisplayName("성공 - 200 - 유효한 refresh token으로 토큰 갱신")
        void success_rotate_tokens() throws Exception {
            // given
            when(sessionManagementService.rotateTokens(any(HttpServletRequest.class)))
                    .thenReturn(mockCookieResult);

            // when & then
            mockMvc.perform(post("/api/v1/auth/session/refresh-token")
                            .cookie(mockRefreshCookie))
                    .andExpect(status().isOk())
                    .andExpect(cookie().exists(mockAccessToken))
                    .andExpect(cookie().exists(mockRefreshToken));

            verify(sessionManagementService).rotateTokens(any(HttpServletRequest.class));
        }

        @Test
        @DisplayName("실패 - 401 - Refresh Token 없어 세션 만료 에러 발생")
        void fail_401_no_refresh_token() throws Exception {
            // given
            when(sessionManagementService.rotateTokens(any(HttpServletRequest.class)))
                    .thenThrow(new ApiException(ErrorCode.SESSION_EXPIRED));

            // when & then
            mockMvc.perform(post("/api/v1/auth/session/refresh-token"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/session/invalidate")
    class InvalidateSessionTests {

        @Test
        @DisplayName("성공 - 200 - 특정 세션 로그아웃")
        void success_invalidate_session() throws Exception{
            mockMvc.perform(delete("/api/v1/auth/session/invalidate")
                            .with(user(testUser))
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .param("deviceId", TEST_DEVICE_ID))
                    .andDo(print())
                    .andExpect(status().isOk());

        }

        @Test
        @DisplayName("실패 - 401 - 인증되지 않은 사용자")
        void fail_401_unauthenticated() throws Exception {
            mockMvc.perform(delete("/api/v1/auth/session/invalidate")
                            .param("deviceId", TEST_DEVICE_ID))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패 - 400 - deviceId 파라미터 누락")
        void fail_400_missing_device_id() throws Exception {
            mockMvc.perform(delete("/api/v1/auth/session/invalidate")
                            .with(user(testUser)))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("실패 - 404 - 세션 삭제 중 예외 발생")
        void fail_404_remove_device_exception() throws Exception {
            // given
            doThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND))
                    .when(sessionManagementService)
                    .removeDevice(eq(TEST_MEMBER_ID), eq(TEST_DEVICE_ID), any(HttpServletRequest.class));

            // when & then
            mockMvc.perform(delete("/api/v1/auth/session/invalidate")
                            .with(user(testUser))
                            .param("deviceId", TEST_DEVICE_ID))
                    .andDo(print())
                    .andExpect(status().isNotFound());

            verify(sessionManagementService)
                    .removeDevice(eq(TEST_MEMBER_ID), eq(TEST_DEVICE_ID), any(HttpServletRequest.class));
        }

        @Test
        @DisplayName("실패 - 404 - 다른 멤버가 세션 삭제 시도")
        void fail_404_different_member_remove_device() throws Exception {
            // given
            doThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND))
                    .when(sessionManagementService)
                    .removeDevice(eq(ANOTHER_TEST_MEMBER_ID), eq(TEST_DEVICE_ID), any(HttpServletRequest.class));

            // when & then
            mockMvc.perform(delete("/api/v1/auth/session/invalidate")
                            .with(user(anotherTestUser))
                            .param("deviceId", TEST_DEVICE_ID))
                    .andDo(print())
                    .andExpect(status().isNotFound());

            verify(sessionManagementService)
                    .removeDevice(eq(ANOTHER_TEST_MEMBER_ID), eq(TEST_DEVICE_ID), any(HttpServletRequest.class));
        }

    }

    @Nested
    @DisplayName("POST /api/v1/auth/session/invalidate/all")
    class InvalidateAllSessionTests {

        @Test
        @DisplayName("성공 - 200 - 모든 세션 로그아웃")
        void success_invalidate_all_sessions() throws Exception{
            mockMvc.perform(delete("/api/v1/auth/session/invalidate/all")
                            .with(user(testUser))
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("실패 - 401 - 인증되지 않은 사용자")
        void fail_401_unauthenticated() throws Exception {
            mockMvc.perform(delete("/api/v1/auth/session/invalidate/all"))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패 - 404 - 세션 삭제 중 예외 발생")
        void fail_404_remove_all_device_exception() throws Exception {
            // given
            doThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND))
                    .when(sessionManagementService)
                    .cleanUpAllDevices(TEST_MEMBER_ID);

            // when & then
            mockMvc.perform(delete("/api/v1/auth/session/invalidate/all")
                            .with(user(testUser))
                            .param("deviceId", TEST_DEVICE_ID))
                    .andDo(print())
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("실패 - 404 - 다른 멤버가 세션 삭제 시도")
        void fail_404_different_member_remove_all_device() throws Exception {
            // given
            doThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND))
                    .when(sessionManagementService)
                    .cleanUpAllDevices(ANOTHER_TEST_MEMBER_ID);

            // when & then
            mockMvc.perform(delete("/api/v1/auth/session/invalidate/all")
                            .with(user(anotherTestUser))
                            .param("deviceId", TEST_DEVICE_ID))
                    .andDo(print())
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/session")
    class GetAllSessionTests {

        @Test
        @DisplayName("성공 - 200 - 멤버의 세션 현황 반환")
        void success_member_current_session() throws Exception {
            given(sessionManagementService.getAllExistingSession(eq(TEST_MEMBER_ID), any(HttpServletRequest.class)))
                    .willReturn(mockSessionList);

            ResultActions resultActions = mockMvc.perform(get("/api/v1/auth/session")
                            .with(user(testUser)))
                    .andDo(print())
                    .andExpect(status().isOk());

            for (int i = 0; i < mockSessionList.size(); i++) {
                SessionStatusResponseDto dto = mockSessionList.get(i);
                boolean currentSession = dto.currentSession();

                if (currentSession) {
                    resultActions
                            .andExpect(jsonPath("$[%d].currentSession".formatted(i)).value(true))
                            .andExpect(jsonPath("$[%d].deviceId".formatted(i)).value(TEST_DEVICE_ID));
                }
            }

            verify(sessionManagementService)
                    .getAllExistingSession(eq(TEST_MEMBER_ID), any(HttpServletRequest.class));
        }

        @Test
        @DisplayName("실패 - 401 - 로그인하지 않은 사용자의 요청에 예외 반환")
        void fail_401_unauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/auth/session"))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패 - 404 - 활성 세션이 없음")
        void fail_404_no_active_session() throws Exception {
            given(sessionManagementService
                    .getAllExistingSession(any(Long.class), any(HttpServletRequest.class)))
                    .willThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

            mockMvc.perform(get("/api/v1/auth/session")
                            .with(user(testUser)))
                    .andDo(print())
                    .andExpect(status().isNotFound());

            verify(sessionManagementService).
                    getAllExistingSession(any(Long.class), any(HttpServletRequest.class));
        }

    }

}