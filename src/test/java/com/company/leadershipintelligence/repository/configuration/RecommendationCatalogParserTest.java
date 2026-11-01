package com.company.leadershipintelligence.repository.configuration;

import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationCatalogParserTest {

    private static ObjectNode withCatalog(String json) {
        ObjectNode doc = ConfigurationSamples.sampleDocument();
        doc.set("recommendations", ConfigurationSamples.json(json));
        return doc;
    }

    private static void parse(ObjectNode doc) {
        ConfigurationSamples.PARSER.parse(doc, new ConfigurationVersion("v"));
    }

    @Test
    void theCatalogIsOptionalAndSortedByKey() {
        ObjectNode doc = ConfigurationSamples.sampleDocument();
        doc.remove("recommendations");
        assertThat(ConfigurationSamples.PARSER.parse(doc, new ConfigurationVersion("v")).recommendations()).isEmpty();
        var snapshot = ConfigurationSamples.PARSER.parse(withCatalog(
                "[{\"key\":\"B\",\"title\":\"b\",\"description\":\"d\"},{\"key\":\"A\",\"title\":\"a\",\"description\":\"d\",\"priority\":5}]"),
                new ConfigurationVersion("v"));
        assertThat(snapshot.recommendations().keySet()).containsExactly("A", "B");
        assertThat(snapshot.recommendations().get("A").priority()).isEqualTo(5);
        assertThat(snapshot.recommendations().get("B").priority()).isEqualTo(100);
    }

    @Test
    void invalidEntriesAreRejectedAtValidationTime() {
        assertThatThrownBy(() -> parse(withCatalog("[{\"key\":\"A\",\"title\":\"a\",\"description\":\"d\"},{\"key\":\"A\",\"title\":\"x\",\"description\":\"y\"}]")))
                .isInstanceOf(ScoringException.class).hasMessageContaining("duplicate recommendation A");
        assertThatThrownBy(() -> parse(withCatalog("[{\"key\":\"A\",\"title\":\"a\"}]"))).isInstanceOf(ScoringException.class);
        assertThatThrownBy(() -> parse(withCatalog("[{\"key\":\"A\",\"title\":\"a\",\"description\":\"d\",\"priority\":-1}]")))
                .isInstanceOf(ScoringException.class);
        assertThatThrownBy(() -> parse(withCatalog("[{\"key\":\"A\",\"title\":\"a\",\"description\":\"d\",\"text\":\"extra\"}]")))
                .isInstanceOf(ScoringException.class);
        assertThatThrownBy(() -> parse(withCatalog("[{\"key\":\" \",\"title\":\"a\",\"description\":\"d\"}]"))).isInstanceOf(ScoringException.class);
    }

    @SuppressWarnings("unused")
    private static ArrayNode unused() {
        return null;
    }
}
