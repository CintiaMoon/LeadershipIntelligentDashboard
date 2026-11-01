package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.enums.SignalDataType;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.rule.ConditionValidator;
import com.company.leadershipintelligence.domain.rule.ContributionRule;
import com.company.leadershipintelligence.domain.rule.DimensionScoringConfig;
import com.company.leadershipintelligence.domain.rule.ImbalanceRule;
import com.company.leadershipintelligence.domain.rule.OverallScoringConfig;
import com.company.leadershipintelligence.domain.rule.PatternDefinition;
import com.company.leadershipintelligence.domain.rule.PresentationCatalog;
import com.company.leadershipintelligence.domain.rule.RecommendationDefinition;
import com.company.leadershipintelligence.domain.rule.ResponseSettings;
import com.company.leadershipintelligence.domain.rule.PenaltyDefinition;
import com.company.leadershipintelligence.domain.rule.StrategyConfig;
import com.company.leadershipintelligence.domain.enums.PenaltyTarget;
import com.company.leadershipintelligence.domain.enums.StackingPolicy;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;
import com.company.leadershipintelligence.domain.rule.SignalDefinition;
import com.company.leadershipintelligence.domain.rule.TextConfig;
import com.company.leadershipintelligence.domain.rule.TextSignalRule;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Immutable effective configuration for one run (specv3.md Sections 6.1, 23). This phase carries the
 * question and signal registries, dimension mappings and patterns; later phases add penalties and
 * interpretation. References are validated on construction: unknown signals and unsupported conditions fail.
 */
