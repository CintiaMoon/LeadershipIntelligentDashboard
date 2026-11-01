package com.company.leadershipintelligence.domain.service.patterns;

import com.company.leadershipintelligence.domain.enums.ConditionOperator;
import com.company.leadershipintelligence.domain.enums.GapDirection;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.enums.TriState;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.model.ConditionResult;
import com.company.leadershipintelligence.domain.model.EvaluationContext;
import com.company.leadershipintelligence.domain.rule.Condition;
import com.company.leadershipintelligence.domain.rule.Operand;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.company.leadershipintelligence.domain.Fixtures.assertCode;
import static com.company.leadershipintelligence.domain.Fixtures.d;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.cmp;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.ctx;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.dim;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.flag;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.insufficient;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.num;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.score;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.sig;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.textFlags;
import static org.assertj.core.api.Assertions.assertThat;

class ConditionEvaluatorTest {

    private final ConditionEvaluator evaluator = new ConditionEvaluator();
    private final EvaluationContext at75 = ctx(num("S", "75"));

    private TriState eval(Condition condition, EvaluationContext context) {
        return evaluator.evaluate(condition, context).outcome();
    }

    @Test
    void fourteenLeafOperatorsAndThreeGroupOperatorsExist() {
        assertThat(Arrays.stream(ConditionOperator.values()).filter(ConditionOperator::isLeaf).map(Enum::name)
                .collect(Collectors.toSet())).containsExactlyInAnyOrder(
                "EQ", "NE", "GT", "GTE", "LT", "LTE", "BETWEEN", "IN", "NOT_IN",
                "EXISTS", "NOT_EXISTS", "CONTAINS", "GAP_GT", "GAP_GTE");
        assertThat(Arrays.stream(ConditionOperator.values()).filter(o -> !o.isLeaf())).hasSize(3);
    }

    @Test
    void numericComparisonsAtTheBoundary() {
        assertThat(eval(cmp(sig("S"), ConditionOperator.EQ, "75"), at75)).isEqualTo(TriState.TRUE);
        assertThat(eval(cmp(sig("S"), ConditionOperator.NE, "75"), at75)).isEqualTo(TriState.FALSE);
        assertThat(eval(cmp(sig("S"), ConditionOperator.GT, "75"), at75)).isEqualTo(TriState.FALSE);
        assertThat(eval(cmp(sig("S"), ConditionOperator.GT, "74.99"), at75)).isEqualTo(TriState.TRUE);
        assertThat(eval(cmp(sig("S"), ConditionOperator.GTE, "75"), at75)).isEqualTo(TriState.TRUE);
        assertThat(eval(cmp(sig("S"), ConditionOperator.LT, "75"), at75)).isEqualTo(TriState.FALSE);
        assertThat(eval(cmp(sig("S"), ConditionOperator.LT, "75.01"), at75)).isEqualTo(TriState.TRUE);
        assertThat(eval(cmp(sig("S"), ConditionOperator.LTE, "75"), at75)).isEqualTo(TriState.TRUE);
    }

    @Test
    void equalityIgnoresScale() {
        assertThat(eval(cmp(sig("S"), ConditionOperator.EQ, "75.000"), at75)).isEqualTo(TriState.TRUE);
    }

    @Test
    void betweenIsInclusiveAtBothEnds() {
        assertThat(eval(new Condition.Between(sig("S"), d("75"), d("80")), at75)).isEqualTo(TriState.TRUE);
        assertThat(eval(new Condition.Between(sig("S"), d("70"), d("75")), at75)).isEqualTo(TriState.TRUE);
        assertThat(eval(new Condition.Between(sig("S"), d("75.01"), d("80")), at75)).isEqualTo(TriState.FALSE);
        assertThat(eval(new Condition.Between(sig("S"), d("60"), d("74.99")), at75)).isEqualTo(TriState.FALSE);
    }

    @Test
    void inAndNotIn() {
        assertThat(eval(new Condition.InList(sig("S"), false, List.of(d("50"), d("75"))), at75)).isEqualTo(TriState.TRUE);
        assertThat(eval(new Condition.InList(sig("S"), false, List.of(d("50"), d("60"))), at75)).isEqualTo(TriState.FALSE);
        assertThat(eval(new Condition.InList(sig("S"), true, List.of(d("50"), d("60"))), at75)).isEqualTo(TriState.TRUE);
        assertThat(eval(new Condition.InList(sig("S"), true, List.of(d("75"))), at75)).isEqualTo(TriState.FALSE);
    }

