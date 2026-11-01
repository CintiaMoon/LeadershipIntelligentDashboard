package com.company.leadershipintelligence.domain.service.explain;

import com.company.leadershipintelligence.domain.enums.ConditionOperator;
import com.company.leadershipintelligence.domain.enums.EvaluationBasis;
import com.company.leadershipintelligence.domain.enums.GapDirection;
import com.company.leadershipintelligence.domain.enums.MissingDataPolicy;
import com.company.leadershipintelligence.domain.enums.OutOfRangePolicy;
import com.company.leadershipintelligence.domain.enums.ScaleDirection;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.enums.Severity;
import com.company.leadershipintelligence.domain.model.DerivedSignal;
import com.company.leadershipintelligence.domain.model.DimensionScore;
import com.company.leadershipintelligence.domain.model.EvaluationContext;
import com.company.leadershipintelligence.domain.model.ImbalanceResult;
import com.company.leadershipintelligence.domain.model.InterpretationResult;
import com.company.leadershipintelligence.domain.model.NormalizedInput;
import com.company.leadershipintelligence.domain.model.OverallScore;
import com.company.leadershipintelligence.domain.model.PatternEvaluation;
import com.company.leadershipintelligence.domain.model.PenaltyAggregation;
import com.company.leadershipintelligence.domain.model.PenaltyEvaluation;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.RunVersions;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.model.ScoringFacts;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.rule.ContributionRule;
import com.company.leadershipintelligence.domain.rule.DimensionScoringConfig;
import com.company.leadershipintelligence.domain.rule.DimensionSettings;
import com.company.leadershipintelligence.domain.rule.ImbalanceRule;
import com.company.leadershipintelligence.domain.rule.InterpretationConfig;
import com.company.leadershipintelligence.domain.rule.OverallScoringConfig;
import com.company.leadershipintelligence.domain.rule.OverallWeights;
import com.company.leadershipintelligence.domain.rule.ScaleConfig;
import com.company.leadershipintelligence.domain.rule.SignalDefinition;
import com.company.leadershipintelligence.domain.service.aggregation.DimensionScoringService;
import com.company.leadershipintelligence.domain.service.aggregation.WeightedAverageStrategy;
import com.company.leadershipintelligence.domain.service.coverage.CoverageValidationService;
import com.company.leadershipintelligence.domain.service.imbalance.ImbalanceDetectionService;
import com.company.leadershipintelligence.domain.service.interpretation.InterpretationService;
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
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.company.leadershipintelligence.domain.valueobject.ScoreWeight;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.company.leadershipintelligence.domain.Fixtures.booleanSignal;
import static com.company.leadershipintelligence.domain.Fixtures.d;
import static com.company.leadershipintelligence.domain.Fixtures.normalizationService;
import static com.company.leadershipintelligence.domain.Fixtures.numberSignal;
import static com.company.leadershipintelligence.domain.Fixtures.question;
import static com.company.leadershipintelligence.domain.Fixtures.singlePointOfFailureText;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.th001;
import static com.company.leadershipintelligence.domain.service.penalties.PenaltyTestSupport.pen;

/**
 * Runs the real phase 1-7 services end to end on illustrative data, giving the explanation tests genuine
 * facts. The data: team 30, delivery 80, technical 66, leadership 70; TH-001 matches; TH-P01 deducts 8 from
 * team health; the team/delivery gap is 50.
 */
final class PipelineFixture {

    static final ScaleConfig PERCENT = new ScaleConfig(d("0"), d("100"), ScaleDirection.POSITIVE, OutOfRangePolicy.REJECT, true);
    static final RunVersions VERSIONS = new RunVersions("3.0.0", "1.0.0", new ConfigurationVersion("cfg-1"), "1", "1");

    private PipelineFixture() {
    }

