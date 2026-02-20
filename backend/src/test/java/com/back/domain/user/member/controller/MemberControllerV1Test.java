package com.back.domain.user.member.controller;

import com.back.domain.user.auth.entity.AuthAccount;
import com.back.domain.user.auth.type.AuthProvider;
import com.back.domain.user.member.dto.MemberUpdateRequestDto;
import com.back.domain.user.member.dto.response.PrivateMemberDataResponseDto;
import com.back.domain.user.member.dto.response.PublicMemberDataResponseDto;
import com.back.domain.user.member.entity.Member;
import com.back.domain.user.member.type.MemberRole;
import com.back.domain.user.member.type.MemberStatus;
import com.back.domain.user.member.useCase.MemberInfoUseCase;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberControllerV1.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
@DisplayName("MemberControllerV1 웹 레이어 테스트")
class MemberControllerV1Test {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private MemberInfoUseCase memberInfoUseCase;

    private final Long testId = 1L;

    private CustomUserDetails testUserDetails;

    private final PrivateMemberDataResponseDto mockResponseDto = new PrivateMemberDataResponseDto(
            testId,
            "displayName",
            "profileUrl",
            LocalDateTime.MIN
    );

    @BeforeEach
    void setUp() {
        Member member = Member.builder()
                .displayName("testUser")
                .memberStatus(MemberStatus.ACTIVE)
                .essentialTermsAgreed(true)
                .role(MemberRole.GENERAL)
                .build();
        ReflectionTestUtils.setField(member, "id", testId);

        AuthAccount account = AuthAccount.builder()
                .member(member)
                .authProvider(AuthProvider.GOOGLE)
                .issuer("google")
                .subject("123456")
                .email("test@example.com")
                .build();
        ReflectionTestUtils.setField(account, "id", testId);

        testUserDetails = new CustomUserDetails(account);
    }

    @Nested
    @DisplayName("GET /api/v1/member/me")
    class GetPrivateMemberInfoTests {

        private final String baseEndpoint = "/api/v1/member/me";

        @Test
        @DisplayName("성공 - 200 - 내 정보 조회 성공")
        void success_get_member_info() throws Exception {
            doReturn(mockResponseDto).when(memberInfoUseCase).getPrivateMemberInfo(eq(testId));

            ResultActions resultActions = mockMvc.perform(get(baseEndpoint)
                    .with(user(testUserDetails))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
            );

            resultActions
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(testId));

            verify(memberInfoUseCase).getPrivateMemberInfo(eq(testId));
        }

