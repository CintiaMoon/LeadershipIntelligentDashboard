package com.company.leadershipintelligence.repository.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static com.company.leadershipintelligence.repository.configuration.ConfigurationSamples.json;
import static org.assertj.core.api.Assertions.assertThat;

/** The replacement marker {"$replace": X} (specv3.md Section 23.3, decision D47). */
class ConfigurationMergerReplaceTest {

    private final ConfigurationMerger merger = new ConfigurationMerger();

    private JsonNode merge(String... layers) {
        return merger.merge(java.util.Arrays.stream(layers).map(ConfigurationSamples::json).toList());
    }

    @Test
    void anObjectIsReplacedInsteadOfMergedSoStaleKeysOfLowerLayersDisappear() {
        JsonNode merged = merge("{\"presentation\":{\"questions\":{\"OLD\":{\"name\":\"old\"}},\"dimensions\":{\"D\":1}}}",
                "{\"presentation\":{\"$replace\":{\"questions\":{\"NEW\":{\"name\":\"new\"}}}}}");
        assertThat(merged).isEqualTo(json("{\"presentation\":{\"questions\":{\"NEW\":{\"name\":\"new\"}}}}"));
    }

    @Test
    void withoutTheMarkerTheSameLayerWouldHaveKeptTheStaleKeys() {
        JsonNode merged = merge("{\"presentation\":{\"questions\":{\"OLD\":1}}}", "{\"presentation\":{\"questions\":{\"NEW\":2}}}");
        assertThat(merged).isEqualTo(json("{\"presentation\":{\"questions\":{\"OLD\":1,\"NEW\":2}}}"));
    }

    @Test
    void itSetsTheFieldWhenNoLowerLayerHasIt() {
        assertThat(merge("{\"a\":1}", "{\"b\":{\"$replace\":{\"x\":1}}}")).isEqualTo(json("{\"a\":1,\"b\":{\"x\":1}}"));
    }

    @Test
    void itReplacesWhateverTypeWasThere() {
        assertThat(merge("{\"a\":[1,2]}", "{\"a\":{\"$replace\":{\"k\":true}}}")).isEqualTo(json("{\"a\":{\"k\":true}}"));
        assertThat(merge("{\"a\":{\"k\":true}}", "{\"a\":{\"$replace\":[1,2]}}")).isEqualTo(json("{\"a\":[1,2]}"));
        assertThat(merge("{\"a\":{\"k\":true}}", "{\"a\":{\"$replace\":7}}")).isEqualTo(json("{\"a\":7}"));
    }

    @Test
    void aReplacementMayBeFollowedByAHigherLayerThatMergesIntoIt() {
        JsonNode merged = merge("{\"o\":{\"x\":1,\"y\":2}}", "{\"o\":{\"$replace\":{\"z\":3}}}", "{\"o\":{\"w\":4}}");
        assertThat(merged).isEqualTo(json("{\"o\":{\"z\":3,\"w\":4}}"));
    }

    @Test
    void aWholeLayerCanReplaceEverythingBelow() {
        assertThat(merge("{\"a\":1,\"b\":2}", "{\"$replace\":{\"c\":3}}")).isEqualTo(json("{\"c\":3}"));
        assertThat(merge("{\"$replace\":{\"c\":3}}")).isEqualTo(json("{\"c\":3}"));
        assertThat(merge("{\"a\":1}", "{\"$replace\":{\"c\":3}}", "{\"d\":4}")).isEqualTo(json("{\"c\":3,\"d\":4}"));
    }

    @Test
    void theMarkerIsOnlyAMarkerWhenItIsTheOnlyKey() {
        // with another key beside it, it is an ordinary field the parser will reject later
        JsonNode merged = merge("{\"o\":{\"x\":1}}", "{\"o\":{\"$replace\":{\"z\":3},\"y\":2}}");
        assertThat(merged.get("o").has("$replace")).isTrue();
        assertThat(merged.get("o").get("x").asInt()).isEqualTo(1);
    }

    @Test
    void removeAndReplaceAreIndependent() {
        JsonNode merged = merge("{\"a\":1,\"b\":{\"x\":1}}", "{\"a\":{\"$remove\":true},\"b\":{\"$replace\":{\"y\":2}}}");
        assertThat(merged).isEqualTo(json("{\"b\":{\"y\":2}}"));
    }

    @Test
    void inputsAreNeverModified() {
        JsonNode lower = json("{\"o\":{\"x\":1}}");
        JsonNode higher = json("{\"o\":{\"$replace\":{\"z\":3}}}");
        JsonNode lowerCopy = lower.deepCopy();
        JsonNode higherCopy = higher.deepCopy();
        JsonNode merged = merger.merge(java.util.List.of(lower, higher));
        ((com.fasterxml.jackson.databind.node.ObjectNode) merged.get("o")).put("changed", true);
        assertThat(lower).isEqualTo(lowerCopy);
        assertThat(higher).isEqualTo(higherCopy);
    }

    @Test
    void aReplacedDocumentStillParsesAsAnOrdinaryConfiguration() {
        // the effective result carries no marker: it is the same document a layer could have written in full
        JsonNode sample = ConfigurationSamples.sampleDocument();
        JsonNode merged = merger.merge(java.util.List.of(sample, json("{\"presentation\":{\"$replace\":{}}}")));
        assertThat(merged.has("presentation")).isTrue();
        assertThat(merged.get("presentation").size()).isZero();
        assertThat(merged.toString()).doesNotContain("$replace");
    }
}
