package com.company.leadershipintelligence.domain;

import com.company.leadershipintelligence.domain.enums.MissingDataPolicy;
import com.company.leadershipintelligence.domain.enums.OutOfRangePolicy;
import com.company.leadershipintelligence.domain.enums.ScaleDirection;
import com.company.leadershipintelligence.domain.enums.SignalDataType;
import com.company.leadershipintelligence.domain.enums.TextMatchMode;
import com.company.leadershipintelligence.domain.enums.UnknownOptionPolicy;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.rule.BooleanConfig;
import com.company.leadershipintelligence.domain.rule.MultipleChoiceConfig;
import com.company.leadershipintelligence.domain.rule.NormalizationConfig;
import com.company.leadershipintelligence.domain.rule.ScaleConfig;
import com.company.leadershipintelligence.domain.rule.SignalDefinition;
import com.company.leadershipintelligence.domain.rule.TextConfig;
import com.company.leadershipintelligence.domain.rule.TextSignalRule;
import com.company.leadershipintelligence.domain.service.normalization.BooleanNormalizationStrategy;
import com.company.leadershipintelligence.domain.service.normalization.MultipleChoiceNormalizationStrategy;
import com.company.leadershipintelligence.domain.service.normalization.NormalizationService;
import com.company.leadershipintelligence.domain.service.normalization.NumberNormalizationStrategy;
import com.company.leadershipintelligence.domain.service.normalization.ScaleNormalizationStrategy;
import com.company.leadershipintelligence.domain.service.normalization.TextNormalizationStrategy;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.company.leadershipintelligence.domain.valueobject.NormalizedValue;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Shared test data. The sample content follows the specv3.md examples and is illustrative only. */
public final class Fixtures {

    private Fixtures() {
    }

    public static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    public static NormalizedValue nv(String value) {
        return new NormalizedValue(new BigDecimal(value));
    }

    public static ScaleConfig scale15(ScaleDirection direction) {
        return new ScaleConfig(d("1"), d("5"), direction, OutOfRangePolicy.REJECT, true);
    }

    public static MultipleChoiceConfig frequency(UnknownOptionPolicy unknown) {
        Map<String, NormalizedValue> values = new LinkedHashMap<>();
        values.put("NEVER", nv("0"));
        values.put("RARELY", nv("25"));
        values.put("SOMETIMES", nv("50"));
        values.put("OFTEN", nv("75"));
        values.put("ALWAYS", nv("100"));
        return new MultipleChoiceConfig(values, unknown);
    }

    public static BooleanConfig yesIsGood() {
        return new BooleanConfig(nv("100"), nv("0"));
    }

    public static TextConfig singlePointOfFailureText() {
        return new TextConfig("1", List.of(new TextSignalRule("SINGLE_POINT_OF_FAILURE", TextMatchMode.KEYWORD_ANY,
                List.of("only", "single", "one person", "key person"))));
    }

    public static QuestionDefinition question(String id, NormalizationConfig config, MissingDataPolicy policy) {
        return new QuestionDefinition(id, config.questionType(), config, policy, null, null);
    }

    public static ScoringConfigurationSnapshot snapshot(List<QuestionDefinition> questions, List<SignalDefinition> signals) {
        Map<String, QuestionDefinition> q = new LinkedHashMap<>();
        questions.forEach(x -> q.put(x.questionId(), x));
        Map<String, SignalDefinition> s = new LinkedHashMap<>();
        signals.forEach(x -> s.put(x.signalId(), x));
        return new ScoringConfigurationSnapshot(new ConfigurationVersion("test-1"), q, s);
    }

    public static SignalDefinition booleanSignal(String id, String... sources) {
        return new SignalDefinition(id, SignalDataType.BOOLEAN, Set.of(sources), null, "1");
    }

    public static SignalDefinition numberSignal(String id, String source) {
        return new SignalDefinition(id, SignalDataType.NUMBER, Set.of(source), null, "1");
    }

    public static NormalizationService normalizationService() {
        return new NormalizationService(List.of(
                new ScaleNormalizationStrategy(), new MultipleChoiceNormalizationStrategy(),
                new BooleanNormalizationStrategy(), new NumberNormalizationStrategy(),
                new TextNormalizationStrategy()));
    }

    public static void assertCode(ThrowingCallable call, ScoringErrorCode expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ScoringException.class,
                e -> assertThat(e.code()).isEqualTo(expected));
    }
}
