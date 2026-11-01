package com.company.leadershipintelligence.domain.service;

import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.DerivedSignal;
import com.company.leadershipintelligence.domain.model.DimensionScore;
import com.company.leadershipintelligence.domain.model.EvaluationContext;
import com.company.leadershipintelligence.domain.model.ImbalanceResult;
import com.company.leadershipintelligence.domain.model.InterpretationResult;
import com.company.leadershipintelligence.domain.model.NormalizedInput;
import com.company.leadershipintelligence.domain.model.OverallScore;
import com.company.leadershipintelligence.domain.model.ParticipationInfo;
import com.company.leadershipintelligence.domain.model.PatternEvaluation;
import com.company.leadershipintelligence.domain.model.ResponseSummary;
import com.company.leadershipintelligence.domain.model.ScopedResponse;
import com.company.leadershipintelligence.domain.service.responses.ResponseAggregationService;
import com.company.leadershipintelligence.domain.model.PenaltyAggregation;
import com.company.leadershipintelligence.domain.model.RunVersions;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.model.ScoringContext;
import com.company.leadershipintelligence.domain.model.ScoringFacts;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.model.ScoringResult;
import com.company.leadershipintelligence.domain.rule.ConfidenceBandConfig;
import com.company.leadershipintelligence.domain.service.aggregation.DimensionScoringService;
import com.company.leadershipintelligence.domain.service.aggregation.WeightedAverageStrategy;
import com.company.leadershipintelligence.domain.service.coverage.CoverageValidationService;
import com.company.leadershipintelligence.domain.service.explain.ExplainabilityService;
import com.company.leadershipintelligence.domain.service.imbalance.ImbalanceDetectionService;
import com.company.leadershipintelligence.domain.service.interpretation.InterpretationService;
import com.company.leadershipintelligence.domain.service.normalization.BooleanNormalizationStrategy;
import com.company.leadershipintelligence.domain.service.normalization.MultipleChoiceNormalizationStrategy;
import com.company.leadershipintelligence.domain.service.normalization.MultiSelectNormalizationStrategy;
import com.company.leadershipintelligence.domain.service.normalization.NormalizationService;
import com.company.leadershipintelligence.domain.service.normalization.NumberNormalizationStrategy;
import com.company.leadershipintelligence.domain.service.normalization.ScaleNormalizationStrategy;
import com.company.leadershipintelligence.domain.service.normalization.TextNormalizationStrategy;
import com.company.leadershipintelligence.domain.service.overall.OverallScoreService;
import com.company.leadershipintelligence.domain.service.patterns.ConditionEvaluator;
import com.company.leadershipintelligence.domain.service.patterns.PatternConfidenceService;
import com.company.leadershipintelligence.domain.service.patterns.PatternDetectionService;
import com.company.leadershipintelligence.domain.service.penalties.ConditionalPenaltyStrategy;
import com.company.leadershipintelligence.domain.service.penalties.FixedPenaltyStrategy;
import com.company.leadershipintelligence.domain.service.penalties.PenaltyAggregationService;
import com.company.leadershipintelligence.domain.service.penalties.PenaltyEligibilityService;
import com.company.leadershipintelligence.domain.service.penalties.PenaltyEvaluationService;
import com.company.leadershipintelligence.domain.service.penalties.ProportionalPenaltyStrategy;
import com.company.leadershipintelligence.domain.service.penalties.SeverityBasedPenaltyStrategy;
import com.company.leadershipintelligence.domain.service.penalties.ThresholdBasedPenaltyStrategy;
import com.company.leadershipintelligence.domain.service.signals.SignalDerivationService;
import com.company.leadershipintelligence.domain.strategy.PenaltyStrategy;
import com.company.leadershipintelligence.domain.valueobject.Decimals;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runs the scoring pipeline over one immutable configuration snapshot (specv3.md Section 6, steps 6-23). It only
 * coordinates the specialised services; it holds no scoring rule. Tenant resolution, loading the snapshot and the
 * configuration (steps 1-5) and persistence (steps 24-25) belong to the service layer: the engine is
 * transaction-unaware and never touches a repository.
 *
 * <p>Order: validate inputs and normalize, derive signals, raw dimensions with coverage, imbalances, patterns
 * with evidence and confidence, penalty eligibility and candidates, penalty aggregation, adjusted dimensions,
 * weighted score and overall penalty, final score, interpretation, trace. Any error aborts the run; a partial
 * result is never returned. Pattern and imbalance detection read raw scores only.
 */
