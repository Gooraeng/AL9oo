package com.back.domain.user.member.service;

import com.back.domain.user.auth.dto.response.NickNameCheckResultResponseDto;
import com.back.domain.user.member.entity.Member;
import com.back.domain.user.member.repository.MemberRepository;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
@DisplayName("MemberService 테스트")
class MemberServiceTest {

    @Mock private MemberRepository memberRepository;

    @InjectMocks private MemberService memberService;

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
    @DisplayName("findMemberById() 테스트")
    class FindMemberByIdTests {

        @Test
        @DisplayName("성공 - 존재하는 회원 조회 시 Member 반환")
        void success_find_existing_member() {
            // Given
            given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));

            // When
            Member result = memberService.findMemberById(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getDisplayName()).isEqualTo("testUser");
            verify(memberRepository).findById(1L);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 ID 조회 시 RESOURCE_NOT_FOUND 예외 발생")
        void fail_nonexistent_member_throws_exception() {
            // Given
            given(memberRepository.findById(999L)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> memberService.findMemberById(999L))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

            verify(memberRepository).findById(999L);
        }
    }

    @Nested
    @DisplayName("findMemberByValidation() 테스트")
    class FindMemberByValidationTests {

        @Test
        @DisplayName("성공 - 활성 회원 조회 시 Member 반환")
        void success_find_active_member() {
            // Given
            given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));

            // When
            Member result = memberService.findMemberByValidation(1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.actuallyInActive()).isFalse();
            verify(memberRepository).findById(1L);
        }

        @Test
        @DisplayName("실패 - 비활성 회원 조회 시 RESOURCE_NOT_FOUND 예외 발생")
        void fail_inactive_member_throws_exception() {
            // Given
            Member inactiveMember = Member.builder()
                    .displayName("inactiveUser")
                    .role(MemberRole.GENERAL)
                    .memberStatus(MemberStatus.BANNED)
                    .essentialTermsAgreed(true)
                    .build();
            ReflectionTestUtils.setField(inactiveMember, "id", 2L);

            given(memberRepository.findById(2L)).willReturn(Optional.of(inactiveMember));

            // When & Then
            assertThatThrownBy(() -> memberService.findMemberByValidation(2L))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

            verify(memberRepository).findById(2L);
        }
    }

    @Nested
    @DisplayName("memberValidation() 테스트")
    class MemberValidationTests {

        @Test
        @DisplayName("성공 - actuallyInActive()가 false인 회원은 예외 없이 통과")
        void success_active_member_passes_validation() {
            // Given - testMember는 ACTIVE, essentialTermsAgreed=true, not deleted

            // When & Then (예외 없음)
            memberService.memberValidation(testMember);
        }

        @Test
        @DisplayName("실패 - actuallyInActive()가 true인 회원은 RESOURCE_NOT_FOUND 예외 발생")
        void fail_inactive_member_throws_exception() {
            // Given
            Member inactiveMember = Member.builder()
                    .displayName("inactiveUser")
                    .role(MemberRole.GENERAL)
                    .memberStatus(MemberStatus.INACTIVE)
                    .essentialTermsAgreed(true)
                    .build();

            // When & Then
            assertThatThrownBy(() -> memberService.memberValidation(inactiveMember))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("실패 - 삭제된 회원은 RESOURCE_NOT_FOUND 예외 발생")
        void fail_deleted_member_throws_exception() {
            // Given
            testMember.markAsDeleted();

            // When & Then
            assertThatThrownBy(() -> memberService.memberValidation(testMember))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("checkDisplayNameDuplication() 테스트")
    class CheckDisplayNameDuplicationTests {

        @Test
        @DisplayName("중복 있음 - existsMemberByDisplayName()이 true 반환 시 available=true 반환")
        void duplicate_exists_returns_available_true() {
            // Given
            given(memberRepository.existsMemberByDisplayName("takenName")).willReturn(true);

            // When
            NickNameCheckResultResponseDto result = memberService.checkDisplayNameDuplication("takenName");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.available()).isTrue();
            verify(memberRepository).existsMemberByDisplayName("takenName");
        }

        @Test
        @DisplayName("중복 없음 - existsMemberByDisplayName()이 false 반환 시 available=false 반환")
        void no_duplicate_returns_available_false() {
            // Given
            given(memberRepository.existsMemberByDisplayName("freeName")).willReturn(false);

            // When
            NickNameCheckResultResponseDto result = memberService.checkDisplayNameDuplication("freeName");

            // Then
            assertThat(result).isNotNull();
            assertThat(result.available()).isFalse();
            verify(memberRepository).existsMemberByDisplayName("freeName");
        }
    }

    @Nested
    @DisplayName("findByIdWithAuthAccounts() 테스트")
    class FindByIdWithAuthAccountsTests {

        @Test
        @DisplayName("성공 - 회원 + AuthAccounts 포함하여 반환")
        void success_find_member_with_auth_accounts() {
            // Given
            given(memberRepository.findByIdWithAuthAccounts(1L)).willReturn(Optional.of(testMember));

            // When
            Optional<Member> result = memberService.findByIdWithAuthAccounts(1L);

            // Then
            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo(1L);
            verify(memberRepository).findByIdWithAuthAccounts(1L);
        }

        @Test
        @DisplayName("성공 - 존재하지 않는 ID 조회 시 Optional.empty() 반환")
        void success_nonexistent_id_returns_empty_optional() {
            // Given
            given(memberRepository.findByIdWithAuthAccounts(999L)).willReturn(Optional.empty());

            // When
            Optional<Member> result = memberService.findByIdWithAuthAccounts(999L);

            // Then
            assertThat(result).isEmpty();
            verify(memberRepository).findByIdWithAuthAccounts(999L);
        }
    }

    @Nested
    @DisplayName("createMember() 테스트")
    class CreateMemberTests {

        @Test
        @DisplayName("성공 - 파라미터로 Member 빌드 후 저장하여 반환")
        void success_create_and_return_saved_member() {
            // Given
            Member savedMember = Member.builder()
                    .displayName("newUser")
                    .role(MemberRole.GUEST)
                    .memberStatus(MemberStatus.ACTIVE)
                    .essentialTermsAgreed(false)
                    .build();
            ReflectionTestUtils.setField(savedMember, "id", 2L);

            given(memberRepository.save(any(Member.class))).willReturn(savedMember);

            // When
            Member result = memberService.createMember(
                    "newUser",
                    MemberRole.GUEST,
                    MemberStatus.ACTIVE,
                    false
            );

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getDisplayName()).isEqualTo("newUser");
            assertThat(result.getRole()).isEqualTo(MemberRole.GUEST);
            assertThat(result.getMemberStatus()).isEqualTo(MemberStatus.ACTIVE);
            assertThat(result.isEssentialTermsAgreed()).isFalse();
            verify(memberRepository).save(any(Member.class));
        }
    }

    @Nested
    @DisplayName("withdrawMember() 테스트")
    class WithdrawMemberTests {

        @Test
        @DisplayName("성공 - 회원 조회 후 withdraw() 호출, markAsDeleted() 반영")
        void success_withdraw_member() {
            // Given
            given(memberRepository.findById(1L)).willReturn(Optional.of(testMember));
            given(memberRepository.save(testMember)).willReturn(testMember);

            // When
            memberService.withdrawMember(1L);

            // Then
            assertThat(testMember.isDeleted()).isTrue();
            verify(memberRepository).findById(1L);
            verify(memberRepository).save(testMember);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 ID 탈퇴 시 RESOURCE_NOT_FOUND 예외 발생")
        void fail_nonexistent_member_throws_exception() {
            // Given
            given(memberRepository.findById(999L)).willReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> memberService.withdrawMember(999L))
                    .isInstanceOf(ApiException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESOURCE_NOT_FOUND);

            verify(memberRepository).findById(999L);
            verify(memberRepository, never()).save(any(Member.class));
        }
    }
}
