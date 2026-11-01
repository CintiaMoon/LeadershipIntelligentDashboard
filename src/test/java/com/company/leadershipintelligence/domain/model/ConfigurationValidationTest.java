package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.MissingDataPolicy;
import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.enums.ScaleDirection;
import com.company.leadershipintelligence.domain.enums.SignalDataType;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.rule.SignalDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.company.leadershipintelligence.domain.Fixtures.assertCode;
import static com.company.leadershipintelligence.domain.Fixtures.booleanSignal;
import static com.company.leadershipintelligence.domain.Fixtures.numberSignal;
import static com.company.leadershipintelligence.domain.Fixtures.nv;
import static com.company.leadershipintelligence.domain.Fixtures.question;
import static com.company.leadershipintelligence.domain.Fixtures.scale15;
import static com.company.leadershipintelligence.domain.Fixtures.singlePointOfFailureText;
import static com.company.leadershipintelligence.domain.Fixtures.snapshot;
import static com.company.leadershipintelligence.domain.Fixtures.yesIsGood;
import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationValidationTest {

    @Test
    void questionTypeMustMatchItsNormalizationConfig() {
        assertCode(() -> new QuestionDefinition("Q01", QuestionType.BOOLEAN, scale15(ScaleDirection.POSITIVE),
                        MissingDataPolicy.IGNORE, null, null), ScoringErrorCode.INVALID_NORMALIZATION_CONFIGURATION);
    }

    @Test
    void neutralPolicyRequiresAnExplicitValueAndOthersForbidIt() {
        assertCode(() -> new QuestionDefinition("Q01", QuestionType.SCALE, scale15(ScaleDirection.POSITIVE),
                MissingDataPolicy.NEUTRAL, null, null), ScoringErrorCode.INVALID_NORMALIZATION_CONFIGURATION);
        assertCode(() -> new QuestionDefinition("Q01", QuestionType.SCALE, scale15(ScaleDirection.POSITIVE),
                MissingDataPolicy.IGNORE, nv("50"), null), ScoringErrorCode.INVALID_NORMALIZATION_CONFIGURATION);
    }

    @Test
    void riskSignalPolicyRequiresASignalAndOthersForbidIt() {
        assertCode(() -> new QuestionDefinition("Q01", QuestionType.SCALE, scale15(ScaleDirection.POSITIVE),
                MissingDataPolicy.RISK_SIGNAL, null, null), ScoringErrorCode.INVALID_NORMALIZATION_CONFIGURATION);
        assertCode(() -> new QuestionDefinition("Q01", QuestionType.SCALE, scale15(ScaleDirection.POSITIVE),
                MissingDataPolicy.REQUIRED, null, "X"), ScoringErrorCode.INVALID_NORMALIZATION_CONFIGURATION);
    }

    @Test
    void textSignalsMustBeRegistered() {
        assertCode(() -> snapshot(List.of(question("Q04", singlePointOfFailureText(), MissingDataPolicy.IGNORE)), List.of()),
                ScoringErrorCode.INVALID_SIGNAL_CONFIGURATION);
        assertThat(snapshot(List.of(question("Q04", singlePointOfFailureText(), MissingDataPolicy.IGNORE)),
                List.of(booleanSignal("SINGLE_POINT_OF_FAILURE", "Q04"))).signals()).containsKey("SINGLE_POINT_OF_FAILURE");
    }

    @Test
    void signalSourcesMustExistAndFitTheSignalType() {
        assertCode(() -> snapshot(List.of(), List.of(numberSignal("TEAM_SUSTAINABILITY", "Q01"))),
                ScoringErrorCode.INVALID_SIGNAL_CONFIGURATION);
        assertCode(() -> snapshot(List.of(question("Q04", singlePointOfFailureText(), MissingDataPolicy.IGNORE)),
                        List.of(booleanSignal("SINGLE_POINT_OF_FAILURE", "Q04"), numberSignal("TEXT_NUMBER", "Q04"))),
                ScoringErrorCode.INVALID_SIGNAL_CONFIGURATION);
        assertCode(() -> snapshot(List.of(question("Q01", scale15(ScaleDirection.POSITIVE), MissingDataPolicy.IGNORE),
                        question("Q04", singlePointOfFailureText(), MissingDataPolicy.IGNORE)),
                        List.of(booleanSignal("SINGLE_POINT_OF_FAILURE", "Q01"))),
                ScoringErrorCode.INVALID_SIGNAL_CONFIGURATION);
    }

    @Test
    void numberSignalNeedsExactlyOneSource() {
        assertCode(() -> new SignalDefinition("S", SignalDataType.NUMBER, Set.of("A", "B"), null, "1"),
                ScoringErrorCode.INVALID_SIGNAL_CONFIGURATION);
        assertCode(() -> new SignalDefinition("S", SignalDataType.BOOLEAN, Set.of(), null, "1"),
                ScoringErrorCode.INVALID_SIGNAL_CONFIGURATION);
    }

    @Test
    void yesNoQuestionIsAValidBooleanQuestion() {
        assertThat(question("Q03", yesIsGood(), MissingDataPolicy.IGNORE).type()).isEqualTo(QuestionType.BOOLEAN);
    }
}
