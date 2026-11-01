package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.AggregationMethod;
import com.company.leadershipintelligence.domain.enums.MissingDataPolicy;
import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.rule.NormalizationConfig;
import com.company.leadershipintelligence.domain.valueobject.NormalizedValue;

/**
 * Question configuration resolved from the immutable template/configuration (specv3.md Sections 4.3,
 * 8.3). The missing-data policy is mandatory (Section 8.10): NEUTRAL requires an explicit neutral
 * value (never assumed to be 50) and RISK_SIGNAL requires the signal to raise.
 */
public record QuestionDefinition(
        String questionId,
        QuestionType type,
        NormalizationConfig normalization,
        MissingDataPolicy missingPolicy,
        NormalizedValue neutralValue,
        String riskSignalId,
        AggregationMethod aggregation) {

    /** A question without its own aggregation method uses the configuration's default (decision D45). */
    public QuestionDefinition(String questionId, QuestionType type, NormalizationConfig normalization,
                              MissingDataPolicy missingPolicy, NormalizedValue neutralValue, String riskSignalId) {
        this(questionId, type, normalization, missingPolicy, neutralValue, riskSignalId, null);
    }

    public QuestionDefinition {
        if (questionId == null || questionId.isBlank() || type == null || normalization == null || missingPolicy == null) {
            throw invalid("questionId, type, normalization and missingPolicy are required");
        }
        if (normalization.questionType() != type) {
            throw invalid("question " + questionId + " is " + type + " but its normalization config is for "
                    + normalization.questionType());
        }
        if (missingPolicy == MissingDataPolicy.NEUTRAL && neutralValue == null) {
            throw invalid("question " + questionId + " uses NEUTRAL and must define a neutral value");
        }
        if (missingPolicy != MissingDataPolicy.NEUTRAL && neutralValue != null) {
            throw invalid("question " + questionId + " defines a neutral value but its policy is " + missingPolicy);
        }
        if (missingPolicy == MissingDataPolicy.RISK_SIGNAL && (riskSignalId == null || riskSignalId.isBlank())) {
            throw invalid("question " + questionId + " uses RISK_SIGNAL and must name the signal");
        }
        if (missingPolicy != MissingDataPolicy.RISK_SIGNAL && riskSignalId != null) {
            throw invalid("question " + questionId + " names a risk signal but its policy is " + missingPolicy);
        }
    }

    private static ScoringException invalid(String message) {
        return new ScoringException(ScoringErrorCode.INVALID_NORMALIZATION_CONFIGURATION, message);
    }
}
