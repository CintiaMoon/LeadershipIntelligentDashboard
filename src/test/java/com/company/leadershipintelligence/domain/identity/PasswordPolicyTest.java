package com.company.leadershipintelligence.domain.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {

    @Test
    void aLongEnoughPasswordIsAccepted() {
        assertThat(PasswordPolicy.violation("correct battery staple", "a@b.com")).isNull();
        assertThat(PasswordPolicy.violation("x7!Kq2#mWp9z", null)).isNull();
    }

    @Test
    void lengthBoundaries() {
        assertThat(PasswordPolicy.violation("abcdefghij1", null)).contains("at least 12");
        assertThat(PasswordPolicy.violation("abcdefghij12", null)).isNull();
        String varied = "abcdefghijklmnop".repeat(8);
        assertThat(PasswordPolicy.violation(varied, null)).isNull();
        assertThat(PasswordPolicy.violation(varied + "q", null)).contains("at most 128");
        assertThat(PasswordPolicy.violation(null, null)).contains("at least 12");
    }

    @Test
    void noCompositionRulesAreImposed() {
        assertThat(PasswordPolicy.violation("alllowercasewords", null)).isNull();
    }

    @Test
    void commonPasswordsAreRefusedWhateverTheCase() {
        assertThat(PasswordPolicy.violation("Password12345", null)).contains("too common");
        assertThat(PasswordPolicy.violation("QWERTYUIOP12", null)).contains("too common");
    }

    @Test
    void theEmailAddressIsNotAPassword() {
        assertThat(PasswordPolicy.violation("someone@example.com", "someone@example.com")).contains("email");
        assertThat(PasswordPolicy.violation("alexandria.long", "alexandria.long@example.com")).contains("email");
        // a short local part is not treated as a password of its own
        assertThat(PasswordPolicy.violation("correct battery staple", "correct@example.com")).isNull();
    }

    @Test
    void aRepetitivePasswordIsRefused() {
        assertThat(PasswordPolicy.violation("aaabbbaaabbb", null)).contains("repetitive");
    }
}
