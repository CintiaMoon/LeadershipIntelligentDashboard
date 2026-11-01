package com.company.leadershipintelligence.domain.service.patterns;

import com.company.leadershipintelligence.domain.enums.ConditionOperator;
import com.company.leadershipintelligence.domain.enums.MissingDataPolicy;
import com.company.leadershipintelligence.domain.enums.ScaleDirection;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.enums.Severity;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.rule.Condition;
import com.company.leadershipintelligence.domain.rule.ConditionValidator;
import com.company.leadershipintelligence.domain.rule.Operand;
import com.company.leadershipintelligence.domain.rule.SignalDefinition;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.company.leadershipintelligence.domain.Fixtures.assertCode;
import static com.company.leadershipintelligence.domain.Fixtures.booleanSignal;
import static com.company.leadershipintelligence.domain.Fixtures.d;
import static com.company.leadershipintelligence.domain.Fixtures.numberSignal;
import static com.company.leadershipintelligence.domain.Fixtures.question;
import static com.company.leadershipintelligence.domain.Fixtures.scale15;
import static com.company.leadershipintelligence.domain.Fixtures.singlePointOfFailureText;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.cmp;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.dim;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.pattern;
import static com.company.leadershipintelligence.domain.service.patterns.PatternTestSupport.sig;
import static org.assertj.core.api.Assertions.assertThat;

class ConditionValidatorTest {

    private final Map<String, QuestionDefinition> questions = Map.of(
            "Q1", question("Q1", scale15(ScaleDirection.POSITIVE), MissingDataPolicy.IGNORE),
            "Q4", question("Q4", singlePointOfFailureText(), MissingDataPolicy.IGNORE));
    private final Map<String, SignalDefinition> signals = Map.of(
            "WORKLOAD", numberSignal("WORKLOAD", "Q1"),
            "SINGLE_POINT_OF_FAILURE", booleanSignal("SINGLE_POINT_OF_FAILURE", "Q4"));

    private List<String> problems(Condition c) {
        return ConditionValidator.problems(c, signals, questions);
    }

    @Test
    void validExpressionHasNoProblems() {
        Condition c = new Condition.And(List.of(
                cmp(sig("WORKLOAD"), ConditionOperator.GTE, "75"),
                new Condition.BooleanComparison(sig("SINGLE_POINT_OF_FAILURE"), ConditionOperator.EQ, true),
                new Condition.Contains(new Operand.QuestionSignalsOperand("Q4"), "SINGLE_POINT_OF_FAILURE"),
                cmp(dim(ScoringDimension.TEAM_HEALTH), ConditionOperator.LT, "50")));
        assertThat(problems(c)).isEmpty();
        ConditionValidator.validate(c, signals, questions);
    }

    @Test
    void unknownSignalsAndQuestionsAreRejected() {
        assertThat(problems(cmp(sig("NOPE"), ConditionOperator.GT, "10"))).containsExactly("unknown signal NOPE");
        assertThat(problems(new Condition.Contains(new Operand.QuestionSignalsOperand("QX"), "SINGLE_POINT_OF_FAILURE")))
                .containsExactly("unknown question QX");
        assertThat(problems(new Condition.Contains(new Operand.QuestionSignalsOperand("Q4"), "NOPE")))
                .containsExactly("CONTAINS references unknown signal NOPE");
    }

    @Test
    void typeIncompatibleOperatorsAreRejected() {
        assertThat(problems(cmp(sig("SINGLE_POINT_OF_FAILURE"), ConditionOperator.GT, "10"))).hasSize(1).first().asString().contains("numeric operand");
        assertThat(problems(new Condition.BooleanComparison(sig("WORKLOAD"), ConditionOperator.EQ, true))).hasSize(1);
        assertThat(problems(new Condition.Between(new Operand.QuestionSignalsOperand("Q4"), d("1"), d("2")))).isNotEmpty();
        assertThat(problems(new Condition.Contains(new Operand.QuestionSignalsOperand("Q1"), "SINGLE_POINT_OF_FAILURE")))
                .containsExactly("question Q1 never produces flags");
    }

    @Test
    void thresholdsMustLieOnTheScoreScale() {
        assertThat(problems(cmp(sig("WORKLOAD"), ConditionOperator.GT, "101"))).hasSize(1);
        assertThat(problems(cmp(sig("WORKLOAD"), ConditionOperator.GT, "-1"))).hasSize(1);
        assertThat(problems(cmp(sig("WORKLOAD"), ConditionOperator.GT, "100"))).isEmpty();
        assertThat(problems(cmp(sig("WORKLOAD"), ConditionOperator.GT, "0"))).isEmpty();
        assertThat(problems(new Condition.Between(sig("WORKLOAD"), d("-5"), d("50")))).hasSize(1);
    }

    @Test
    void nestingDeeperThanTheLimitIsRejected() {
        Condition c = cmp(sig("WORKLOAD"), ConditionOperator.GT, "10");
        for (int i = 1; i < ConditionValidator.MAX_DEPTH; i++) {
            c = new Condition.Not(c);
        }
        assertThat(problems(c)).isEmpty();
        assertThat(problems(new Condition.Not(c))).hasSize(1);
    }

    @Test
    void allProblemsAreReportedTogether() {
        Condition c = new Condition.And(List.of(
                cmp(sig("NOPE"), ConditionOperator.GT, "10"),
                cmp(sig("WORKLOAD"), ConditionOperator.GT, "500")));
        assertThat(problems(c)).hasSize(2);
        assertCode(() -> ConditionValidator.validate(c, signals, questions), ScoringErrorCode.INVALID_CONDITION_EXPRESSION);
    }

    // ---- snapshot level ----

    private ScoringConfigurationSnapshot snapshot(com.company.leadershipintelligence.domain.rule.PatternDefinition... patterns) {
        return new ScoringConfigurationSnapshot(new ConfigurationVersion("t"), new LinkedHashMap<>(questions),
                new LinkedHashMap<>(signals), com.company.leadershipintelligence.domain.rule.DimensionScoringConfig.none(),
                List.of(patterns));
    }

    @Test
    void snapshotRejectsPatternsWithUnknownSignals() {
        assertCode(() -> snapshot(pattern("TH-001", 1, Severity.HIGH, cmp(sig("NOPE"), ConditionOperator.GT, "10"), 1, "0.5", true, false)),
                ScoringErrorCode.INVALID_CONDITION_EXPRESSION);
    }

    @Test
    void snapshotRejectsDuplicatePatternsAndImpossibleEvidenceRequirements() {
        var ok = pattern("TH-001", 1, Severity.HIGH, cmp(sig("WORKLOAD"), ConditionOperator.GT, "10"), 1, "0.5", true, false);
        assertCode(() -> snapshot(ok, ok), ScoringErrorCode.INVALID_PATTERN_CONFIGURATION);
        assertThat(snapshot(ok, pattern("TH-001", 2, Severity.HIGH, cmp(sig("WORKLOAD"), ConditionOperator.GT, "10"), 1, "0.5", true, false))
                .patterns()).hasSize(2);
        var tooDemanding = pattern("TH-002", 1, Severity.HIGH, cmp(sig("WORKLOAD"), ConditionOperator.GT, "10"), 2, "0.5", true, false);
        assertCode(() -> snapshot(tooDemanding), ScoringErrorCode.INVALID_PATTERN_CONFIGURATION);
    }
}
