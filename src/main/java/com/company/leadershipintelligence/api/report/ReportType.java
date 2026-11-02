package com.company.leadershipintelligence.api.report;

import java.util.Locale;
import java.util.Optional;

/** The report types of decision D59. The path accepts the name in any case, with dashes or underscores. */
public enum ReportType {
    LEADERSHIP_INTELLIGENCE("Leadership Intelligence Report", false),
    DIMENSION_ANALYSIS("Dimension Analysis Report", false),
    PATTERN_PENALTY("Pattern and Penalty Report", false),
    HISTORICAL_COMPARISON("Historical Comparison Report", true);

    private final String title;
    private final boolean comparison;

    ReportType(String title, boolean comparison) {
        this.title = title;
        this.comparison = comparison;
    }

    public String title() {
        return title;
    }

    /** True for the report that reads two runs. */
    public boolean comparison() {
        return comparison;
    }

    public static Optional<ReportType> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normal = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (ReportType type : values()) {
            if (type.name().equals(normal)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
