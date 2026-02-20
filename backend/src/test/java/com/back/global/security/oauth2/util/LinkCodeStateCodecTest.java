package com.back.global.security.oauth2.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LinkCodeStateCodec 테스트")
class LinkCodeStateCodecTest {

    private LinkCodeStateCodec codec;

    @BeforeEach
    void setUp() {
        codec = new LinkCodeStateCodec();
    }

    @Test
    @DisplayName("encode → decode 라운드트립 정합성")
    void roundtrip_encodeAndDecode_returnsOriginalValues() {
        String linkCode = UUID.randomUUID().toString(); // 36자
        String originalState = "some-oauth2-state-value";

        String encoded = codec.encode(linkCode, originalState);
        String[] decoded = codec.decode(encoded);

        assertThat(decoded).isNotNull();
        assertThat(decoded[0]).isEqualTo(linkCode);
        assertThat(decoded[1]).isEqualTo(originalState);
    }

    @Test
    @DisplayName("파이프 문자 포함 state도 정상 파싱")
    void roundtrip_stateWithPipeCharacters_parsesCorrectly() {
        String linkCode = UUID.randomUUID().toString();
        String originalState = "state|with|pipes";

        String encoded = codec.encode(linkCode, originalState);
        String[] decoded = codec.decode(encoded);

        assertThat(decoded).isNotNull();
        assertThat(decoded[0]).isEqualTo(linkCode);
        assertThat(decoded[1]).isEqualTo(originalState);
    }

    @Test
    @DisplayName("빈 originalState도 정상 파싱")
    void roundtrip_emptyOriginalState_parsesCorrectly() {
        String linkCode = UUID.randomUUID().toString();
        String originalState = "";

        String encoded = codec.encode(linkCode, originalState);
        String[] decoded = codec.decode(encoded);

        assertThat(decoded).isNotNull();
        assertThat(decoded[0]).isEqualTo(linkCode);
        assertThat(decoded[1]).isEqualTo(originalState);
    }

    @Test
    @DisplayName("null 입력 시 null 반환")
    void decode_null_returnsNull() {
        assertThat(codec.decode(null)).isNull();
    }

    @Test
    @DisplayName("잘못된 Base64 입력 시 null 반환")
    void decode_invalidBase64_returnsNull() {
        assertThat(codec.decode("!!!not-base64!!!")).isNull();
    }

    @Test
    @DisplayName("LINK: prefix 없는 state 시 null 반환")
    void decode_noPrefixState_returnsNull() {
        String plainState = "plain-oauth2-state-value";
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(plainState.getBytes(StandardCharsets.UTF_8));
        assertThat(codec.decode(encoded)).isNull();
    }

    @Test
    @DisplayName("UUID_LENGTH(36자) 미만 linkCode 시 null 반환")
    void decode_shortLinkCode_returnsNull() {
        String shortContent = "LINK:short";
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(shortContent.getBytes(StandardCharsets.UTF_8));
        assertThat(codec.decode(encoded)).isNull();
    }

    @Test
    @DisplayName("isLinkState: 계정 연동 state면 true 반환")
    void isLinkState_validLinkState_returnsTrue() {
        String linkCode = UUID.randomUUID().toString();
        String encoded = codec.encode(linkCode, "some-state");
        assertThat(codec.isLinkState(encoded)).isTrue();
    }

    @Test
    @DisplayName("isLinkState: 일반 state면 false 반환")
    void isLinkState_plainState_returnsFalse() {
        String plainEncoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("plain-state".getBytes(StandardCharsets.UTF_8));
        assertThat(codec.isLinkState(plainEncoded)).isFalse();
    }
}
