package com.company.leadershipintelligence.repository.configuration;

import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PresentationCatalogParserTest {

    private static ObjectNode with(String presentation) {
        ObjectNode doc = ConfigurationSamples.sampleDocument();
        doc.set("presentation", ConfigurationSamples.json(presentation));
        return doc;
    }

    private static void parse(ObjectNode doc) {
        ConfigurationSamples.PARSER.parse(doc, new ConfigurationVersion("v"));
    }

    @Test
    void thePresentationIsOptionalAndSectionsAreOptional() {
        ObjectNode doc = ConfigurationSamples.sampleDocument();
        doc.remove("presentation");
        assertThat(ConfigurationSamples.PARSER.parse(doc, new ConfigurationVersion("v")).presentation().patterns()).isEmpty();
        var snapshot = ConfigurationSamples.PARSER.parse(with("{\"dimensions\":{\"TEAM_HEALTH\":{\"name\":\"T\",\"description\":\"d\"}}}"),
                new ConfigurationVersion("v"));
        assertThat(snapshot.presentation().dimensions()).containsOnlyKeys("TEAM_HEALTH");
        assertThat(snapshot.presentation().dimensions().get("TEAM_HEALTH").explanation()).isNull();
    }

    @Test
    void referencesToUnknownItemsAreRejected() {
        for (String section : new String[]{"patterns", "penalties", "dimensions", "signals", "questions"}) {
            assertThatThrownBy(() -> parse(with("{\"" + section + "\":{\"UNKNOWN_ID\":{\"name\":\"x\",\"description\":\"y\"}}}")))
                    .as(section).isInstanceOf(RuntimeException.class).hasMessageContaining("unknown");
        }
    }

    @Test
    void invalidEntriesAreRejected() {
        assertThatThrownBy(() -> parse(with("{\"patterns\":{\"TH-001\":{\"name\":\"x\"}}}"))).isInstanceOf(ScoringException.class);
        assertThatThrownBy(() -> parse(with("{\"patterns\":{\"TH-001\":{\"name\":\" \",\"description\":\"y\"}}}"))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> parse(with("{\"patterns\":{\"TH-001\":{\"name\":\"x\",\"description\":\"y\",\"explanation\":\" \"}}}")))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> parse(with("{\"patterns\":{\"TH-001\":{\"name\":\"x\",\"description\":\"y\",\"colour\":\"red\"}}}")))
                .isInstanceOf(ScoringException.class);
        assertThatThrownBy(() -> parse(with("{\"colours\":{}}"))).isInstanceOf(ScoringException.class);
    }

    @Test
    void thePresentationBelongsToTheConfigurationHash() {
        ObjectNode a = ConfigurationSamples.sampleDocument();
        ObjectNode b = ConfigurationSamples.sampleDocument();
        ((ObjectNode) b.get("presentation").get("dimensions").get("TEAM_HEALTH")).put("name", "Renamed");
        assertThat(CanonicalJson.hash(a)).isNotEqualTo(CanonicalJson.hash(b));
    }
}