public class ScoringOrchestrator {

    private final NormalizationService normalization;
    private final SignalDerivationService signalDerivation;
    private final DimensionScoringService dimensionScoring;
    private final ImbalanceDetectionService imbalanceDetection;
    private final PatternDetectionService patternDetection;
    private final PenaltyEvaluationService penaltyEvaluation;
    private final PenaltyAggregationService penaltyAggregation;
    private final OverallScoreService overallScore;
    private final InterpretationService interpretation;
    private final ExplainabilityService explainability;
    private final StageTimer timer;
    private final ResponseAggregationService aggregation;

    public ScoringOrchestrator(NormalizationService normalization, SignalDerivationService signalDerivation,
                               DimensionScoringService dimensionScoring, ImbalanceDetectionService imbalanceDetection,
                               PatternDetectionService patternDetection, PenaltyEvaluationService penaltyEvaluation,
                               PenaltyAggregationService penaltyAggregation, OverallScoreService overallScore,
                               InterpretationService interpretation, ExplainabilityService explainability) {
        this(normalization, signalDerivation, dimensionScoring, imbalanceDetection, patternDetection, penaltyEvaluation,
                penaltyAggregation, overallScore, interpretation, explainability, StageTimer.NOOP);
    }

    public ScoringOrchestrator(NormalizationService normalization, SignalDerivationService signalDerivation,
                               DimensionScoringService dimensionScoring, ImbalanceDetectionService imbalanceDetection,
                               PatternDetectionService patternDetection, PenaltyEvaluationService penaltyEvaluation,
                               PenaltyAggregationService penaltyAggregation, OverallScoreService overallScore,
                               InterpretationService interpretation, ExplainabilityService explainability, StageTimer timer) {
        this.timer = timer;
        this.normalization = normalization;
        this.aggregation = new ResponseAggregationService(normalization);
        this.signalDerivation = signalDerivation;
        this.dimensionScoring = dimensionScoring;
        this.imbalanceDetection = imbalanceDetection;
        this.patternDetection = patternDetection;
        this.penaltyEvaluation = penaltyEvaluation;
        this.penaltyAggregation = penaltyAggregation;
        this.overallScore = overallScore;
        this.interpretation = interpretation;
        this.explainability = explainability;
    }

    /** The standard engine: all five normalization and penalty strategies, weighted average, default services. */
    public static ScoringOrchestrator standard(ConfidenceBandConfig confidenceBands) {
        return standard(confidenceBands, StageTimer.NOOP);
    }

    public static ScoringOrchestrator standard(ConfidenceBandConfig confidenceBands, StageTimer timer) {
        ConditionEvaluator conditions = new ConditionEvaluator();
        PenaltyStrategy fixed = new FixedPenaltyStrategy();
        PenaltyStrategy proportional = new ProportionalPenaltyStrategy();
        PenaltyStrategy severity = new SeverityBasedPenaltyStrategy();
        PenaltyStrategy threshold = new ThresholdBasedPenaltyStrategy();
        List<PenaltyStrategy> penaltyStrategies = List.of(fixed, proportional, severity, threshold,
                new ConditionalPenaltyStrategy(conditions, List.of(fixed, proportional, severity, threshold)));
        return new ScoringOrchestrator(
                new NormalizationService(List.of(new ScaleNormalizationStrategy(), new MultipleChoiceNormalizationStrategy(), new MultiSelectNormalizationStrategy(),
                        new BooleanNormalizationStrategy(), new NumberNormalizationStrategy(), new TextNormalizationStrategy())),
                new SignalDerivationService(),
                new DimensionScoringService(new WeightedAverageStrategy(), new CoverageValidationService()),
                new ImbalanceDetectionService(),
                new PatternDetectionService(conditions, new PatternConfidenceService(confidenceBands)),
                new PenaltyEvaluationService(new PenaltyEligibilityService(), penaltyStrategies),
                new PenaltyAggregationService(),
                new OverallScoreService(),
                new InterpretationService(),
                new ExplainabilityService(),
                timer);
    }

