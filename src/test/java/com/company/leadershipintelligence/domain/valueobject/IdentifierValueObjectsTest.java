package com.company.leadershipintelligence.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentifierValueObjectsTest {

    @Test
    void identifiersRejectNullAndBlank() {
        assertThatThrownBy(() -> new TenantId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new OrganizationId(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AssessmentId("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AssessmentSnapshotId("\t")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScoringRunId(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfigurationVersion("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void identifiersCompareByValue() {
        assertThat(new TenantId("t-1")).isEqualTo(new TenantId("t-1")).isNotEqualTo(new TenantId("t-2"));
        assertThat(new TenantId("t-1")).hasToString("t-1");
    }
}
