package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.EvidenceKind;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** One supporting observation behind a pattern finding (specv3.md Section 14.1). */
public record PatternEvidence(
        String operandKey,
        EvidenceKind kind,
        BigDecimal numericValue,
        Boolean flag,
        Set<String> sourceQuestionIds,
        String correlationGroup,
        BigDecimal reliability) {

    public PatternEvidence {
        sourceQuestionIds = Collections.unmodifiableSortedSet(new TreeSet<>(sourceQuestionIds));
    }
}
