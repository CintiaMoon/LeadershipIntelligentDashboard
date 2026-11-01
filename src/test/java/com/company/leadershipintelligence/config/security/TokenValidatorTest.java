package com.company.leadershipintelligence.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A token must be valid in time and, when configured, issued by the expected issuer for this service. */
class TokenValidatorTest {

    private static Jwt token(String issuer, List<String> audience, Instant expires) {
        Jwt.Builder builder = Jwt.withTokenValue("t").header("alg", "none").subject("u").issuedAt(Instant.now().minusSeconds(7200)).expiresAt(expires);
        if (issuer != null) {
            builder.issuer(issuer);
        }
        if (audience != null) {
            builder.audience(audience);
        }
        return builder.build();
    }

    private static final Instant LATER = Instant.now().plusSeconds(600);

    @Test
    void withoutConfigurationOnlyTheValidityPeriodIsChecked() {
        OAuth2TokenValidator<Jwt> validator = SecurityConfiguration.validator("", "");
        assertThat(validator.validate(token("anyone", null, LATER)).hasErrors()).isFalse();
        assertThat(validator.validate(token("anyone", null, Instant.now().minusSeconds(3600))).hasErrors()).as("expired").isTrue();
    }

    @Test
    void aConfiguredIssuerMustMatchExactly() {
        OAuth2TokenValidator<Jwt> validator = SecurityConfiguration.validator("https://idp.example/realm", null);
        assertThat(validator.validate(token("https://idp.example/realm", null, LATER)).hasErrors()).isFalse();
        assertThat(validator.validate(token("https://evil.example/realm", null, LATER)).hasErrors()).isTrue();
        assertThat(validator.validate(token(null, null, LATER)).hasErrors()).as("no issuer").isTrue();
    }

    @Test
    void aConfiguredAudienceMustBeListed() {
        OAuth2TokenValidator<Jwt> validator = SecurityConfiguration.validator(null, "lis-api");
        assertThat(validator.validate(token("i", List.of("other", "lis-api"), LATER)).hasErrors()).isFalse();
        assertThat(validator.validate(token("i", List.of("other"), LATER)).hasErrors()).isTrue();
        assertThat(validator.validate(token("i", null, LATER)).hasErrors()).as("no audience").isTrue();
    }

    @Test
    void issuerAudienceAndTimeAreAllRequiredTogether() {
        OAuth2TokenValidator<Jwt> validator = SecurityConfiguration.validator("iss", "aud");
        assertThat(validator.validate(token("iss", List.of("aud"), LATER)).hasErrors()).isFalse();
        assertThat(validator.validate(token("iss", List.of("aud"), Instant.now().minusSeconds(3600))).hasErrors()).isTrue();
        assertThat(validator.validate(token("iss", List.of("x"), LATER)).hasErrors()).isTrue();
        assertThat(validator.validate(token("x", List.of("aud"), LATER)).hasErrors()).isTrue();
    }
}