    public ScoringResult score(ScoringContext context, List<ScoringInput> inputs, ScoringConfigurationSnapshot configuration) {
        requireContext(context, configuration);
        List<NormalizedInput> normalized = timer.time(StageTimer.NORMALIZATION, () -> normalization.normalize(inputs, configuration));
        return scoreNormalized(context, normalized, List.of(), Optional.empty(), configuration);
    }

    /**
     * Scores one unit from answers of several respondents (decision D45): the organization when {@code teamId} is
     * null, otherwise one team. The answers are normalized one by one and combined per question, then the rest of
     * the pipeline is the same as for {@link #score(ScoringContext, List, ScoringConfigurationSnapshot)}.
     */
    public ScoringResult scoreResponses(ScoringContext context, List<ScopedResponse> responses, String teamId,
                                        ScoringConfigurationSnapshot configuration) {
        requireContext(context, configuration);
        ResponseAggregationService.Outcome outcome = timer.time(StageTimer.NORMALIZATION,
                () -> aggregation.aggregate(responses, teamId, configuration));
        return scoreNormalized(context, outcome.inputs(), outcome.summaries(), outcome.participation(), configuration);
    }

    private static void requireContext(ScoringContext context, ScoringConfigurationSnapshot configuration) {
        if (!context.configurationVersion().equals(configuration.version())) {
            throw new IllegalArgumentException("the context names configuration " + context.configurationVersion()
                    + " but the snapshot is " + configuration.version());
        }
        requireConfigured(configuration);
    }

    private ScoringResult scoreNormalized(ScoringContext context, List<NormalizedInput> normalized, List<ResponseSummary> summaries,
                                          Optional<ParticipationInfo> participation, ScoringConfigurationSnapshot configuration) {
        List<DerivedSignal> signals = timer.time(StageTimer.SIGNALS, () -> signalDerivation.derive(normalized, configuration));
        List<DimensionScore> dimensions = timer.time(StageTimer.DIMENSIONS, () -> dimensionScoring.score(normalized, configuration));
        EvaluationContext evaluation = EvaluationContext.of(signals, dimensions, normalized, participation);

        List<ImbalanceResult> imbalances = timer.time(StageTimer.IMBALANCES, () -> imbalanceDetection.detect(configuration.imbalanceRules(), dimensions));
        List<PatternEvaluation> patterns = timer.time(StageTimer.PATTERNS, () -> patternDetection.detect(configuration.patterns(), evaluation));
        PenaltyAggregation penalties = timer.time(StageTimer.PENALTIES, () -> penaltyAggregation.aggregate(
                penaltyEvaluation.evaluate(configuration.penalties(), patterns, evaluation)));

        OverallScore overall = timer.time(StageTimer.OVERALL, () -> overallScore.calculate(dimensions, penalties, configuration.overall().weights()));
        InterpretationResult lis = interpretation.interpret(overall, configuration.overall().lisInterpretation()).orElse(null);
        Map<ScoringDimension, InterpretationResult> labels = new EnumMap<>(ScoringDimension.class);
        if (configuration.overall().dimensionInterpretation() != null) {
            overall.dimensions().forEach(adjusted -> {
                if (adjusted.adjustedScore() != null) {
                    labels.put(adjusted.dimension(),
                            interpretation.interpret(adjusted.adjustedScore(), configuration.overall().dimensionInterpretation()));
                }
            });
        }

        RunVersions versions = new RunVersions(context.engineVersion(), context.algorithmVersion(), configuration.version(),
                Decimals.ROUNDING_POLICY_VERSION, PatternConfidenceService.versionFor(participation.isPresent()));
        ScoringFacts facts = new ScoringFacts(versions, normalized, signals, dimensions, imbalances, patterns, penalties,
                overall, lis, labels, summaries);
        return new ScoringResult(context, facts, timer.time(StageTimer.EXPLAINABILITY, () -> explainability.build(facts)));
    }

    private static void requireConfigured(ScoringConfigurationSnapshot configuration) {
        if (!configuration.dimensions().isConfigured()) {
            throw new ScoringException(ScoringErrorCode.INVALID_COVERAGE_CONFIGURATION,
                    "dimension mappings and coverage settings are not configured");
        }
        if (!configuration.overall().isConfigured()) {
            throw new ScoringException(ScoringErrorCode.INVALID_WEIGHT_CONFIGURATION,
                    "overall weights and interpretation are not configured");
        }
    }
}
