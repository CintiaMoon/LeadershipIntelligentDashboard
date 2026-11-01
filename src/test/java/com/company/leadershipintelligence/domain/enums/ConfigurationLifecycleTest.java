package com.company.leadershipintelligence.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationLifecycleTest {

    @Test
    void onlyTheDocumentedTransitionsAreAllowed() {
        assertThat(ConfigurationStatus.DRAFT.canTransitionTo(ConfigurationStatus.VALIDATING)).isTrue();
        assertThat(ConfigurationStatus.VALIDATING.canTransitionTo(ConfigurationStatus.ACTIVE)).isTrue();
        assertThat(ConfigurationStatus.VALIDATING.canTransitionTo(ConfigurationStatus.DRAFT)).isTrue();
        assertThat(ConfigurationStatus.ACTIVE.canTransitionTo(ConfigurationStatus.PAUSED)).isTrue();
        assertThat(ConfigurationStatus.ACTIVE.canTransitionTo(ConfigurationStatus.ARCHIVED)).isTrue();
        assertThat(ConfigurationStatus.PAUSED.canTransitionTo(ConfigurationStatus.ACTIVE)).isTrue();
        assertThat(ConfigurationStatus.PAUSED.canTransitionTo(ConfigurationStatus.ARCHIVED)).isTrue();
    }

    @Test
    void everyOtherTransitionIsRejected() {
        int allowed = 0;
        for (ConfigurationStatus from : ConfigurationStatus.values()) {
            for (ConfigurationStatus to : ConfigurationStatus.values()) {
                if (from.canTransitionTo(to)) {
                    allowed++;
                }
            }
        }
        assertThat(allowed).isEqualTo(7);
        assertThat(ConfigurationStatus.DRAFT.canTransitionTo(ConfigurationStatus.ACTIVE)).isFalse();
        assertThat(ConfigurationStatus.ACTIVE.canTransitionTo(ConfigurationStatus.DRAFT)).isFalse();
        assertThat(ConfigurationStatus.ACTIVE.canTransitionTo(ConfigurationStatus.ACTIVE)).isFalse();
        for (ConfigurationStatus to : ConfigurationStatus.values()) {
            assertThat(ConfigurationStatus.ARCHIVED.canTransitionTo(to)).isFalse();
        }
    }

    @Test
    void onlyDraftIsEditableAndOnlyActiveIsUsedForScoring() {
        for (ConfigurationStatus status : ConfigurationStatus.values()) {
            assertThat(status.editable()).isEqualTo(status == ConfigurationStatus.DRAFT);
            assertThat(status.usableForScoring()).isEqualTo(status == ConfigurationStatus.ACTIVE);
        }
    }

    @Test
    void scopePrecedenceRunsFromPlatformToTenant() {
        assertThat(ConfigurationScope.values()).extracting(ConfigurationScope::name)
                .containsExactly("PLATFORM_DEFAULT", "TEMPLATE", "ORGANIZATION", "TENANT");
        assertThat(ConfigurationScope.TENANT.precedence()).isGreaterThan(ConfigurationScope.ORGANIZATION.precedence());
        assertThat(ConfigurationScope.ORGANIZATION.precedence()).isGreaterThan(ConfigurationScope.TEMPLATE.precedence());
        assertThat(ConfigurationScope.TEMPLATE.precedence()).isGreaterThan(ConfigurationScope.PLATFORM_DEFAULT.precedence());
        assertThat(ConfigurationScope.PLATFORM_DEFAULT.tenantOwned()).isFalse();
        assertThat(ConfigurationScope.TENANT.tenantOwned()).isTrue();
    }
}
