package com.company.leadershipintelligence.repository.configuration;

import com.company.leadershipintelligence.domain.enums.AggregationMethod;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The optional {@code responses} block and the per-question {@code aggregation} (decision D45). */
class ResponseSettingsParserTest {

    private static ScoringConfigurationSnapshot parse(ObjectNode doc) {
        return ConfigurationSamples.PARSER.parse(doc, new ConfigurationVersion("v"));
    }

    private static ObjectNode withResponses(String json) {
        ObjectNode doc = ConfigurationSamples.sampleDocument();
        doc.set("responses", ConfigurationSamples.json(json));
        return doc;
    }

    @Test
    void withoutABlockTheUnvalidatedDefaultsApply() {
        ObjectNode doc = ConfigurationSamples.sampleDocument();
        doc.remove("responses");
        var settings = parse(doc).responses();
        assertThat(settings.defaultMethod()).isEqualTo(AggregationMethod.MEDIAN);
        assertThat(settings.minResponses()).isEqualTo(5);
        assertThat(settings.signalShare()).isEqualByComparingTo("0.5");
        assertThat(settings.fullConfidenceResponses()).isEqualTo(10);
    }

    @Test
    void theBlockOverridesOnlyWhatItNames() {
        var settings = parse(withResponses("{\"minResponses\":7,\"defaultMethod\":\"MEAN\"}")).responses();
        assertThat(settings.minResponses()).isEqualTo(7);
        assertThat(settings.defaultMethod()).isEqualTo(AggregationMethod.MEAN);
        assertThat(settings.fullConfidenceResponses()).isEqualTo(10);
        assertThat(settings.signalShare()).isEqualByComparingTo("0.5");
    }

    @Test
    void aQuestionCanChooseItsOwnMethod() {
        ObjectNode doc = ConfigurationSamples.sampleDocument();
        ObjectNode first = (ObjectNode) doc.withArray("questions").get(0);
        first.put("aggregation", "MEAN");
        var question = parse(doc).questionsInOrder().stream()
                .filter(q -> q.questionId().equals(first.get("id").asText())).findFirst().orElseThrow();
        assertThat(question.aggregation()).isEqualTo(AggregationMethod.MEAN);
    }

    @Test
    void invalidValuesAreRejected() {
        assertThatThrownBy(() -> parse(withResponses("{\"minResponses\":0}")))
                .isInstanceOfSatisfying(ScoringException.class, e -> assertThat(e.code()).isEqualTo(ScoringErrorCode.INVALID_RESPONSE_CONFIGURATION));
        assertThatThrownBy(() -> parse(withResponses("{\"signalShare\":1.5}"))).isInstanceOf(ScoringException.class);
        assertThatThrownBy(() -> parse(withResponses("{\"minResponses\":8,\"fullConfidenceResponses\":5}"))).isInstanceOf(ScoringException.class);
        assertThatThrownBy(() -> parse(withResponses("{\"defaultMethod\":\"MODE\"}"))).isInstanceOf(ScoringException.class);
        assertThatThrownBy(() -> parse(withResponses("{\"surprise\":1}"))).isInstanceOf(ScoringException.class);
    }
}
