package com.company.leadershipintelligence.service.identity;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TokenCodecTest {

    private final TokenCodec codec = new TokenCodec();

    @Test
    void aTokenIs256BitsOfUrlSafeText() {
        assertThat(codec.newToken()).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void tokensDoNotRepeat() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            assertThat(seen.add(codec.newToken())).isTrue();
        }
    }

    @Test
    void theStoredHashIsDeterministicHexAndHidesTheToken() {
        String hash = codec.hash("abc");
        assertThat(hash).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(codec.hash("abc")).isEqualTo(hash);
        assertThat(codec.hash("abd")).isNotEqualTo(hash);
    }
}
