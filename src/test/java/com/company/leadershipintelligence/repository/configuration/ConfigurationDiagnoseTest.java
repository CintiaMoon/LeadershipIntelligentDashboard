package com.company.leadershipintelligence.repository.configuration;

import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A document with several problems reports each of them (specv3.md Section 30.6, decision D54); parsing still stops at the first. */
class ConfigurationDiagnoseTest {

    private static final ConfigurationDocumentParser PARSER = ConfigurationSamples.PARSER;
    private static final ConfigurationVersion VERSION = new ConfigurationVersion("diagnose");

    private static ObjectNode sample() {
        return ConfigurationSamples.sampleDocument();
    }

    private static List<String> diagnose(JsonNode document) {
        return PARSER.diagnose(document, VERSION);
    }

    @Test
    void aValidDocumentHasNoProblems() {
        assertThat(diagnose(sample())).isEmpty();
    }

    @Test
    void everyBrokenQuestionIsReportedWithItsPath() {
        ObjectNode doc = sample();
        ArrayNode questions = (ArrayNode) doc.get("questions");
        ((ObjectNode) questions.get(0)).remove("normalization");
        ((ObjectNode) questions.get(1)).put("type", "NOT_A_TYPE");
        List<String> problems = diagnose(doc);
        assertThat(problems).hasSize(2);
        assertThat(problems.get(0)).contains("$.questions[0].normalization");
        assertThat(problems.get(1)).contains("NOT_A_TYPE");
    }

    @Test
    void problemsInDifferentSectionsAreAllReported() {
        ObjectNode doc = sample();
        ((ObjectNode) doc.get("questions").get(0)).remove("missingPolicy");
        ((ObjectNode) doc.get("signals").get(0)).put("dataType", "WRONG");
        ((ObjectNode) doc.get("patterns").get(0)).put("severity", "SEVERE");
        ((ObjectNode) doc.get("penalties").get(0)).remove("target");
        ((ObjectNode) doc.get("overall")).remove("weights");
        doc.put("surprise", 1);
        List<String> problems = diagnose(doc);
        assertThat(problems).hasSize(6);
        assertThat(problems).anyMatch(p -> p.contains("questions[0]") && p.contains("missingPolicy"));
        assertThat(problems).anyMatch(p -> p.contains("signals[0]"));
        assertThat(problems).anyMatch(p -> p.contains("SEVERE"));
        assertThat(problems).anyMatch(p -> p.contains("penalties[0]") && p.contains("target"));
        assertThat(problems).anyMatch(p -> p.contains("weights"));
        assertThat(problems).anyMatch(p -> p.contains("surprise"));
    }

    @Test
    void aMissingSectionIsReportedAndTheRestIsStillChecked() {
        ObjectNode doc = sample();
        doc.remove("dimensions");
        ((ObjectNode) doc.get("questions").get(2)).put("type", "NOPE");
        List<String> problems = diagnose(doc);
        assertThat(problems).hasSize(2);
        assertThat(problems).anyMatch(p -> p.contains("dimensions"));
        assertThat(problems).anyMatch(p -> p.contains("NOPE"));
    }

    @Test
    void duplicatesAreReported() {
        ObjectNode doc = sample();
        ArrayNode questions = (ArrayNode) doc.get("questions");
        questions.add(questions.get(0).deepCopy());
        ArrayNode recommendations = (ArrayNode) doc.get("recommendations");
        recommendations.add(recommendations.get(0).deepCopy());
        assertThat(diagnose(doc)).containsExactly("duplicate question Q01", "duplicate recommendation " + recommendations.get(0).get("key").asText());
    }

    @Test
    void referencesBetweenSectionsAreCheckedOnlyWhenNothingElseIsWrong() {
        ObjectNode doc = sample();
        ((ObjectNode) doc.get("penalties").get(0).get("trigger")).put("pattern", "NO-SUCH-PATTERN");
        List<String> onlyReference = diagnose(doc);
        assertThat(onlyReference).hasSize(1);
        assertThat(onlyReference.get(0)).contains("NO-SUCH-PATTERN");

        // with a section problem as well, the reference error is not reported on top of it (it could be a consequence)
        ((ObjectNode) doc.get("questions").get(0)).remove("normalization");
        List<String> both = diagnose(doc);
        assertThat(both).hasSize(1);
        assertThat(both.get(0)).contains("normalization");
    }

    @Test
    void aDocumentThatIsNotAnObjectIsOneProblem() {
        assertThat(diagnose(ConfigurationSamples.json("[1, 2]"))).hasSize(1).first().asString().contains("must be an object");
    }

    @Test
    void theNumberOfProblemsReportedIsLimited() {
        ObjectNode doc = sample();
        ArrayNode questions = (ArrayNode) doc.get("questions");
        for (int i = 0; i < 80; i++) {
            ObjectNode broken = JsonNodeFactoryHolder.object();
            broken.put("id", "BAD" + i);
            questions.add(broken);
        }
        assertThat(diagnose(doc)).hasSize(50);
    }

    @Test
    void parsingStillStopsAtTheFirstProblem() {
        ObjectNode doc = sample();
        ((ObjectNode) doc.get("questions").get(0)).remove("normalization");
        ((ObjectNode) doc.get("signals").get(0)).put("dataType", "WRONG");
        assertThatThrownBy(() -> PARSER.parse(doc, VERSION)).isInstanceOf(ScoringException.class).hasMessageContaining("questions[0]");
    }

    private static final class JsonNodeFactoryHolder {
        static ObjectNode object() {
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        }
    }
}
