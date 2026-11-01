package com.company.leadershipintelligence.repository.configuration;

import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.enums.UnknownOptionPolicy;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.rule.MultiSelectConfig;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A MULTI_SELECT question in a configuration document (specv3.md Section 4.2.1). */
class MultiSelectParserTest {

    private static ObjectNode withQuestion(String normalization) {
        ObjectNode doc = ConfigurationSamples.sampleDocument();
        ObjectNode question = doc.withArray("questions").addObject();
        question.put("id", "QM").put("type", "MULTI_SELECT").put("missingPolicy", "IGNORE");
        question.set("normalization", ConfigurationSamples.json(normalization));
        return doc;
    }

    private static QuestionDefinition parse(ObjectNode doc) {
        return ConfigurationSamples.PARSER.parse(doc, new ConfigurationVersion("v")).question("QM").orElseThrow();
    }

    @Test
    void readsTheOptionsAndTheUnknownOptionPolicy() {
        QuestionDefinition question = parse(withQuestion("{\"values\":{\"A\":100,\"B\":40.5},\"unknownOption\":\"TREAT_AS_MISSING\"}"));
        assertThat(question.type()).isEqualTo(QuestionType.MULTI_SELECT);
        MultiSelectConfig config = (MultiSelectConfig) question.normalization();
        assertThat(config.values().keySet()).containsExactly("A", "B");
        assertThat(config.values().get("B").value()).isEqualByComparingTo("40.5");
        assertThat(config.unknownOptionPolicy()).isEqualTo(UnknownOptionPolicy.TREAT_AS_MISSING);
    }

    @Test
    void rejectsAnOptionValueOutsideZeroToOneHundredAndMissingParts() {
        assertThatThrownBy(() -> parse(withQuestion("{\"values\":{\"A\":101},\"unknownOption\":\"REJECT\"}"))).isInstanceOf(ScoringException.class);
        assertThatThrownBy(() -> parse(withQuestion("{\"values\":{},\"unknownOption\":\"REJECT\"}"))).isInstanceOf(ScoringException.class);
        assertThatThrownBy(() -> parse(withQuestion("{\"values\":{\"A\":1}}"))).isInstanceOf(ScoringException.class);
        assertThatThrownBy(() -> parse(withQuestion("{\"values\":{\"A\":1},\"unknownOption\":\"REJECT\",\"extra\":1}"))).isInstanceOf(ScoringException.class);
    }

    @Test
    void multiSelectAndMultipleChoiceStayDistinctTypes() {
        ObjectNode doc = ConfigurationSamples.sampleDocument();
        ObjectNode question = doc.withArray("questions").addObject();
        question.put("id", "QM").put("type", "MULTIPLE_CHOICE").put("missingPolicy", "IGNORE");
        question.set("normalization", ConfigurationSamples.json("{\"values\":{\"A\":1},\"unknownOption\":\"REJECT\"}"));
        assertThat(parse(doc).type()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
    }
}