    static ScoringConfigurationSnapshot snapshot(boolean extraTeamQuestions) {
        Map<String, QuestionDefinition> questions = new LinkedHashMap<>();
        List<ContributionRule> rules = new ArrayList<>();
        Map<String, ScoringDimension> basic = Map.of("T1", ScoringDimension.TEAM_HEALTH, "D1", ScoringDimension.DELIVERY_STABILITY,
                "R1", ScoringDimension.TECHNICAL_RISK, "L1", ScoringDimension.LEADERSHIP_LOAD);
        basic.forEach((q, dim) -> {
            questions.put(q, question(q, PERCENT, MissingDataPolicy.IGNORE));
            rules.add(new ContributionRule(q, dim, new ScoreWeight(d("1"))));
        });
        if (extraTeamQuestions) {
            for (String q : List.of("T2", "T3", "T4")) {
                questions.put(q, question(q, PERCENT, MissingDataPolicy.IGNORE));
                rules.add(new ContributionRule(q, ScoringDimension.TEAM_HEALTH, new ScoreWeight(d("1"))));
            }
        }
        questions.put("Q4", question("Q4", singlePointOfFailureText(), MissingDataPolicy.IGNORE));

        Map<String, SignalDefinition> signals = new LinkedHashMap<>();
        signals.put("TEAM_SUSTAINABILITY", numberSignal("TEAM_SUSTAINABILITY", "T1"));
        signals.put("WORKLOAD_PRESSURE", numberSignal("WORKLOAD_PRESSURE", "D1"));
        signals.put("SINGLE_POINT_OF_FAILURE", booleanSignal("SINGLE_POINT_OF_FAILURE", "Q4"));

        Map<ScoringDimension, DimensionSettings> settings = new EnumMap<>(ScoringDimension.class);
        for (ScoringDimension dim : ScoringDimension.values()) {
            settings.put(dim, new DimensionSettings(d("0.75")));
        }
        Map<ScoringDimension, ScoreWeight> weights = new EnumMap<>(ScoringDimension.class);
        for (ScoringDimension dim : ScoringDimension.values()) {
            weights.put(dim, new ScoreWeight(d("0.25")));
        }
        ImbalanceRule imbalance = new ImbalanceRule("TEAM_DELIVERY_GAP", 1, ScoringDimension.TEAM_HEALTH,
                ScoringDimension.DELIVERY_STABILITY, GapDirection.ABSOLUTE, ConditionOperator.GAP_GTE, d("40"), Severity.HIGH,
                EvaluationBasis.RAW_DIMENSION_SCORE, true, "explain.TEAM_DELIVERY_GAP", "recommend.TEAM_DELIVERY_GAP");
        OverallScoringConfig overall = new OverallScoringConfig(new OverallWeights(weights, d("0")),
                InterpretationConfig.illustrative(), InterpretationConfig.illustrative());
        return new ScoringConfigurationSnapshot(new ConfigurationVersion("cfg-1"), questions, signals,
                new DimensionScoringConfig(settings, rules, 4), List.of(th001()),
                List.of(pen().id("TH-P01").trigger("TH-001").max("12")
                        .strategy(new com.company.leadershipintelligence.domain.rule.StrategyConfig.Fixed(
                                new com.company.leadershipintelligence.domain.valueobject.PenaltyAmount(d("8")))).build()), List.of(imbalance), overall);
    }

    static ScoringFacts run(ScoringConfigurationSnapshot snapshot, List<ScoringInput> inputs) {
        List<NormalizedInput> normalized = normalizationService().normalize(inputs, snapshot);
        List<DerivedSignal> signals = new SignalDerivationService().derive(normalized, snapshot);
        List<DimensionScore> dimensions = new DimensionScoringService(new WeightedAverageStrategy(),
                new CoverageValidationService()).score(normalized, snapshot);
        EvaluationContext context = EvaluationContext.of(signals, dimensions, normalized);
        List<ImbalanceResult> imbalances = new ImbalanceDetectionService().detect(snapshot.imbalanceRules(), dimensions);
        List<PatternEvaluation> patterns = new PatternDetectionService(new ConditionEvaluator(),
                new PatternConfidenceService(com.company.leadershipintelligence.domain.rule.ConfidenceBandConfig.illustrative()))
                .detect(snapshot.patterns(), context);
        PenaltyStrategy fixed = new FixedPenaltyStrategy();
        PenaltyStrategy proportional = new ProportionalPenaltyStrategy();
        PenaltyStrategy severity = new SeverityBasedPenaltyStrategy();
        PenaltyStrategy threshold = new ThresholdBasedPenaltyStrategy();
        List<PenaltyStrategy> strategies = List.of(fixed, proportional, severity, threshold,
                new ConditionalPenaltyStrategy(new ConditionEvaluator(), List.of(fixed, proportional, severity, threshold)));
        List<PenaltyEvaluation> evaluations = new PenaltyEvaluationService(new PenaltyEligibilityService(), strategies)
                .evaluate(snapshot.penalties(), patterns, context);
        PenaltyAggregation penalties = new PenaltyAggregationService().aggregate(evaluations);
        OverallScore overall = new OverallScoreService().calculate(dimensions, penalties, snapshot.overall().weights());
        InterpretationService interpretation = new InterpretationService();
        InterpretationResult lis = interpretation.interpret(overall, snapshot.overall().lisInterpretation()).orElse(null);
        Map<ScoringDimension, InterpretationResult> labels = new EnumMap<>(ScoringDimension.class);
        overall.dimensions().forEach(a -> {
            if (a.adjustedScore() != null) {
                labels.put(a.dimension(), interpretation.interpret(a.adjustedScore(), snapshot.overall().dimensionInterpretation()));
            }
        });
        return new ScoringFacts(VERSIONS, normalized, signals, dimensions, imbalances, patterns, penalties, overall, lis, labels);
    }

    static List<ScoringInput> standardInputs() {
        return List.of(new ScoringInput("T1", 30), new ScoringInput("D1", 80), new ScoringInput("R1", 66),
                new ScoringInput("L1", 70), new ScoringInput("Q4", "We depend on one key person."));
    }
}