        @Test
        @DisplayName("실패 - 401 - 로그인하지 않은 유저가 조회 시 실패")
        void fail_unauthenticated_get_member_info() throws Exception {
            mockMvc.perform(get(baseEndpoint)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패 - 404 - 멤버가 존재하지 않거나 삭제됨")
        void fail_member_not_exists() throws Exception {
            doThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND))
                    .when(memberInfoUseCase).getPrivateMemberInfo(testId);

            mockMvc.perform(get(baseEndpoint)
                            .with(user(testUserDetails))
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isNotFound());

            verify(memberInfoUseCase).getPrivateMemberInfo(testId);
        }
    }

    @Nested
    @DisplayName("Patch /api/v1/member/me")
    class UpdateMemberInfoTests {

        private final String baseEndpoint = "/api/v1/member/me";

        @Test
        @DisplayName("성공 - 200 - 멤버 정보 변경 성공")
        void success_update_member_info() throws Exception {
            doNothing().when(memberInfoUseCase)
                    .updateMemberInfo(eq(testId), any(MemberUpdateRequestDto.class));

            mockMvc.perform(patch(baseEndpoint)
                            .with(user(testUserDetails))
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new MemberUpdateRequestDto("newDisplayName"))
                            ))
                    .andDo(print())
                    .andExpect(status().isOk());

            verify(memberInfoUseCase).updateMemberInfo(eq(testId), any(MemberUpdateRequestDto.class));
        }

        @Test
        @DisplayName("실패 - 401 - 로그인된 멤버 정보가 없어 실패")
        void fail_unauthenticated_update_member_info() throws Exception {
            mockMvc.perform(patch(baseEndpoint)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new MemberUpdateRequestDto("newDisplayName"))
                            ))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패 - 404 - 멤버가 삭제되었거나 존재하지 않음")
        void fail_member_not_exists() throws Exception {
            doThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND))
                    .when(memberInfoUseCase).updateMemberInfo(eq(testId), any(MemberUpdateRequestDto.class));

            mockMvc.perform(patch(baseEndpoint)
                            .with(user(testUserDetails))
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new MemberUpdateRequestDto("newDisplayName"))
                            ))
                    .andDo(print())
                    .andExpect(status().isNotFound());

            verify(memberInfoUseCase).updateMemberInfo(eq(testId), any(MemberUpdateRequestDto.class));
        }

        @Test
        @DisplayName("실패 - 409 - 중복된 닉네임으로 변경 시도하여 실패")
        void fail_duplicate_nickname_update_member_info() throws Exception {
            doThrow(new ApiException(ErrorCode.CONFLICT))
                    .when(memberInfoUseCase).updateMemberInfo(eq(testId), any(MemberUpdateRequestDto.class));

            mockMvc.perform(patch(baseEndpoint)
                            .with(user(testUserDetails))
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new MemberUpdateRequestDto("displayName"))
                            ))
                    .andDo(print())
                    .andExpect(status().isConflict());

            verify(memberInfoUseCase).updateMemberInfo(eq(testId), any(MemberUpdateRequestDto.class));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/member/me")
    class DeleteMemberWithdrawalTests {

        private final String baseEndpoint = "/api/v1/member/me";

        @Test
        @DisplayName("성공 - 200 - 서비스 탈퇴 성공")
        void success_withdrawal() throws Exception {
            doNothing().when(memberInfoUseCase)
                    .withdrawUsingService(eq(testId));

            mockMvc.perform(delete(baseEndpoint)
                            .with(user(testUserDetails)))
                    .andDo(print())
                    .andExpect(status().isOk());

            verify(memberInfoUseCase).withdrawUsingService(eq(testId));
        }

        @Test
        @DisplayName("실패 - 401 - 로그인 되어 있지 않아 실패")
        void fail_unauthenticated_withdrawal() throws Exception {
            doThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND))
                    .when(memberInfoUseCase).withdrawUsingService(testId);

            mockMvc.perform(delete(baseEndpoint))
                    .andDo(print())
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("실패 - 409 - 이미 정지되어 있는 유저를 다시 정지함")
        void fail_member_already_withdrew() throws Exception {
            doThrow(new ApiException(ErrorCode.CONFLICT))
                    .when(memberInfoUseCase).withdrawUsingService(testId);

            mockMvc.perform(delete(baseEndpoint)
                            .with(user(testUserDetails)))
                    .andDo(print())
                    .andExpect(status().isConflict());

            verify(memberInfoUseCase).withdrawUsingService(eq(testId));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/member/{targetMemberId}")
    class GetPublicMemberInfoTests {

        private final String baseEndpoint = "/api/v1/member/";

        @Test
        @DisplayName("성공 - 200 - 특정 멤버 공개 정보 조회 성공")
        void success_get_public_member_info() throws Exception {
            Long targetMemberId = 1L;
            String endpoint = baseEndpoint + targetMemberId;

            given(memberInfoUseCase.getPublicMemberInfo(targetMemberId))
                    .willReturn(PublicMemberDataResponseDto.convertToPublic(mockResponseDto));

            mockMvc.perform(get(endpoint)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").doesNotExist());

            verify(memberInfoUseCase).getPublicMemberInfo(targetMemberId);
        }

        @Test
        @DisplayName("실패 - 404 - 멤버가 존재하지 않거나 삭제됨")
        void fail_member_not_exists() throws Exception {
            Long targetMemberId = 1L;
            String endpoint = baseEndpoint + targetMemberId;

            given(memberInfoUseCase.getPublicMemberInfo(targetMemberId))
                    .willThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

            mockMvc.perform(get(endpoint)
                            .contentType(MediaType.APPLICATION_JSON)
                            .accept(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isNotFound());

            verify(memberInfoUseCase).getPublicMemberInfo(targetMemberId);
        }
    }
}