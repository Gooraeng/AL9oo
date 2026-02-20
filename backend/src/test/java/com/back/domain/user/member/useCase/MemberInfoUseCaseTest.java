package com.back.domain.user.member.useCase;

import com.back.domain.user.auth.dto.response.NickNameCheckResultResponseDto;
import com.back.domain.user.member.dto.MemberUpdateRequestDto;
import com.back.domain.user.member.dto.response.PrivateMemberDataResponseDto;
import com.back.domain.user.member.dto.response.PublicMemberDataResponseDto;
import com.back.domain.user.member.entity.Member;
import com.back.domain.user.member.service.MemberService;
import com.back.domain.user.member.type.MemberRole;
import com.back.domain.user.member.type.MemberStatus;
import com.back.global.exception.ApiException;
import com.back.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("MemberInfoUseCase 테스트")
class MemberInfoUseCaseTest {

    @Mock private MemberService memberService;

    @InjectMocks private MemberInfoUseCase memberInfoUseCase;

    private Member testMember;

    @BeforeEach
    void setUp() {
        testMember = Member.builder()
                .displayName("testUser")
                .role(MemberRole.GENERAL)
                .memberStatus(MemberStatus.ACTIVE)
                .essentialTermsAgreed(true)
                .build();
        ReflectionTestUtils.setField(testMember, "id", 1L);
    }

    @Nested
    @DisplayName("getPrivateMemberInfo() 테스트")
    class GetPrivateMemberInfoTests {

        @Test
        @DisplayName("성공 - 활성 회원 조회 시 PrivateMemberDataResponseDto 반환, id 포함 확인")
        void success_active_member_returns_private_dto() {
            // Given
            given(memberService.findMemberById(1L)).willReturn(testMember);

            // When
            PrivateMemberDataResponseDto result = memberInfoUseCase.getPrivateMemberInfo(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.displayName()).isEqualTo("testUser");
            verify(memberService).findMemberById(1L);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 회원 조회 시 RESOURCE_NOT_FOUND 예외 발생")
        void fail_nonexistent_member_throws_exception() {
            // Given
            given(memberService.findMemberById(999L))
                    .willThrow(new ApiException(ErrorCode.RESOURCE_NOT_FOUND));

            // When & Then
            assertThatThrownBy(() -> memberInfoUseCase.getPrivateMemberInfo(999L))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

            verify(memberService).findMemberById(999L);
        }

        @Test
        @DisplayName("실패 - isDeleted()가 true인 회원 조회 시 RESOURCE_NOT_FOUND 예외 발생")
        void fail_deleted_member_throws_exception() {
            // Given
            testMember.markAsDeleted();
            given(memberService.findMemberById(1L)).willReturn(testMember);

            // When & Then
            assertThatThrownBy(() -> memberInfoUseCase.getPrivateMemberInfo(1L))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

            verify(memberService).findMemberById(1L);
        }
    }

    @Nested
    @DisplayName("getPublicMemberInfo() 테스트")
    class GetPublicMemberInfoTests {

        @Test
        @DisplayName("성공 - 활성 회원 조회 시 PublicMemberDataResponseDto 반환, id 필드 없음 확인")
        void success_active_member_returns_public_dto_without_id() {
            // Given
            given(memberService.findMemberById(1L)).willReturn(testMember);

            // When
            PublicMemberDataResponseDto result = memberInfoUseCase.getPublicMemberInfo(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.displayName()).isEqualTo("testUser");
            // PublicMemberDataResponseDto에는 id 필드가 없음
            verify(memberService).findMemberById(1L);
        }
    }

    @Nested
    @DisplayName("updateMemberInfo() 테스트")
    class UpdateMemberInfoTests {

        @Test
        @DisplayName("성공 - displayName이 다르고 중복 없음 → saveMember() 호출됨")
        void success_nickname_changed_saves_member() {
            // Given
            MemberUpdateRequestDto dto = new MemberUpdateRequestDto("newNickname");
            given(memberService.findMemberById(1L)).willReturn(testMember);
            // available=true means the name is not a duplicate (기존 Oauth2SignUpOrLoginCaseTest 컨벤션 준수)
            given(memberService.checkDisplayNameDuplication("newNickname"))
                    .willReturn(new NickNameCheckResultResponseDto(true));

            // When
            memberInfoUseCase.updateMemberInfo(1L, dto);

            // Then
            assertThat(testMember.getDisplayName()).isEqualTo("newNickname");
            verify(memberService).findMemberById(1L);
            verify(memberService).checkDisplayNameDuplication("newNickname");
            verify(memberService).saveMember(testMember);
        }

        @Test
        @DisplayName("성공 - displayName이 동일 → saveMember() 호출 안됨")
        void success_same_nickname_does_not_save() {
            // Given
            MemberUpdateRequestDto dto = new MemberUpdateRequestDto("testUser");
            given(memberService.findMemberById(1L)).willReturn(testMember);

            // When
            memberInfoUseCase.updateMemberInfo(1L, dto);

            // Then
            verify(memberService).findMemberById(1L);
            verify(memberService, never()).checkDisplayNameDuplication(any());
            verify(memberService, never()).saveMember(any());
        }

        @Test
        @DisplayName("실패 - 삭제된 회원 정보 변경 시 RESOURCE_NOT_FOUND 예외 발생")
        void fail_deleted_member_throws_exception() {
            // Given
            testMember.markAsDeleted();
            MemberUpdateRequestDto dto = new MemberUpdateRequestDto("newNickname");
            given(memberService.findMemberById(1L)).willReturn(testMember);

            // When & Then
            assertThatThrownBy(() -> memberInfoUseCase.updateMemberInfo(1L, dto))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

            verify(memberService).findMemberById(1L);
            verify(memberService, never()).saveMember(any());
        }

        @Test
        @DisplayName("실패 - 닉네임 중복 시 CONFLICT 예외 발생")
        void fail_duplicate_nickname_throws_conflict() {
            // Given
            MemberUpdateRequestDto dto = new MemberUpdateRequestDto("takenNickname");
            given(memberService.findMemberById(1L)).willReturn(testMember);
            // available=false means the name IS a duplicate (기존 컨벤션 준수)
            given(memberService.checkDisplayNameDuplication("takenNickname"))
                    .willReturn(new NickNameCheckResultResponseDto(false));

            // When & Then
            assertThatThrownBy(() -> memberInfoUseCase.updateMemberInfo(1L, dto))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONFLICT);

            verify(memberService).findMemberById(1L);
            verify(memberService).checkDisplayNameDuplication("takenNickname");
            verify(memberService, never()).saveMember(any());
        }
    }

    @Nested
    @DisplayName("withdrawUsingService() 테스트")
    class WithdrawUsingServiceTests {

        @Test
        @DisplayName("성공 - memberService.withdrawMember(memberId) 호출 위임 확인")
        void success_delegates_to_member_service() {
            // Given
            doNothing().when(memberService).withdrawMember(1L);

            // When
            memberInfoUseCase.withdrawUsingService(1L);

            // Then
            verify(memberService).withdrawMember(1L);
        }
    }
}
