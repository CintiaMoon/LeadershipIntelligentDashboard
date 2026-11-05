package com.company.leadershipintelligence.domain.identity;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TokenRolesTest {

    @Test
    void aTokenIsNeverOwnerAndNeverStrongerThanItsCreator() {
        assertThat(TokenRoles.issuable()).containsExactly("ADMIN", "ENGINEERING_LEADER", "ANALYST", "VIEWER");
        assertThat(TokenRoles.mayIssue(Set.of("OWNER"), "OWNER")).isFalse();
        assertThat(TokenRoles.mayIssue(Set.of("OWNER"), "ADMIN")).isTrue();
        assertThat(TokenRoles.mayIssue(Set.of("ADMIN"), "ADMIN")).isTrue();
        assertThat(TokenRoles.mayIssue(Set.of("ADMIN"), "VIEWER")).isTrue();
        assertThat(TokenRoles.mayIssue(Set.of("ANALYST"), "ADMIN")).isFalse();
        assertThat(TokenRoles.mayIssue(Set.of("VIEWER"), "ANALYST")).isFalse();
    }

    @Test
    void theStrongestRoleOfSeveralCounts() {
        assertThat(TokenRoles.mayIssue(Set.of("VIEWER", "ADMIN"), "ADMIN")).isTrue();
        assertThat(TokenRoles.mayIssue(Set.of("OWNER", "VIEWER"), "ENGINEERING_LEADER")).isTrue();
    }

    @Test
    void respondentsAndUnknownRolesCanIssueAndCarryNothing() {
        assertThat(TokenRoles.mayIssue(Set.of("RESPONDENT"), "VIEWER")).isFalse();
        assertThat(TokenRoles.mayIssue(Set.of(), "VIEWER")).isFalse();
        assertThat(TokenRoles.mayIssue(Set.of("OWNER"), "RESPONDENT")).isFalse();
        assertThat(TokenRoles.mayIssue(Set.of("OWNER"), "PLATFORM_ADMIN")).isFalse();
        assertThat(TokenRoles.mayIssue(Set.of("OWNER"), null)).isFalse();
        assertThat(TokenRoles.rank("RESPONDENT")).isEqualTo(-1);
    }
}
