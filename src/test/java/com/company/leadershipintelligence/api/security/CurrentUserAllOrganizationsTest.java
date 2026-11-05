package com.company.leadershipintelligence.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserAllOrganizationsTest {

    private static Jwt jwt(String tokenValue, boolean allClaim) {
        return Jwt.withTokenValue(tokenValue).header("alg", "none").subject("someone").claim("tenant_id", "acme").claim("roles", List.of("VIEWER"))
                .claim("org_ids", List.of("org-1")).claim("org_all", allClaim).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    @Test
    void anOrganizationListMeansExactlyThoseOrganizations() {
        CurrentUser user = CurrentUser.from(jwt("eyJhbGciOiJSUzI1NiJ9.signed.token", false));
        assertThat(user.allOrganizations()).isFalse();
        assertThat(user.scope().everything()).isFalse();
        assertThat(user.scope().organizationIds()).containsExactly("org-1");
    }

    @Test
    void theAllOrganizationsClaimIsHonouredOnlyOnTheApplicationsOwnCallerObject() {
        CurrentUser own = CurrentUser.from(jwt(CurrentUser.INTERNAL_TOKEN_VALUE, true));
        assertThat(own.allOrganizations()).isTrue();
        assertThat(own.scope().everything()).isTrue();
        // a verified external token that happens to carry the claim gains nothing
        CurrentUser external = CurrentUser.from(jwt("eyJhbGciOiJSUzI1NiJ9.signed.token", true));
        assertThat(external.allOrganizations()).isFalse();
        assertThat(external.scope().everything()).isFalse();
        assertThat(external.scope().organizationIds()).containsExactly("org-1");
    }
}
