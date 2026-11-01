package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.TextMatchMode;

import java.util.List;

/** One deterministic text flag (specv3.md Section 8.9). Keywords match whole words or phrases. */
public record TextSignalRule(String signalId, TextMatchMode matchMode, List<String> keywords) {

    public TextSignalRule {
        ConfigChecks.require(signalId != null && !signalId.isBlank(), "text signal id is required");
        ConfigChecks.require(matchMode != null, "text matchMode is required");
        ConfigChecks.require(keywords != null && !keywords.isEmpty(), "text signal needs at least one keyword");
        keywords = List.copyOf(keywords);
        for (String keyword : keywords) {
            ConfigChecks.require(keyword != null && !keyword.isBlank(), "text keywords must not be blank");
        }
    }
}