public record ScoringConfigurationSnapshot(
        ConfigurationVersion version,
        Map<String, QuestionDefinition> questions,
        Map<String, SignalDefinition> signals,
        DimensionScoringConfig dimensions,
        List<PatternDefinition> patterns,
        List<PenaltyDefinition> penalties,
        List<ImbalanceRule> imbalanceRules,
        OverallScoringConfig overall,
        Map<String, RecommendationDefinition> recommendations,
        PresentationCatalog presentation,
        ResponseSettings responses) {

    /** Without a {@code responses} block the unvalidated defaults apply (decision D45). */
    public ScoringConfigurationSnapshot(ConfigurationVersion version, Map<String, QuestionDefinition> questions,
                                        Map<String, SignalDefinition> signals, DimensionScoringConfig dimensions,
                                        List<PatternDefinition> patterns, List<PenaltyDefinition> penalties,
                                        List<ImbalanceRule> imbalanceRules, OverallScoringConfig overall,
                                        Map<String, RecommendationDefinition> recommendations,
                                        PresentationCatalog presentation) {
        this(version, questions, signals, dimensions, patterns, penalties, imbalanceRules, overall, recommendations,
                presentation, ResponseSettings.defaults());
    }

    public ScoringConfigurationSnapshot(ConfigurationVersion version, Map<String, QuestionDefinition> questions,
                                        Map<String, SignalDefinition> signals) {
        this(version, questions, signals, DimensionScoringConfig.none(), List.of(), List.of(), List.of());
    }

    public ScoringConfigurationSnapshot(ConfigurationVersion version, Map<String, QuestionDefinition> questions,
                                        Map<String, SignalDefinition> signals, DimensionScoringConfig dimensions) {
        this(version, questions, signals, dimensions, List.of(), List.of(), List.of());
    }

    public ScoringConfigurationSnapshot(ConfigurationVersion version, Map<String, QuestionDefinition> questions,
                                        Map<String, SignalDefinition> signals, DimensionScoringConfig dimensions,
                                        List<PatternDefinition> patterns) {
        this(version, questions, signals, dimensions, patterns, List.of(), List.of());
    }

    public ScoringConfigurationSnapshot(ConfigurationVersion version, Map<String, QuestionDefinition> questions,
                                        Map<String, SignalDefinition> signals, DimensionScoringConfig dimensions,
                                        List<PatternDefinition> patterns, List<PenaltyDefinition> penalties) {
        this(version, questions, signals, dimensions, patterns, penalties, List.of());
    }

    public ScoringConfigurationSnapshot(ConfigurationVersion version, Map<String, QuestionDefinition> questions,
                                        Map<String, SignalDefinition> signals, DimensionScoringConfig dimensions,
                                        List<PatternDefinition> patterns, List<PenaltyDefinition> penalties,
                                        List<ImbalanceRule> imbalanceRules) {
        this(version, questions, signals, dimensions, patterns, penalties, imbalanceRules, OverallScoringConfig.none());
    }

    public ScoringConfigurationSnapshot(ConfigurationVersion version, Map<String, QuestionDefinition> questions,
                                        Map<String, SignalDefinition> signals, DimensionScoringConfig dimensions,
                                        List<PatternDefinition> patterns, List<PenaltyDefinition> penalties,
                                        List<ImbalanceRule> imbalanceRules, OverallScoringConfig overall) {
        this(version, questions, signals, dimensions, patterns, penalties, imbalanceRules, overall, Map.of());
    }

    public ScoringConfigurationSnapshot(ConfigurationVersion version, Map<String, QuestionDefinition> questions,
                                        Map<String, SignalDefinition> signals, DimensionScoringConfig dimensions,
                                        List<PatternDefinition> patterns, List<PenaltyDefinition> penalties,
                                        List<ImbalanceRule> imbalanceRules, OverallScoringConfig overall,
                                        Map<String, RecommendationDefinition> recommendations) {
        this(version, questions, signals, dimensions, patterns, penalties, imbalanceRules, overall, recommendations,
                PresentationCatalog.empty());
    }

    public ScoringConfigurationSnapshot {
        if (version == null || questions == null || signals == null || dimensions == null || patterns == null || penalties == null || imbalanceRules == null || overall == null || recommendations == null || presentation == null || responses == null) {
            throw new IllegalArgumentException("version, questions, signals, dimensions and patterns are required");
        }
        questions = Collections.unmodifiableSortedMap(new TreeMap<>(questions));
        signals = Collections.unmodifiableSortedMap(new TreeMap<>(signals));
        List<PatternDefinition> sorted = new ArrayList<>(patterns);
        sorted.sort(Comparator.comparing(PatternDefinition::patternId).thenComparingInt(PatternDefinition::version));
        patterns = List.copyOf(sorted);
        validateReferences(questions, signals);
        validateMappings(questions, dimensions);
        List<PenaltyDefinition> orderedPenalties = new ArrayList<>(penalties);
        orderedPenalties.sort(Comparator.comparingInt(PenaltyDefinition::ruleOrder)
                .thenComparing(PenaltyDefinition::penaltyId).thenComparingInt(PenaltyDefinition::version));
        penalties = List.copyOf(orderedPenalties);
        List<ImbalanceRule> orderedRules = new ArrayList<>(imbalanceRules);
        orderedRules.sort(Comparator.comparing(ImbalanceRule::ruleId).thenComparingInt(ImbalanceRule::version));
        imbalanceRules = List.copyOf(orderedRules);
        validatePatterns(patterns, questions, signals);
        validatePenalties(penalties, patterns, questions, signals);
        validateImbalances(imbalanceRules);
        recommendations = Collections.unmodifiableSortedMap(new TreeMap<>(recommendations));
        validatePresentation(presentation, patterns, penalties, signals, questions);
    }

    private static void validatePresentation(PresentationCatalog presentation, List<PatternDefinition> patterns,
                                             List<PenaltyDefinition> penalties, Map<String, SignalDefinition> signals,
                                             Map<String, QuestionDefinition> questions) {
        Set<String> patternIds = new HashSet<>();
        patterns.forEach(p -> patternIds.add(p.patternId()));
        Set<String> penaltyIds = new HashSet<>();
        penalties.forEach(p -> penaltyIds.add(p.penaltyId()));
        Set<String> dimensionNames = new HashSet<>();
        for (com.company.leadershipintelligence.domain.enums.ScoringDimension d : com.company.leadershipintelligence.domain.enums.ScoringDimension.values()) {
            dimensionNames.add(d.name());
        }
        requireKnown("pattern", presentation.patterns().keySet(), patternIds);
        requireKnown("penalty", presentation.penalties().keySet(), penaltyIds);
        requireKnown("dimension", presentation.dimensions().keySet(), dimensionNames);
        requireKnown("signal", presentation.signals().keySet(), signals.keySet());
        requireKnown("question", presentation.questions().keySet(), questions.keySet());
    }

    private static void requireKnown(String kind, Set<String> keys, Set<String> known) {
        for (String key : keys) {
            if (!known.contains(key)) {
                throw new IllegalArgumentException("presentation refers to unknown " + kind + " '" + key + "'");
            }
        }
    }

    public Optional<QuestionDefinition> question(String questionId) {
        return Optional.ofNullable(questions.get(questionId));
    }

    /** Question definitions in question-id order (deterministic). */
    public Collection<QuestionDefinition> questionsInOrder() {
        return questions.values();
    }

    private static void validateReferences(Map<String, QuestionDefinition> questions,
                                           Map<String, SignalDefinition> signals) {
        for (QuestionDefinition question : questions.values()) {
            if (question.riskSignalId() != null) {
                requireBooleanSignal(signals, question.riskSignalId(), question.questionId());
            }
            if (question.normalization() instanceof TextConfig text) {
                for (TextSignalRule rule : text.signals()) {
                    requireBooleanSignal(signals, rule.signalId(), question.questionId());
                }
            }
        }
        for (SignalDefinition signal : signals.values()) {
            for (String source : signal.sourceQuestionIds()) {
                QuestionDefinition question = questions.get(source);
                if (question == null) {
                    throw invalid("signal " + signal.signalId() + " references unknown question " + source);
                }
                if (signal.dataType() == SignalDataType.NUMBER && question.type() == QuestionType.TEXT) {
                    throw invalid("NUMBER signal " + signal.signalId() + " cannot use TEXT question " + source);
                }
            }
        }
    }

    private static void validateMappings(Map<String, QuestionDefinition> questions, DimensionScoringConfig dimensions) {
        for (ContributionRule rule : dimensions.rules()) {
            QuestionDefinition question = questions.get(rule.questionId());
            if (question == null) {
                throw new ScoringException(ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION,
                        "mapping references unknown question " + rule.questionId());
            }
            if (question.type() == QuestionType.TEXT) {
                throw new ScoringException(ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION,
                        "TEXT question " + rule.questionId() + " has no numeric value and cannot be mapped to a dimension");
            }
        }
    }

    private static void validatePatterns(List<PatternDefinition> patterns, Map<String, QuestionDefinition> questions,
                                         Map<String, SignalDefinition> signals) {
        Set<String> seen = new HashSet<>();
        for (PatternDefinition pattern : patterns) {
            if (!seen.add(pattern.patternId() + "|" + pattern.version())) {
                throw new ScoringException(ScoringErrorCode.INVALID_PATTERN_CONFIGURATION,
                        "duplicate pattern " + pattern.patternId() + " version " + pattern.version());
            }
            ConditionValidator.validate(pattern.condition(), signals, questions);
            if (pattern.evidence().minimumSignals() > pattern.condition().operands().size()) {
                throw new ScoringException(ScoringErrorCode.INVALID_PATTERN_CONFIGURATION,
                        "pattern " + pattern.patternId() + " requires more independent signals than its condition references");
            }
        }
    }

    private static void validatePenalties(List<PenaltyDefinition> penalties, List<PatternDefinition> patterns,
                                          Map<String, QuestionDefinition> questions, Map<String, SignalDefinition> signals) {
        Set<String> seen = new HashSet<>();
        Map<String, List<PenaltyDefinition>> byTrigger = new TreeMap<>();
        Map<PenaltyTarget, Set<PenaltyAmount>> capsByTarget = new java.util.EnumMap<>(PenaltyTarget.class);
        for (PenaltyDefinition penalty : penalties) {
            if (!seen.add(penalty.penaltyId() + "|" + penalty.version())) {
                throw penaltyInvalid("duplicate penalty " + penalty.penaltyId() + " version " + penalty.version());
            }
            boolean triggerExists = patterns.stream().anyMatch(p -> p.patternId().equals(penalty.triggerPatternId())
                    && p.version() == penalty.triggerPatternVersion());
            if (!triggerExists) {
                throw penaltyInvalid("penalty " + penalty.penaltyId() + " references unknown pattern "
                        + penalty.triggerPatternId() + " version " + penalty.triggerPatternVersion());
            }
            if (penalty.strategy() instanceof StrategyConfig.Conditional conditional) {
                ConditionValidator.validate(conditional.condition(), signals, questions);
            }
            if (penalty.stackingPolicy() == StackingPolicy.CAPPED_STACK) {
                capsByTarget.computeIfAbsent(penalty.target(), t -> new HashSet<>()).add(penalty.maximumCumulativeDeduction());
            }
            byTrigger.computeIfAbsent(penalty.triggerPatternId() + "|" + penalty.triggerPatternVersion(), k -> new ArrayList<>()).add(penalty);
        }
        capsByTarget.forEach((target, caps) -> {
            if (caps.size() > 1) {
                throw penaltyInvalid("CAPPED_STACK penalties on " + target + " must share one cumulative cap");
            }
        });
        for (List<PenaltyDefinition> group : byTrigger.values()) {
            boolean overall = group.stream().anyMatch(p -> p.target() == PenaltyTarget.OVERALL);
            boolean dimension = group.stream().anyMatch(p -> p.target() != PenaltyTarget.OVERALL);
            if (overall && dimension) {
                for (PenaltyDefinition penalty : group) {
                    if (penalty.dualApplicationRationale() == null) {
                        throw penaltyInvalid("penalty " + penalty.penaltyId() + " shares its trigger between a dimension and OVERALL"
                                + " and needs a documented dual-application rationale");
                    }
                }
            }
        }
    }

    private static void validateImbalances(List<ImbalanceRule> rules) {
        Set<String> seen = new HashSet<>();
        for (ImbalanceRule rule : rules) {
            if (!seen.add(rule.ruleId() + "|" + rule.version())) {
                throw new ScoringException(ScoringErrorCode.INVALID_IMBALANCE_CONFIGURATION,
                        "duplicate imbalance rule " + rule.ruleId() + " version " + rule.version());
            }
        }
    }

    private static ScoringException penaltyInvalid(String message) {
        return new ScoringException(ScoringErrorCode.INVALID_PENALTY_CONFIGURATION, message);
    }

    private static void requireBooleanSignal(Map<String, SignalDefinition> signals, String signalId, String questionId) {
        SignalDefinition signal = signals.get(signalId);
        if (signal == null || signal.dataType() != SignalDataType.BOOLEAN) {
            throw invalid("question " + questionId + " references unregistered BOOLEAN signal " + signalId);
        }
        if (!signal.sourceQuestionIds().contains(questionId)) {
            throw invalid("signal " + signalId + " does not list question " + questionId + " as a source");
        }
    }

    private static ScoringException invalid(String message) {
        return new ScoringException(ScoringErrorCode.INVALID_SIGNAL_CONFIGURATION, message);
    }
}
