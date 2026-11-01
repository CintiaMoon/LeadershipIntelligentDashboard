package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.QuestionType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** TEXT configuration: versioned, deterministic flag rules; never a numeric score. */
public record TextConfig(String matcherVersion, List<TextSignalRule> signals) implements NormalizationConfig {

    public TextConfig {
        ConfigChecks.require(matcherVersion != null && !matcherVersion.isBlank(), "TEXT matcherVersion is required");
        ConfigChecks.require(signals != null && !signals.isEmpty(), "TEXT needs at least one signal rule");
        signals = List.copyOf(signals);
        Set<String> ids = new HashSet<>();
        for (TextSignalRule rule : signals) {
            ConfigChecks.require(ids.add(rule.signalId()), "duplicate text signal id " + rule.signalId());
        }
    }

    @Override
    public QuestionType questionType() {
        return QuestionType.TEXT;
    }
}
