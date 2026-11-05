package com.company.leadershipintelligence.domain.identity;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MemberRulesTest {

    @Test
    void anOwnerGrantsAndManagesEveryRole() {
        for (String role : MemberRules.ROLES) {
            assertThat(MemberRules.mayGrant(Set.of("OWNER"), Set.of(role))).as(role).isTrue();
            assertThat(MemberRules.mayManage(Set.of("OWNER"), Set.of(role))).as(role).isTrue();
        }
    }

    @Test
    void anAdministratorManagesTheLowerRolesAndRespondentsButNeitherOwnersNorAdministrators() {
        for (String role : Set.of("ENGINEERING_LEADER", "ANALYST", "VIEWER", "RESPONDENT")) {
            assertThat(MemberRules.mayGrant(Set.of("ADMIN"), Set.of(role))).as(role).isTrue();
            assertThat(MemberRules.mayManage(Set.of("ADMIN"), Set.of(role))).as(role).isTrue();
        }
        for (String role : Set.of("OWNER", "ADMIN")) {
            assertThat(MemberRules.mayGrant(Set.of("ADMIN"), Set.of(role))).as(role).isFalse();
            assertThat(MemberRules.mayManage(Set.of("ADMIN"), Set.of(role))).as(role).isFalse();
        }
        // one forbidden role in the set is enough
        assertThat(MemberRules.mayGrant(Set.of("ADMIN"), Set.of("VIEWER", "ADMIN"))).isFalse();
        assertThat(MemberRules.mayManage(Set.of("ADMIN"), Set.of("VIEWER", "OWNER"))).isFalse();
    }

    @Test
    void everyoneElseManagesNobody() {
        for (String role : Set.of("ENGINEERING_LEADER", "ANALYST", "VIEWER", "RESPONDENT")) {
            assertThat(MemberRules.mayGrant(Set.of(role), Set.of("VIEWER"))).as(role).isFalse();
            assertThat(MemberRules.mayManage(Set.of(role), Set.of("VIEWER"))).as(role).isFalse();
        }
        assertThat(MemberRules.mayGrant(Set.of(), Set.of("VIEWER"))).isFalse();
    }

    @Test
    void theStrongestRoleOfTheActorCounts() {
        assertThat(MemberRules.mayGrant(Set.of("VIEWER", "OWNER"), Set.of("ADMIN"))).isTrue();
        assertThat(MemberRules.mayGrant(Set.of("VIEWER", "ADMIN"), Set.of("ANALYST"))).isTrue();
    }

    @Test
    void aRoleSetIsKnownNonEmptyAndKeepsRespondentApart() {
        assertThat(MemberRules.validRoles(Set.of("OWNER", "ADMIN"))).isTrue();
        assertThat(MemberRules.validRoles(Set.of("RESPONDENT"))).isTrue();
        assertThat(MemberRules.validRoles(Set.of("RESPONDENT", "VIEWER"))).isFalse();
        assertThat(MemberRules.validRoles(Set.of())).isFalse();
        assertThat(MemberRules.validRoles(null)).isFalse();
        assertThat(MemberRules.validRoles(Set.of("PLATFORM_ADMIN"))).isFalse();
        assertThat(MemberRules.validRoles(Set.of("admin"))).isFalse();
    }
}
