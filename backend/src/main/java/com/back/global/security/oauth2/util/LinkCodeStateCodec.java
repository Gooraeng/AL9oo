package com.back.global.security.oauth2.util;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * OAuth2 state 파라미터에 linkCode를 인코딩/디코딩하는 유틸리티.
 *
 * <p>계정 연동 시 linkCode(UUID, 36자 고정)를 OAuth2 state에 포함시켜 전달합니다.
 * 형식: base64(LINK:{linkCode(36자)}{originalState})</p>
 */
@Component
public class LinkCodeStateCodec {

    private static final String LINK_PREFIX = "LINK:";
    private static final int UUID_LENGTH = 36;

    /**
     * linkCode와 originalState를 결합하여 인코딩합니다.
     *
     * @param linkCode      계정 연동 코드 (UUID 36자)
     * @param originalState 원본 OAuth2 state
     * @return 인코딩된 state 문자열
     */
    public String encode(String linkCode, String originalState) {
        String combined = LINK_PREFIX + linkCode + originalState;
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(combined.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 인코딩된 state에서 linkCode와 originalState를 추출합니다.
     *
     * @param encodedState 인코딩된 state 문자열
     * @return [linkCode, originalState] 배열, 계정 연동용이 아닌 경우 null
     */
    public String[] decode(String encodedState) {
        if (encodedState == null) {
            return null;
        }

        try {
            String decoded = new String(
                    Base64.getUrlDecoder().decode(encodedState),
                    StandardCharsets.UTF_8
            );

            if (!decoded.startsWith(LINK_PREFIX)) {
                return null;
            }

            String withoutPrefix = decoded.substring(LINK_PREFIX.length());

            if (withoutPrefix.length() < UUID_LENGTH) {
                return null;
            }

            String linkCode = withoutPrefix.substring(0, UUID_LENGTH);
            String originalState = withoutPrefix.substring(UUID_LENGTH);

            return new String[]{linkCode, originalState};
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 인코딩된 state가 계정 연동용인지 확인합니다.
     *
     * @param encodedState 인코딩된 state 문자열
     * @return 계정 연동용이면 true
     */
    public boolean isLinkState(String encodedState) {
        return decode(encodedState) != null;
    }
}
