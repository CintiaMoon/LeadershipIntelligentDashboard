package com.company.leadershipintelligence.domain.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailAddressTest {

    @Test
    void anAddressIsTrimmedAndLowerCased() {
        assertThat(EmailAddress.normalize("  Ana.Silva@Example.COM ")).isEqualTo("ana.silva@example.com");
    }

    @Test
    void textThatIsNotAnAddressIsRefused() {
        for (String bad : new String[]{null, "", "   ", "no-at-sign", "a@b", "@example.com", "a@@example.com", "a b@example.com", "a@exa mple.com",
                "a@example..com", "a@example.", "x".repeat(250) + "@e.com"}) {
            assertThat(EmailAddress.normalize(bad)).as(String.valueOf(bad)).isNull();
        }
    }

    @Test
    void theLongestAcceptedAddressHas254Characters() {
        String local = "l".repeat(64);
        String domain = "d".repeat(254 - 64 - 1 - 4) + ".com";
        assertThat(EmailAddress.normalize(local + "@" + domain)).hasSize(254);
        assertThat(EmailAddress.normalize(local + "@d" + domain)).isNull();
    }
}
