package com.company.leadershipintelligence.service.questionnaire;

import com.company.leadershipintelligence.domain.enums.ScaleDirection;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.enums.SignalDataType;
import com.company.leadershipintelligence.domain.rule.BooleanConfig;
import com.company.leadershipintelligence.domain.rule.Condition;
import com.company.leadershipintelligence.domain.rule.ContributionRule;
import com.company.leadershipintelligence.domain.rule.MultiSelectConfig;
import com.company.leadershipintelligence.domain.rule.MultipleChoiceConfig;
import com.company.leadershipintelligence.domain.rule.NormalizationConfig;
import com.company.leadershipintelligence.domain.rule.NumberConfig;
import com.company.leadershipintelligence.domain.rule.Operand;
import com.company.leadershipintelligence.domain.rule.PatternDefinition;
import com.company.leadershipintelligence.domain.rule.PenaltyDefinition;
import com.company.leadershipintelligence.domain.rule.PresentationCatalog;
import com.company.leadershipintelligence.domain.rule.RecommendationDefinition;
import com.company.leadershipintelligence.domain.rule.ScaleConfig;
import com.company.leadershipintelligence.domain.rule.SignalDefinition;
import com.company.leadershipintelligence.domain.rule.StrategyConfig;
import com.company.leadershipintelligence.domain.rule.TextConfig;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.ConditionSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.OptionSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.PenaltySpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.QuestionSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.RecommendationSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.RuleSpec;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Copies the configuration that applies to a tenant into a builder document ("start from the initial questionnaire", decision D50).
 * The builder is narrower than the engine, so a copy is faithful for questions, scores, dimension weights and the rules whose
 * conditions it can express (numeric comparisons of one question's normalized value or of a raw dimension score, combined with
 * AND, OR and NOT), each with its first supported penalty and first recommendation. Everything else is left out and listed in the
 * notes: text-derived flags, gap and contains conditions, other penalty strategies, imbalance rules, missing-data policies. The
 * result is a draft to review, never published here.
 */
final class PlatformDefaultImporter {

    record Imported(QuestionnaireDocument document, List<String> notes) {
    }

    private static final Set<String> BUILDER_STACKING = QuestionnaireValidator.STACKING;

    private PlatformDefaultImporter() {
    }

    static Imported from(ScoringConfigurationSnapshot snapshot, String name) {
        List<String> notes = new ArrayList<>();
        PresentationCatalog presentation = snapshot.presentation();
        Map<String, ContributionRule> mapping = new HashMap<>();
        for (ContributionRule rule : snapshot.dimensions().rules()) {
            if (mapping.putIfAbsent(rule.questionId(), rule) != null) {
                notes.add("question " + rule.questionId() + " feeds more than one dimension; only the first is kept");
            }
        }
        Map<String, String> signalToQuestion = new HashMap<>();
        for (SignalDefinition signal : snapshot.signals().values()) {
            if (signal.dataType() == SignalDataType.NUMBER && signal.sourceQuestionIds().size() == 1) {
                signalToQuestion.put(signal.signalId(), signal.sourceQuestionIds().iterator().next());
            }
        }
        List<QuestionSpec> questions = new ArrayList<>();
        for (QuestionDefinition question : snapshot.questionsInOrder()) {
            questions.add(question(question, mapping.get(question.questionId()), presentation, notes));
        }
        Map<String, QuestionSpec> byId = new HashMap<>();
        questions.forEach(q -> byId.put(q.id(), q));

        List<RuleSpec> rules = new ArrayList<>();
        for (PatternDefinition pattern : snapshot.patterns()) {
            RuleSpec rule = rule(snapshot, pattern, signalToQuestion, byId, notes);
            if (rule != null) {
                rules.add(rule);
            }
        }
        if (!snapshot.imbalanceRules().isEmpty()) {
            notes.add("imbalance rules are not part of the builder and were left out");
        }
        boolean hasPolicies = snapshot.questionsInOrder().stream().anyMatch(q -> q.missingPolicy() != com.company.leadershipintelligence.domain.enums.MissingDataPolicy.IGNORE);
        if (hasPolicies) {
            notes.add("missing-data policies are not part of the builder; every question ignores a missing answer");
        }
        return new Imported(new QuestionnaireDocument(name, null, questions, rules), notes);
    }

    // ---------------- questions ----------------