    @Test
    void existsAndNotExistsAreNeverUnknown() {
        assertThat(eval(new Condition.Existence(sig("S"), false), at75)).isEqualTo(TriState.TRUE);
        assertThat(eval(new Condition.Existence(sig("MISSING"), false), at75)).isEqualTo(TriState.FALSE);
        assertThat(eval(new Condition.Existence(sig("MISSING"), true), at75)).isEqualTo(TriState.TRUE);
        assertThat(eval(new Condition.Existence(sig("S"), true), at75)).isEqualTo(TriState.FALSE);
        EvaluationContext withInsufficient = ctx(List.of(), List.of(insufficient(ScoringDimension.TEAM_HEALTH)), List.of());
        assertThat(eval(new Condition.Existence(dim(ScoringDimension.TEAM_HEALTH), false), withInsufficient)).isEqualTo(TriState.FALSE);
    }

    @Test
    void containsChecksTheQuestionsDetectedFlags() {
        EvaluationContext flagged = ctx(List.of(), List.of(), List.of(textFlags("Q4", "SPOF", "OTHER")));
        EvaluationContext noFlags = ctx(List.of(), List.of(), List.of(textFlags("Q4")));
        Condition contains = new Condition.Contains(new Operand.QuestionSignalsOperand("Q4"), "SPOF");
        assertThat(eval(contains, flagged)).isEqualTo(TriState.TRUE);
        assertThat(eval(contains, noFlags)).isEqualTo(TriState.FALSE);
        assertThat(eval(contains, ctx(List.of(), List.of(), List.of()))).isEqualTo(TriState.UNKNOWN);
    }

    @Test
    void booleanSignalComparisons() {
        EvaluationContext context = ctx(flag("SPOF", true));
        assertThat(eval(new Condition.BooleanComparison(sig("SPOF"), ConditionOperator.EQ, true), context)).isEqualTo(TriState.TRUE);
        assertThat(eval(new Condition.BooleanComparison(sig("SPOF"), ConditionOperator.NE, true), context)).isEqualTo(TriState.FALSE);
        assertThat(eval(new Condition.BooleanComparison(sig("MISSING"), ConditionOperator.EQ, false), context)).isEqualTo(TriState.UNKNOWN);
    }

    @Test
    void gapDirectionAndThresholdBoundary() {
        EvaluationContext context = ctx(List.of(), List.of(score(ScoringDimension.DELIVERY_STABILITY, "79"),
                score(ScoringDimension.TECHNICAL_RISK, "49")), List.of());
        Operand delivery = dim(ScoringDimension.DELIVERY_STABILITY);
        Operand technical = dim(ScoringDimension.TECHNICAL_RISK);
        // gap is exactly 30
        assertThat(eval(new Condition.Gap(delivery, technical, GapDirection.LEFT_MINUS_RIGHT, ConditionOperator.GAP_GTE, d("30")), context))
                .isEqualTo(TriState.TRUE);
        assertThat(eval(new Condition.Gap(delivery, technical, GapDirection.LEFT_MINUS_RIGHT, ConditionOperator.GAP_GT, d("30")), context))
                .isEqualTo(TriState.FALSE);
        assertThat(eval(new Condition.Gap(delivery, technical, GapDirection.RIGHT_MINUS_LEFT, ConditionOperator.GAP_GTE, d("0")), context))
                .isEqualTo(TriState.FALSE);
        assertThat(eval(new Condition.Gap(technical, delivery, GapDirection.ABSOLUTE, ConditionOperator.GAP_GTE, d("30")), context))
                .isEqualTo(TriState.TRUE);
    }

    @Test
    void gapWithAMissingOperandIsUnknownNotZero() {
        EvaluationContext context = ctx(List.of(), List.of(score(ScoringDimension.DELIVERY_STABILITY, "79"),
                insufficient(ScoringDimension.TECHNICAL_RISK)), List.of());
        Condition gap = new Condition.Gap(dim(ScoringDimension.DELIVERY_STABILITY), dim(ScoringDimension.TECHNICAL_RISK),
                GapDirection.LEFT_MINUS_RIGHT, ConditionOperator.GAP_GT, d("30"));
        // if the missing dimension were read as zero the gap would be 79 and this would be TRUE
        assertThat(eval(gap, context)).isEqualTo(TriState.UNKNOWN);
    }

    @Test
    void missingSignalIsUnknownAndNeverReadAsZero() {
        // 0 < 10 would be TRUE if the missing value were coerced to zero
        assertThat(eval(cmp(sig("MISSING"), ConditionOperator.LT, "10"), at75)).isEqualTo(TriState.UNKNOWN);
        assertThat(eval(cmp(sig("MISSING"), ConditionOperator.EQ, "0"), at75)).isEqualTo(TriState.UNKNOWN);
    }

    @Test
    void insufficientDimensionIsUnknown() {
        EvaluationContext context = ctx(List.of(), List.of(insufficient(ScoringDimension.TEAM_HEALTH)), List.of());
        assertThat(eval(cmp(dim(ScoringDimension.TEAM_HEALTH), ConditionOperator.LT, "50"), context)).isEqualTo(TriState.UNKNOWN);
    }

