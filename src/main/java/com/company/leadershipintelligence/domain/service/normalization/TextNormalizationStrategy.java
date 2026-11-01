package com.company.leadershipintelligence.domain.service.normalization;

import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.rule.NormalizationConfig;
import com.company.leadershipintelligence.domain.rule.TextConfig;
import com.company.leadershipintelligence.domain.rule.TextSignalRule;
import com.company.leadershipintelligence.domain.strategy.NormalizationResult;
import com.company.leadershipintelligence.domain.strategy.NormalizationStrategy;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Deterministic keyword flags (specv3.md Section 8.9). Matching is case-insensitive on whole words
 * or phrases. It yields signals only, never a numeric score, and makes no claim of semantic certainty.
 */
public class TextNormalizationStrategy implements NormalizationStrategy {

    @Override
    public QuestionType supportedType() {
        return QuestionType.TEXT;
    }

    @Override
    public String strategyId() {
        return "text";
    }

    @Override
    public String strategyVersion() {
        return "1";
    }

    @Override
    public NormalizationResult normalize(ScoringInput input, QuestionDefinition question, NormalizationConfig configuration) {
        TextConfig config = (TextConfig) configuration;
        if (!(input.answer() instanceof String text)) {
            throw new ScoringException(ScoringErrorCode.INVALID_ANSWER_TYPE,
                    "answer for question " + question.questionId() + " must be text",
                    Map.of("questionId", question.questionId()));
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        Set<String> detected = new TreeSet<>();
        for (TextSignalRule rule : config.signals()) {
            for (String keyword : rule.keywords()) {
                if (containsWord(normalized, keyword.toLowerCase(Locale.ROOT))) {
                    detected.add(rule.signalId());
                    break;
                }
            }
        }
        return NormalizationResult.ofSignals(detected);
    }

    private static boolean containsWord(String text, String keyword) {
        Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(keyword.strip()) + "(?![\\p{L}\\p{N}])");
        return pattern.matcher(text).find();
    }
}