    private static QuestionSpec question(QuestionDefinition q, ContributionRule mapping, PresentationCatalog presentation, List<String> notes) {
        PresentationCatalog.Entry text = presentation.questions().get(q.questionId());
        String title = text == null ? null : text.name();
        String prompt = text == null ? q.questionId() : text.description();
        String dimension = mapping == null ? null : mapping.dimension().name();
        BigDecimal weight = mapping == null ? null : mapping.weight().value();
        NormalizationConfig n = q.normalization();
        if (n instanceof ScaleConfig s) {
            boolean positive = s.direction() == ScaleDirection.POSITIVE;
            if (s.min().compareTo(BigDecimal.ONE) == 0 && s.max().compareTo(BigDecimal.valueOf(5)) == 0) {
                return new QuestionSpec(q.questionId(), QuestionKind.RATING, title, prompt, null, false, dimension, weight, positive, List.of(), null,
                        List.of(), null, null, null);
            }
            if (s.min().compareTo(BigDecimal.ONE) == 0 && s.max().compareTo(BigDecimal.valueOf(7)) == 0) {
                return new QuestionSpec(q.questionId(), QuestionKind.LIKERT, title, prompt, null, false, dimension, weight, positive, List.of(), 7,
                        List.of(), null, null, null);
            }
            if (s.min().signum() == 0 && s.max().compareTo(BigDecimal.TEN) == 0) {
                return new QuestionSpec(q.questionId(), QuestionKind.NPS, title, prompt, null, false, dimension, weight, positive, List.of(), null,
                        List.of(), null, null, null);
            }
            notes.add("question " + q.questionId() + " is a scale from " + s.min() + " to " + s.max() + " and became a number question");
            return numeric(q, title, prompt, dimension, weight, s.min(), s.max(), positive);
        }
        if (n instanceof NumberConfig num) {
            if (!num.breakpoints().isEmpty()) {
                notes.add("question " + q.questionId() + " had breakpoints, which the builder does not support; its scores are now linear");
            }
            return numeric(q, title, prompt, dimension, weight, num.min(), num.max(), num.direction() == ScaleDirection.POSITIVE);
        }
        if (n instanceof MultipleChoiceConfig mc) {
            return new QuestionSpec(q.questionId(), QuestionKind.SINGLE_CHOICE, title, prompt, null, false, dimension, weight, null,
                    options(mc.values()), null, List.of(), null, null, null);
        }
        if (n instanceof MultiSelectConfig ms) {
            return new QuestionSpec(q.questionId(), QuestionKind.MULTI_CHOICE, title, prompt, null, false, dimension, weight, null,
                    options(ms.values()), null, List.of(), null, null, null);
        }
        if (n instanceof BooleanConfig b) {
            String desirable = b.trueValue().value().compareTo(b.falseValue().value()) >= 0 ? "YES" : "NO";
            return new QuestionSpec(q.questionId(), QuestionKind.YES_NO, title, prompt, null, false, dimension, weight, null, List.of(), null,
                    List.of(), null, null, desirable);
        }
        if (n instanceof TextConfig) {
            notes.add("question " + q.questionId() + " is free text; its keyword flags were left out because free text is collected and never scored");
            return new QuestionSpec(q.questionId(), QuestionKind.FREE_TEXT, title, prompt, null, false, null, null, null, List.of(), null,
                    List.of(), null, null, null);
        }
        throw new IllegalStateException("unsupported question type " + q.type());
    }

    private static QuestionSpec numeric(QuestionDefinition q, String title, String prompt, String dimension, BigDecimal weight, BigDecimal min,
                                        BigDecimal max, boolean higherIsBetter) {
        return new QuestionSpec(q.questionId(), QuestionKind.NUMERIC, title, prompt, null, false, dimension, weight, higherIsBetter, List.of(),
                null, List.of(), min, max, null);
    }

    private static List<OptionSpec> options(Map<String, com.company.leadershipintelligence.domain.valueobject.NormalizedValue> values) {
        List<OptionSpec> options = new ArrayList<>();
        values.forEach((key, value) -> options.add(new OptionSpec(key, label(key), value.value())));
        return options;
    }

