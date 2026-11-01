package com.company.leadershipintelligence.domain.service.normalization;

import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.enums.UnknownOptionPolicy;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.rule.MultiSelectConfig;
import com.company.leadershipintelligence.domain.rule.NormalizationConfig;
import com.company.leadershipintelligence.domain.strategy.NormalizationResult;
import com.company.leadershipintelligence.domain.strategy.NormalizationStrategy;
import com.company.leadershipintelligence.domain.valueobject.Decimals;
import com.company.leadershipintelligence.domain.valueobject.NormalizedValue;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Several options may be chosen (specv3.md Section 4.2.1, decision D49). The answer is a list of option identifiers;
 * a repeated option counts once and the order does not matter. The normalized value is the arithmetic mean of the
 * values of the chosen options. An empty selection is missing data (handled before a strategy runs). With the
 * TREAT_AS_MISSING policy an option the configuration does not define is ignored, and a selection with no known
 * option is missing; with REJECT any unknown option rejects the answer.
 */
public class MultiSelectNormalizationStrategy implements NormalizationStrategy {

    @Override
    public QuestionType supportedType() {
        return QuestionType.MULTI_SELECT;
    }

    @Override
    public String strategyId() {
        return "multi-select";
    }

    @Override
    public String strategyVersion() {
        return "1";
    }

    @Override
    public NormalizationResult normalize(ScoringInput input, QuestionDefinition question, NormalizationConfig configuration) {
        MultiSelectConfig config = (MultiSelectConfig) configuration;
        if (!(input.answer() instanceof Collection<?> chosen)) {
            throw invalidType(question, "must be a list of options");
        }
        Set<String> options = new LinkedHashSet<>();
        for (Object element : chosen) {
            if (!(element instanceof String option)) {
                throw invalidType(question, "must contain only option identifiers");
            }
            options.add(option);
        }
        BigDecimal sum = BigDecimal.ZERO;
        int known = 0;
        for (String option : options) {
            NormalizedValue value = config.values().get(option);
            if (value == null) {
                if (config.unknownOptionPolicy() == UnknownOptionPolicy.TREAT_AS_MISSING) {
                    continue;
                }
                throw new ScoringException(ScoringErrorCode.INVALID_MULTIPLE_CHOICE_VALUE,
                        "an answer is not a configured option", Map.of("questionId", question.questionId()));
            }
            sum = sum.add(value.value());
            known++;
        }
        if (known == 0) {
            return NormalizationResult.missing();
        }
        return NormalizationResult.ofValue(new NormalizedValue(known == 1 ? sum : Decimals.divide(sum, BigDecimal.valueOf(known))));
    }

    private static ScoringException invalidType(QuestionDefinition question, String what) {
        return new ScoringException(ScoringErrorCode.INVALID_ANSWER_TYPE,
                "answer for question " + question.questionId() + " " + what, Map.of("questionId", question.questionId()));
    }
}
