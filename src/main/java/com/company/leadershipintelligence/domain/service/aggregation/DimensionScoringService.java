package com.company.leadershipintelligence.domain.service.aggregation;

import com.company.leadershipintelligence.domain.enums.ContributionStatus;
import com.company.leadershipintelligence.domain.enums.CoverageStatus;
import com.company.leadershipintelligence.domain.enums.DimensionStatus;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.DimensionContribution;
import com.company.leadershipintelligence.domain.model.DimensionCoverage;
import com.company.leadershipintelligence.domain.model.DimensionScore;
import com.company.leadershipintelligence.domain.model.NormalizedInput;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.rule.ContributionRule;
import com.company.leadershipintelligence.domain.rule.DimensionScoringConfig;
import com.company.leadershipintelligence.domain.service.coverage.CoverageValidationService;
import com.company.leadershipintelligence.domain.strategy.AggregationConfiguration;
import com.company.leadershipintelligence.domain.strategy.AggregationResult;
import com.company.leadershipintelligence.domain.strategy.AggregationStrategy;
import com.company.leadershipintelligence.domain.strategy.WeightedContribution;
import com.company.leadershipintelligence.domain.valueobject.ScoreValue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Raw dimension scores (specv3.md Section 10). Only valid, positive-weight inputs enter the aggregate and
 * the coverage denominator. A dimension that fails coverage, or has no valid input, is INSUFFICIENT_DATA
 * with no score (decision D18, invariant 24). Results are in dimension enum order, contributions in
 * question-id order.
 */
public class DimensionScoringService {

    private static final AggregationConfiguration AGGREGATION = new AggregationConfiguration("1");

    private final AggregationStrategy aggregation;
    private final CoverageValidationService coverageService;

    public DimensionScoringService(AggregationStrategy aggregation, CoverageValidationService coverageService) {
        this.aggregation = aggregation;
        this.coverageService = coverageService;
    }

    public List<DimensionScore> score(List<NormalizedInput> inputs, ScoringConfigurationSnapshot configuration) {
        DimensionScoringConfig config = configuration.dimensions();
        if (!config.isConfigured()) {
            throw new ScoringException(ScoringErrorCode.INVALID_COVERAGE_CONFIGURATION,
                    "dimension mappings and coverage settings are not configured");
        }
        Map<String, NormalizedInput> byQuestion = new HashMap<>();
        for (NormalizedInput input : inputs) {
            byQuestion.put(input.questionId(), input);
        }
        List<DimensionScore> scores = new ArrayList<>();
        for (ScoringDimension dimension : ScoringDimension.values()) {
            scores.add(scoreDimension(dimension, config, byQuestion));
        }
        return List.copyOf(scores);
    }

    private DimensionScore scoreDimension(ScoringDimension dimension, DimensionScoringConfig config,
                                          Map<String, NormalizedInput> byQuestion) {
        List<ContributionRule> rules = config.rulesFor(dimension);
        List<WeightedContribution> valid = new ArrayList<>();
        BigDecimal expected = BigDecimal.ZERO;
        int applicable = 0;
        int answered = 0;
        for (ContributionRule rule : rules) {
            if (rule.weight().isZero()) {
                continue;
            }
            applicable++;
            expected = expected.add(rule.weight().value());
            NormalizedInput input = byQuestion.get(rule.questionId());
            if (input != null && input.value().isPresent()) {
                answered++;
                valid.add(new WeightedContribution(rule.questionId(), input.normalizedValue(), rule.weight()));
            }
        }
        BigDecimal observed = valid.stream().map(c -> c.weight().value()).reduce(BigDecimal.ZERO, BigDecimal::add);
        DimensionCoverage coverage = coverageService.evaluate(config.settings().get(dimension),
                applicable, answered, valid.size(), expected, observed);

        if (coverage.status() == CoverageStatus.INSUFFICIENT) {
            return new DimensionScore(dimension, DimensionStatus.INSUFFICIENT_DATA, null, coverage,
                    contributions(dimension, rules, byQuestion, null));
        }
        AggregationResult result = aggregation.aggregate(valid, AGGREGATION);
        ScoreValue raw = ScoreValue.clamp(result.score()).rounded();
        return new DimensionScore(dimension, DimensionStatus.VALID, raw, coverage,
                contributions(dimension, rules, byQuestion, result));
    }

    private static List<DimensionContribution> contributions(ScoringDimension dimension, List<ContributionRule> rules,
                                                             Map<String, NormalizedInput> byQuestion,
                                                             AggregationResult result) {
        Map<String, AggregationResult.Share> shares = new HashMap<>();
        if (result != null) {
            result.shares().forEach(s -> shares.put(s.questionId(), s));
        }
        List<DimensionContribution> list = new ArrayList<>();
        for (ContributionRule rule : rules) {
            NormalizedInput input = byQuestion.get(rule.questionId());
            boolean hasValue = input != null && input.value().isPresent();
            if (rule.weight().isZero()) {
                list.add(new DimensionContribution(rule.questionId(), dimension,
                        hasValue ? input.normalizedValue() : null, rule.weight(), null, null,
                        hasValue ? ContributionStatus.EXCLUDED_ZERO_WEIGHT : ContributionStatus.EXCLUDED_NO_VALUE));
            } else if (!hasValue) {
                list.add(new DimensionContribution(rule.questionId(), dimension, null, rule.weight(), null, null,
                        ContributionStatus.EXCLUDED_NO_VALUE));
            } else if (result == null) {
                list.add(new DimensionContribution(rule.questionId(), dimension, input.normalizedValue(), rule.weight(),
                        null, null, ContributionStatus.NOT_SCORED));
            } else {
                AggregationResult.Share share = shares.get(rule.questionId());
                list.add(new DimensionContribution(rule.questionId(), dimension, input.normalizedValue(), rule.weight(),
                        share.normalizedWeight(), share.contribution(), ContributionStatus.APPLIED));
            }
        }
        return list;
    }
}