    /** ALWAYS_ON_CALL becomes "Always on call". */
    static String label(String key) {
        String words = key.replace('_', ' ').toLowerCase(Locale.ROOT).trim();
        return words.isEmpty() ? key : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    // ---------------- rules ----------------

    private static RuleSpec rule(ScoringConfigurationSnapshot snapshot, PatternDefinition pattern, Map<String, String> signalToQuestion,
                                 Map<String, QuestionSpec> questions, List<String> notes) {
        ConditionSpec condition = condition(pattern.condition(), signalToQuestion, questions);
        if (condition == null) {
            notes.add("pattern " + pattern.patternId() + " uses a condition the builder cannot express (text flags, gaps or contains) and was left out");
            return null;
        }
        PresentationCatalog.Entry text = snapshot.presentation().patterns().get(pattern.patternId());
        String name = text == null ? label(pattern.code()) : text.name();
        String description = text == null ? name : text.description();
        String explanation = text == null ? null : text.explanation();

        PenaltySpec penalty = null;
        for (PenaltyDefinition candidate : snapshot.penalties()) {
            if (!candidate.triggerPatternId().equals(pattern.patternId()) || candidate.triggerPatternVersion() != pattern.version()) {
                continue;
            }
            PenaltySpec converted = penalty(candidate);
            if (penalty == null && converted != null) {
                penalty = converted;
            } else {
                notes.add("penalty " + candidate.penaltyId() + " of pattern " + pattern.patternId()
                        + (converted == null ? " uses a strategy the builder does not support" : " is a further penalty of the same pattern")
                        + " and was left out");
            }
        }
        RecommendationSpec recommendation = null;
        for (String key : pattern.actionKeys()) {
            RecommendationDefinition definition = snapshot.recommendations().get(key);
            if (definition != null && recommendation == null) {
                recommendation = new RecommendationSpec(definition.title(), definition.description(), definition.priority());
            } else {
                notes.add("action " + key + " of pattern " + pattern.patternId() + " was left out (the builder keeps one recommendation per rule)");
            }
        }
        return new RuleSpec(null, name, description, explanation, pattern.severity().name(), pattern.enabled(), condition,
                pattern.evidence().minimumSignals(), pattern.evidence().minimumConfidence().value(), penalty, recommendation);
    }

    private static PenaltySpec penalty(PenaltyDefinition p) {
        String strategy;
        BigDecimal amount;
        if (p.strategy() instanceof StrategyConfig.Fixed fixed) {
            strategy = "FIXED";
            amount = fixed.deduction().value();
        } else if (p.strategy() instanceof StrategyConfig.SeverityBased severity) {
            strategy = "SEVERITY_BASED";
            amount = severity.baseDeduction().value();
        } else {
            return null;
        }
        String policy = p.stackingPolicy().name();
        if (!BUILDER_STACKING.contains(policy)) {
            policy = "STACK";
        }
        BigDecimal cap = p.maximumCumulativeDeduction() == null ? null : p.maximumCumulativeDeduction().value();
        return new PenaltySpec(p.target().name(), strategy, amount, policy, cap, p.category(), p.priority());
    }

    /** Null when any part cannot be expressed in the builder. */
    private static ConditionSpec condition(Condition c, Map<String, String> signalToQuestion, Map<String, QuestionSpec> questions) {
        if (c instanceof Condition.And and) {
            List<ConditionSpec> children = children(and.children(), signalToQuestion, questions);
            return children == null ? null : new ConditionSpec("AND", children, null, null, null, null, null, null, null);
        }
        if (c instanceof Condition.Or or) {
            List<ConditionSpec> children = children(or.children(), signalToQuestion, questions);
            return children == null ? null : new ConditionSpec("OR", children, null, null, null, null, null, null, null);
        }
        if (c instanceof Condition.Not not) {
            ConditionSpec child = condition(not.child(), signalToQuestion, questions);
            return child == null ? null : new ConditionSpec("NOT", null, child, null, null, null, null, null, null);
        }
        if (c instanceof Condition.NumericComparison n) {
            return leaf(n.operand(), signalToQuestion, questions, (q, d) -> new ConditionSpec(n.operator().name(), null, null, q, d, n.value(), null, null, null));
        }
        if (c instanceof Condition.Between b) {
            return leaf(b.operand(), signalToQuestion, questions, (q, d) -> new ConditionSpec("BETWEEN", null, null, q, d, null, b.low(), b.high(), null));
        }
        if (c instanceof Condition.InList in) {
            return leaf(in.operand(), signalToQuestion, questions, (q, d) -> new ConditionSpec(in.negated() ? "NOT_IN" : "IN", null, null, q, d, null,
                    null, null, in.values()));
        }
        if (c instanceof Condition.Existence e) {
            return leaf(e.operand(), signalToQuestion, questions, (q, d) -> new ConditionSpec(e.negated() ? "NOT_EXISTS" : "EXISTS", null, null, q, d,
                    null, null, null, null));
        }
        return null;
    }

    private static List<ConditionSpec> children(List<Condition> conditions, Map<String, String> signalToQuestion, Map<String, QuestionSpec> questions) {
        List<ConditionSpec> result = new ArrayList<>();
        for (Condition child : conditions) {
            ConditionSpec converted = condition(child, signalToQuestion, questions);
            if (converted == null) {
                return null;
            }
            result.add(converted);
        }
        return result;
    }

    private interface LeafBuilder {
        ConditionSpec build(String question, String dimension);
    }

    private static ConditionSpec leaf(Operand operand, Map<String, String> signalToQuestion, Map<String, QuestionSpec> questions, LeafBuilder builder) {
        if (operand instanceof Operand.DimensionOperand d) {
            return builder.build(null, d.dimension().name());
        }
        if (operand instanceof Operand.SignalOperand s) {
            String question = signalToQuestion.get(s.signalId());
            if (question != null && questions.containsKey(question) && questions.get(question).kind() != QuestionKind.FREE_TEXT) {
                return builder.build(question, null);
            }
        }
        return null;
    }
}
