package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.SignalDataType;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Registered signal (specv3.md Section 9.3). A NUMBER signal has exactly one source question; a
 * BOOLEAN signal is raised by one or more TEXT or missing-data sources. No multi-source numeric
 * derivation exists until a rule is configured. {@code reliability} in [0, 1] feeds pattern confidence
 * (decision D17); keyword-derived flags should be configured below 1. Sample values are unvalidated.
 */
public record SignalDefinition(
        String signalId,
        SignalDataType dataType,
        Set<String> sourceQuestionIds,
        String correlationGroup,
        String derivationVersion,
        BigDecimal reliability) {

    public SignalDefinition(String signalId, SignalDataType dataType, Set<String> sourceQuestionIds,
                            String correlationGroup, String derivationVersion) {
        this(signalId, dataType, sourceQuestionIds, correlationGroup, derivationVersion, BigDecimal.ONE);
    }

    public SignalDefinition {
        if (signalId == null || signalId.isBlank() || dataType == null
                || derivationVersion == null || derivationVersion.isBlank()) {
            throw new ScoringException(ScoringErrorCode.INVALID_SIGNAL_CONFIGURATION,
                    "signal id, data type and derivation version are required");
        }
        if (sourceQuestionIds == null || sourceQuestionIds.isEmpty()) {
            throw new ScoringException(ScoringErrorCode.INVALID_SIGNAL_CONFIGURATION,
                    "signal " + signalId + " needs at least one source question");
        }
        if (dataType == SignalDataType.NUMBER && sourceQuestionIds.size() != 1) {
            throw new ScoringException(ScoringErrorCode.INVALID_SIGNAL_CONFIGURATION,
                    "NUMBER signal " + signalId + " must have exactly one source question");
        }
        if (reliability == null || reliability.signum() < 0 || reliability.compareTo(BigDecimal.ONE) > 0) {
            throw new ScoringException(ScoringErrorCode.INVALID_SIGNAL_CONFIGURATION,
                    "signal " + signalId + " reliability must be between 0 and 1");
        }
        sourceQuestionIds = Collections.unmodifiableSortedSet(new TreeSet<>(sourceQuestionIds));
    }
}
