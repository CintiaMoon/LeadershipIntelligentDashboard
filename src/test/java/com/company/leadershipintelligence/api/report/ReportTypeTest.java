package com.company.leadershipintelligence.api.report;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportTypeTest {

    @Test
    void acceptsTheNameInAnyCaseWithDashesOrUnderscores() {
        assertThat(ReportType.parse("LEADERSHIP_INTELLIGENCE")).contains(ReportType.LEADERSHIP_INTELLIGENCE);
        assertThat(ReportType.parse("leadership-intelligence")).contains(ReportType.LEADERSHIP_INTELLIGENCE);
        assertThat(ReportType.parse(" pattern-penalty ")).contains(ReportType.PATTERN_PENALTY);
        assertThat(ReportType.parse("Dimension_Analysis")).contains(ReportType.DIMENSION_ANALYSIS);
    }

    @Test
    void refusesAnythingElse() {
        assertThat(ReportType.parse("risk")).isEmpty();
        assertThat(ReportType.parse("")).isEmpty();
        assertThat(ReportType.parse(null)).isEmpty();
    }

    @Test
    void onlyTheHistoricalComparisonReadsTwoRuns() {
        for (ReportType type : ReportType.values()) {
            assertThat(type.comparison()).isEqualTo(type == ReportType.HISTORICAL_COMPARISON);
        }
    }
}
