package com.company.leadershipintelligence.api.security;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.service.command.OrganizationScope;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    private static AuthenticatedCaller caller(String subject, String tenant, Set<String> roles, Set<String> organizations, boolean all) {
        return new AuthenticatedCaller(subject, null, tenant, roles, organizations, all);
    }

    @Test
    void readsTenantRolesAndOrganizationsFromTheCaller() {
        CurrentUser user = CurrentUser.from(caller("u-1", "t-1", Set.of("ADMIN", "ANALYST"), Set.of("org-1", "org-2"), false));
        assertThat(user.tenantId().value()).isEqualTo("t-1");
        assertThat(user.actor()).isEqualTo("u-1");
        assertThat(user.roles()).containsExactlyInAnyOrder(Role.ADMIN, Role.ANALYST);
        assertThat(user.hasAnyRole(Role.ADMIN)).isTrue();
        assertThat(user.hasAnyRole(Role.OWNER, Role.VIEWER)).isFalse();
        assertThat(user.organizationIds()).containsExactlyInAnyOrder("org-1", "org-2");
    }

    @Test
    void aCallerWithoutACompanyIsRejected() {
        assertThatThrownBy(() -> CurrentUser.from(caller("u", null, Set.of("ADMIN"), Set.of(), false)))
                .isInstanceOfSatisfying(ScoringException.class, e -> assertThat(e.code()).isEqualTo(ScoringErrorCode.TENANT_CONTEXT_MISSING));
        assertThatThrownBy(() -> CurrentUser.from(caller("u", " ", Set.of(), Set.of(), false))).isInstanceOf(ScoringException.class);
    }

    @Test
    void unknownRolesAreIgnoredAndMissingRolesMeanNoRolesAndNoOrganizations() {
        CurrentUser user = CurrentUser.from(caller("u", "t", Set.of("SUPERUSER", "admin", "VIEWER", "RESPONDENT", "PLATFORM_ADMIN"), Set.of(), false));
        assertThat(user.roles()).containsExactly(Role.VIEWER);
        assertThat(user.organizationIds()).isEmpty();
        assertThat(CurrentUser.from(caller(null, "t", Set.of(), Set.of(), false)).roles()).isEmpty();
        assertThat(CurrentUser.from(caller(null, "t", Set.of(), Set.of(), false)).actor()).isEqualTo("unknown");
    }

    @Test
    void theOrganizationScopeAllowsOnlyTheCallersOrganizations() {
        OrganizationScope scope = CurrentUser.from(caller("u", "t", Set.of(), Set.of("org-1"), false)).scope();
        scope.require("org-1");
        assertThatThrownBy(() -> scope.require("org-2")).isInstanceOfSatisfying(ScoringException.class,
                e -> assertThat(e.code()).isEqualTo(ScoringErrorCode.ORGANIZATION_ACCESS_DENIED));
        assertThatThrownBy(() -> CurrentUser.from(caller("u", "t", Set.of(), Set.of(), false)).scope().require("org-1")).isInstanceOf(ScoringException.class);
        OrganizationScope.unrestricted().require("anything");
    }

    @Test
    void aCallerNotLimitedToAListMayNameANewOrganizationAndStillSeesTheOnesThatExist() {
        CurrentUser user = CurrentUser.from(caller("u", "t", Set.of("OWNER"), Set.of("org-1"), true));
        assertThat(user.allOrganizations()).isTrue();
        assertThat(user.scope().everything()).isTrue();
        user.scope().require("an-organization-that-does-not-exist-yet");
        assertThat(user.scope().organizationIds()).containsExactly("org-1");
        assertThat(CurrentUser.from(caller("u", "t", Set.of("OWNER"), Set.of("org-1"), false)).scope().everything()).isFalse();
    }

    @Test
    void rolesBecomeSpringAuthorities() {
        var authentication = new CallerAuthentication(caller("u", "t", Set.of("OWNER", "VIEWER"), Set.of(), false));
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_OWNER", "ROLE_VIEWER");
        assertThat(new CallerAuthentication(caller("u", "t", Set.of(), Set.of(), false)).getAuthorities()).isEmpty();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getName()).isEqualTo("u");
        assertThat(authentication.getPrincipal().tenantId()).isEqualTo("t");
    }

    @Test
    void thePlatformAdministratorIsNotATenantRole() {
        AuthenticatedCaller platform = caller("root", null, Set.of(PlatformRole.PLATFORM_ADMIN), Set.of(), false);
        PlatformRole.require(platform);
        assertThat(PlatformRole.actor(platform)).isEqualTo("root");
        assertThatThrownBy(() -> PlatformRole.require(caller("u", "t", Set.of("OWNER"), Set.of(), false))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(Role.values()).extracting(Enum::name).doesNotContain(PlatformRole.PLATFORM_ADMIN, RespondentRole.RESPONDENT);
    }

    @Test
    void aRespondentIsKnownByTheVerifiedEmail() {
        AuthenticatedCaller respondent = new AuthenticatedCaller("u", " Pat@Example.com ", "t", Set.of("RESPONDENT"), Set.of(), false);
        RespondentRole.require(respondent);
        assertThat(RespondentRole.email(respondent)).isEqualTo("pat@example.com");
        assertThatThrownBy(() -> RespondentRole.require(caller("u", "t", Set.of("OWNER"), Set.of(), false))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> RespondentRole.email(caller("u", "t", Set.of("RESPONDENT"), Set.of(), false))).isInstanceOf(ScoringException.class);
    }
}
