package com.company.leadershipintelligence.domain.service.signals;

import com.company.leadershipintelligence.domain.enums.MissingDataPolicy;
import com.company.leadershipintelligence.domain.enums.ScaleDirection;
import com.company.leadershipintelligence.domain.enums.SignalDataType;
import com.company.leadershipintelligence.domain.model.DerivedSignal;
import com.company.leadershipintelligence.domain.model.NormalizedInput;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.company.leadershipintelligence.domain.Fixtures.booleanSignal;
import static com.company.leadershipintelligence.domain.Fixtures.normalizationService;
import static com.company.leadershipintelligence.domain.Fixtures.nv;
import static com.company.leadershipintelligence.domain.Fixtures.numberSignal;
import static com.company.leadershipintelligence.domain.Fixtures.question;
import static com.company.leadershipintelligence.domain.Fixtures.scale15;
import static com.company.leadershipintelligence.domain.Fixtures.singlePointOfFailureText;
import static com.company.leadershipintelligence.domain.Fixtures.snapshot;
import static org.assertj.core.api.Assertions.assertThat;

class SignalDerivationServiceTest {

    private final SignalDerivationService service = new SignalDerivationService();

    private final ScoringConfigurationSnapshot config = snapshot(List.of(
            question("Q01", scale15(ScaleDirection.POSITIVE), MissingDataPolicy.IGNORE),
            question("Q04", singlePointOfFailureText(), MissingDataPolicy.IGNORE)),
            List.of(numberSignal("TEAM_SUSTAINABILITY", "Q01"), booleanSignal("SINGLE_POINT_OF_FAILURE", "Q04")));

    private List<DerivedSignal> derive(ScoringInput... inputs) {
        List<NormalizedInput> normalized = normalizationService().normalize(List.of(inputs), config);
        return service.derive(normalized, config);
    }

    @Test
    void numberSignalCopiesSourceValueWithProvenance() {
        List<DerivedSignal> signals = derive(new ScoringInput("Q01", 2), new ScoringInput("Q04", null));
        assertThat(signals).hasSize(1);
        DerivedSignal signal = signals.get(0);
        assertThat(signal.signalId()).isEqualTo("TEAM_SUSTAINABILITY");
        assertThat(signal.dataType()).isEqualTo(SignalDataType.NUMBER);
        assertThat(signal.numericValue()).isEqualTo(nv("25"));
        assertThat(signal.sourceQuestionIds()).containsExactly("Q01");
        assertThat(signal.derivationVersion()).isEqualTo("1");
    }

    @Test
    void missingNumericSourceProducesNoSignalNotZero() {
        assertThat(derive(new ScoringInput("Q01", null), new ScoringInput("Q04", null))).isEmpty();
    }

    @Test
    void textFlagRaisesTrueWithSourceQuestion() {
        List<DerivedSignal> signals = derive(new ScoringInput("Q01", null), new ScoringInput("Q04", "Only one key person knows it"));
        assertThat(signals).hasSize(1);
        assertThat(signals.get(0).flag()).isTrue();
        assertThat(signals.get(0).sourceQuestionIds()).containsExactly("Q04");
    }

    @Test
    void answeredTextWithoutAMatchIsFalse() {
        List<DerivedSignal> signals = derive(new ScoringInput("Q01", null), new ScoringInput("Q04", "Work is well shared"));
        assertThat(signals).hasSize(1);
        assertThat(signals.get(0).flag()).isFalse();
    }

    @Test
    void missingTextIsNotTheAbsenceOfRisk() {
        assertThat(derive(new ScoringInput("Q01", null), new ScoringInput("Q04", ""))).isEmpty();
    }

    @Test
    void outputIsInSignalIdOrderAndRepeatable() {
        List<DerivedSignal> first = derive(new ScoringInput("Q04", "single point"), new ScoringInput("Q01", 5));
        assertThat(first).extracting(DerivedSignal::signalId).containsExactly("SINGLE_POINT_OF_FAILURE", "TEAM_SUSTAINABILITY");
        assertThat(derive(new ScoringInput("Q04", "single point"), new ScoringInput("Q01", 5))).isEqualTo(first);
    }
}
