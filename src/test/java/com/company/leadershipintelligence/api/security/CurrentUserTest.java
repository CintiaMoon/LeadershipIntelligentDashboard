package com.company.leadershipintelligence.api.security;

import com.company.leadershipintelligence.config.security.SecurityConfiguration;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.service.command.OrganizationScope;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "none"), claims);
    }

    @Test
    void readsTenantRolesAndOrganizationsFromTheToken() {
        CurrentUser user = CurrentUser.from(jwt(Map.of("sub", "u-1", "tenant_id", "t-1", "roles", List.of("ADMIN", "ANALYST"),
                "org_ids", List.of("org-1", "org-2"))));
        assertThat(user.tenantId().value()).isEqualTo("t-1");
        assertThat(user.actor()).isEqualTo("u-1");
        assertThat(user.roles()).containsExactlyInAnyOrder(Role.ADMIN, Role.ANALYST);
        assertThat(user.hasAnyRole(Role.ADMIN)).isTrue();
        assertThat(user.hasAnyRole(Role.OWNER, Role.VIEWER)).isFalse();
        assertThat(user.organizationIds()).containsExactlyInAnyOrder("org-1", "org-2");
    }

    @Test
    void aTokenWithoutATenantIsRejected() {
        assertThatThrownBy(() -> CurrentUser.from(jwt(Map.of("sub", "u", "roles", List.of("ADMIN")))))
                .isInstanceOfSatisfying(ScoringException.class, e -> assertThat(e.code()).isEqualTo(ScoringErrorCode.TENANT_CONTEXT_MISSING));
        assertThatThrownBy(() -> CurrentUser.from(jwt(Map.of("sub", "u", "tenant_id", " "))))
                .isInstanceOf(ScoringException.class);
    }

    @Test
    void unknownRolesAreIgnoredAndMissingClaimsMeanNoRolesAndNoOrganizations() {
        CurrentUser user = CurrentUser.from(jwt(Map.of("sub", "u", "tenant_id", "t", "roles", List.of("SUPERUSER", "admin", "VIEWER"))));
        assertThat(user.roles()).containsExactly(Role.VIEWER);
        assertThat(user.organizationIds()).isEmpty();
        assertThat(CurrentUser.from(jwt(Map.of("tenant_id", "t"))).roles()).isEmpty();
        assertThat(CurrentUser.from(jwt(Map.of("tenant_id", "t"))).actor()).isEqualTo("unknown");
    }

    @Test
    void theOrganizationScopeAllowsOnlyTheTokensOrganizations() {
        OrganizationScope scope = CurrentUser.from(jwt(Map.of("tenant_id", "t", "org_ids", List.of("org-1")))).scope();
        scope.require("org-1");
        assertThatThrownBy(() -> scope.require("org-2")).isInstanceOfSatisfying(ScoringException.class,
                e -> assertThat(e.code()).isEqualTo(ScoringErrorCode.ORGANIZATION_ACCESS_DENIED));
        assertThatThrownBy(() -> CurrentUser.from(jwt(Map.of("tenant_id", "t"))).scope().require("org-1")).isInstanceOf(ScoringException.class);
        OrganizationScope.unrestricted().require("anything");
    }

    @Test
    void rolesBecomeSpringAuthorities() {
        var converter = new SecurityConfiguration().rolesConverter();
        var authentication = converter.convert(jwt(Map.of("sub", "u", "tenant_id", "t", "roles", List.of("OWNER", "VIEWER"))));
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactlyInAnyOrder("ROLE_OWNER", "ROLE_VIEWER");
        assertThat(converter.convert(jwt(Map.of("sub", "u"))).getAuthorities()).isEmpty();
    }
}
