package com.back.domain.user.auth.useCase;

import com.back.domain.user.auth.dto.request.SignUpRequestDto;
import com.back.domain.user.auth.dto.response.SignUpResponseDto;

/**
 * 회원가입 유스케이스 인터페이스.
 *
 * <p>로컬 및 OAuth2 회원가입의 최종 완료 단계를 정의합니다.</p>
 */
public interface SignUpCase {

    /**
     * 회원가입을 완료합니다.
     *
     * @param dto 가입 요청 데이터 (닉네임, 약관 동의 등)
     * @return 가입 완료 메시지를 담은 응답
     */
    SignUpResponseDto doSignUp(SignUpRequestDto dto);

}