    @Test
    void threeValuedLogicTables() {
        Condition t = cmp(sig("S"), ConditionOperator.GTE, "75");
        Condition f = cmp(sig("S"), ConditionOperator.LT, "75");
        Condition u = cmp(sig("MISSING"), ConditionOperator.GT, "1");
        assertThat(eval(new Condition.And(List.of(t, f)), at75)).isEqualTo(TriState.FALSE);
        assertThat(eval(new Condition.And(List.of(u, f)), at75)).isEqualTo(TriState.FALSE);
        assertThat(eval(new Condition.And(List.of(t, u)), at75)).isEqualTo(TriState.UNKNOWN);
        assertThat(eval(new Condition.And(List.of(t, t)), at75)).isEqualTo(TriState.TRUE);
        assertThat(eval(new Condition.Or(List.of(f, t)), at75)).isEqualTo(TriState.TRUE);
        assertThat(eval(new Condition.Or(List.of(u, t)), at75)).isEqualTo(TriState.TRUE);
        assertThat(eval(new Condition.Or(List.of(f, u)), at75)).isEqualTo(TriState.UNKNOWN);
        assertThat(eval(new Condition.Or(List.of(f, f)), at75)).isEqualTo(TriState.FALSE);
        assertThat(eval(new Condition.Not(t), at75)).isEqualTo(TriState.FALSE);
        assertThat(eval(new Condition.Not(f), at75)).isEqualTo(TriState.TRUE);
        assertThat(eval(new Condition.Not(u), at75)).isEqualTo(TriState.UNKNOWN);
    }

    @Test
    void nestedExpressionsAndTheTraceTree() {
        Condition t = cmp(sig("S"), ConditionOperator.GTE, "75");
        Condition f = cmp(sig("S"), ConditionOperator.LT, "75");
        Condition nested = new Condition.And(List.of(new Condition.Or(List.of(f, t)), new Condition.Not(f)));
        ConditionResult result = evaluator.evaluate(nested, at75);
        assertThat(result.outcome()).isEqualTo(TriState.TRUE);
        assertThat(result.children()).hasSize(2);
        assertThat(result.children().get(0).children()).extracting(ConditionResult::outcome)
                .containsExactly(TriState.FALSE, TriState.TRUE);
        assertThat(result.children().get(1).operator()).isEqualTo(ConditionOperator.NOT);
        assertThat(result.children().get(0).children().get(1).detail()).isEqualTo("signal:S=75 GTE 75");
    }

    @Test
    void evaluationIsDeterministic() {
        Condition c = new Condition.And(List.of(cmp(sig("S"), ConditionOperator.GTE, "75"), cmp(sig("MISSING"), ConditionOperator.GT, "1")));
        assertThat(evaluator.evaluate(c, at75)).isEqualTo(evaluator.evaluate(c, at75));
    }

    // ---- structural validation on construction ----

    @Test
    void invalidArityAndShapesAreRejected() {
        Condition leaf = cmp(sig("S"), ConditionOperator.GTE, "75");
        assertCode(() -> new Condition.And(List.of(leaf)), ScoringErrorCode.INVALID_CONDITION_EXPRESSION);
        assertCode(() -> new Condition.Or(List.of()), ScoringErrorCode.INVALID_CONDITION_EXPRESSION);
        assertCode(() -> new Condition.Not(null), ScoringErrorCode.INVALID_CONDITION_EXPRESSION);
        assertCode(() -> new Condition.Between(sig("S"), d("80"), d("70")), ScoringErrorCode.INVALID_CONDITION_EXPRESSION);
        assertCode(() -> new Condition.NumericComparison(sig("S"), ConditionOperator.BETWEEN, d("1")), ScoringErrorCode.INVALID_CONDITION_EXPRESSION);
        assertCode(() -> new Condition.BooleanComparison(sig("S"), ConditionOperator.GT, true), ScoringErrorCode.INVALID_CONDITION_EXPRESSION);
        assertCode(() -> new Condition.InList(sig("S"), false, List.of()), ScoringErrorCode.INVALID_CONDITION_EXPRESSION);
        assertCode(() -> new Condition.Gap(sig("S"), sig("S"), GapDirection.ABSOLUTE, ConditionOperator.GAP_GT, d("1")), ScoringErrorCode.INVALID_CONDITION_EXPRESSION);
        assertCode(() -> new Condition.Gap(sig("A"), sig("B"), GapDirection.ABSOLUTE, ConditionOperator.GT, d("1")), ScoringErrorCode.INVALID_CONDITION_EXPRESSION);
        assertCode(() -> new Condition.Gap(sig("A"), sig("B"), null, ConditionOperator.GAP_GT, d("1")), ScoringErrorCode.INVALID_CONDITION_EXPRESSION);
    }
}
