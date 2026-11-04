package com.company.leadershipintelligence.service.identity;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher(new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(4))));

    @Test
    void aPasswordMatchesItsHashAndNothingElse() {
        String hash = hasher.hash("correct horse battery");
        assertThat(hash).startsWith("{bcrypt}").doesNotContain("correct");
        assertThat(hasher.matches("correct horse battery", hash)).isTrue();
        assertThat(hasher.matches("correct horse batterY", hash)).isFalse();
        assertThat(hasher.matches("", hash)).isFalse();
        assertThat(hasher.matches(null, hash)).isFalse();
        assertThat(hasher.matches("correct horse battery", null)).isFalse();
    }

    @Test
    void theSamePasswordGivesADifferentHashEachTime() {
        assertThat(hasher.hash("correct horse battery")).isNotEqualTo(hasher.hash("correct horse battery"));
    }

    @Test
    void everyCharacterOfALongPassphraseCounts() {
        // bcrypt alone would ignore everything after 72 bytes
        String base = "a".repeat(72);
        String hash = hasher.hash(base + "X");
        assertThat(hasher.matches(base + "X", hash)).isTrue();
        assertThat(hasher.matches(base + "Y", hash)).isFalse();
    }

    @Test
    void unicodeIsHandled() {
        String hash = hasher.hash("pässwörd-密码-🔐-long");
        assertThat(hasher.matches("pässwörd-密码-🔐-long", hash)).isTrue();
    }
}
