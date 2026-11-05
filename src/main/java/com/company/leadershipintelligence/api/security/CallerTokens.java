package com.company.leadershipintelligence.api.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Builds the caller of a request that was authenticated by the application itself (a session with a membership, an API token), in
 * the {@link Jwt} shape the controllers and {@link CurrentUser} already read. The object is internal: it is never signed, sent to
 * anyone or accepted from a client, and it lives for the one request.
 */
final class CallerTokens {

    private static final Duration LIFETIME = Duration.ofMinutes(5);

    private CallerTokens() {
    }

    static JwtAuthenticationToken caller(String subject, String email, String tenantId, List<String> roles, List<String> organizations,
                                         boolean allOrganizations, Clock clock) {
        Instant now = clock.instant();
        Jwt.Builder builder = Jwt.withTokenValue(CurrentUser.INTERNAL_TOKEN_VALUE).header("alg", "none").subject(subject)
                .claim(CurrentUser.ROLES_CLAIM, roles).claim(CurrentUser.ORGANIZATIONS_CLAIM, organizations)
                .claim(CurrentUser.ALL_ORGANIZATIONS_CLAIM, allOrganizations)
                .issuedAt(now).expiresAt(now.plus(LIFETIME));
        if (email != null) {
            builder.claim("email", email);
        }
        if (tenantId != null) {
            builder.claim(CurrentUser.TENANT_CLAIM, tenantId);
        }
        Jwt jwt = builder.build();
        List<GrantedAuthority> authorities = roles.stream().<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
        return new JwtAuthenticationToken(jwt, authorities, subject);
    }
}
