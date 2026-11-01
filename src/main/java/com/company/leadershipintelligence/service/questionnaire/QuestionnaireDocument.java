package com.company.leadershipintelligence.service.questionnaire;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

/**
 * What the builder edits and the library stores (decisions D47, D50): a questionnaire as authored, before it is turned into a
 * scoring configuration layer. All thresholds in a rule condition are on the normalized 0 to 100 value of a question (100 is the
 * desirable end) or on a raw dimension score. Everything here is unvalidated authoring; the platform's own checks run at publish.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuestionnaireDocument(String name, String description, List<QuestionSpec> questions, List<RuleSpec> rules) {

    public QuestionnaireDocument {
        questions = questions == null ? List.of() : List.copyOf(questions);
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    /** One question. {@code dimension} null means collected but not mapped to a dimension. */
    @JsonIgnoreProperties(ignoreUnknown = false)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuestionSpec(
            String id,
            QuestionKind kind,
            String title,
            String text,
            String help,
            boolean required,
            String dimension,
            BigDecimal weight,
            Boolean higherIsBetter,
            List<OptionSpec> options,
            Integer points,
            List<String> labels,
            BigDecimal min,
            BigDecimal max,
            String desirableAnswer) {

        public QuestionSpec {
            options = options == null ? List.of() : List.copyOf(options);
            labels = labels == null ? List.of() : List.copyOf(labels);
        }
    }

    /** A choice. {@code score} (0 to 100, 100 desirable) is optional: unscored options are spaced evenly in the order shown. */
    @JsonIgnoreProperties(ignoreUnknown = false)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OptionSpec(String key, String label, BigDecimal score) {
    }

    /** An authored pattern with, optionally, the penalty it causes and the recommendation it raises (decision D50). */
    @JsonIgnoreProperties(ignoreUnknown = false)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RuleSpec(
            String id,
            String name,
            String description,
            String explanation,
            String severity,
            Boolean enabled,
            ConditionSpec condition,
            Integer minimumSignals,
            BigDecimal minimumConfidence,
            PenaltySpec penalty,
            RecommendationSpec recommendation) {
    }

    /**
     * A condition tree. {@code op} is AND, OR, NOT, EQ, NE, GT, GTE, LT, LTE, BETWEEN, IN, NOT_IN, EXISTS or NOT_EXISTS. A leaf names
     * exactly one of {@code question} (its normalized value) or {@code dimension} (its raw score).
     */
    @JsonIgnoreProperties(ignoreUnknown = false)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConditionSpec(
            String op,
            List<ConditionSpec> conditions,
            ConditionSpec condition,
            String question,
            String dimension,
            BigDecimal value,
            BigDecimal low,
            BigDecimal high,
            List<BigDecimal> values) {
    }

    /** {@code target} is a dimension name or OVERALL; {@code strategy} is FIXED or SEVERITY_BASED. */
    @JsonIgnoreProperties(ignoreUnknown = false)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PenaltySpec(
            String target,
            String strategy,
            BigDecimal amount,
            String stackingPolicy,
            BigDecimal cumulativeCap,
            String category,
            Integer priority) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecommendationSpec(String title, String description, Integer priority) {
    }
}
