package com.company.leadershipintelligence.repository.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.company.leadershipintelligence.repository.configuration.ConfigurationSamples.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationMergerTest {

    private final ConfigurationMerger merger = new ConfigurationMerger();

    private JsonNode merge(String... layers) {
        return merger.merge(java.util.Arrays.stream(layers).map(ConfigurationSamples::json).toList());
    }

    @Test
    void scalarsInTheHigherLayerReplaceLowerOnes() {
        assertThat(merge("{\"a\":1,\"b\":2}", "{\"b\":3}")).isEqualTo(json("{\"a\":1,\"b\":3}"));
    }

    @Test
    void objectsMergeRecursively() {
        assertThat(merge("{\"o\":{\"x\":1,\"y\":{\"p\":1,\"q\":2}}}", "{\"o\":{\"y\":{\"q\":9},\"z\":3}}"))
                .isEqualTo(json("{\"o\":{\"x\":1,\"y\":{\"p\":1,\"q\":9},\"z\":3}}"));
    }

    @Test
    void arraysAreReplacedAsAWholeNeverMerged() {
        assertThat(merge("{\"list\":[1,2,3]}", "{\"list\":[9]}")).isEqualTo(json("{\"list\":[9]}"));
        assertThat(merge("{\"list\":[{\"id\":\"A\",\"v\":1},{\"id\":\"B\",\"v\":2}]}", "{\"list\":[{\"id\":\"A\",\"v\":5}]}"))
                .isEqualTo(json("{\"list\":[{\"id\":\"A\",\"v\":5}]}"));
    }

    @Test
    void removeMarkerDeletesTheField() {
        assertThat(merge("{\"a\":1,\"b\":{\"c\":2}}", "{\"a\":{\"$remove\":true},\"b\":{\"c\":{\"$remove\":true}}}"))
                .isEqualTo(json("{\"b\":{}}"));
        // a marker for a field that does not exist is harmless
        assertThat(merge("{\"a\":1}", "{\"zzz\":{\"$remove\":true}}")).isEqualTo(json("{\"a\":1}"));
    }

    @Test
    void highestLayerWinsAcrossFourLayers() {
        JsonNode merged = merge("{\"k\":\"platform\",\"p\":1}", "{\"k\":\"template\",\"t\":1}", "{\"k\":\"organization\"}", "{\"k\":\"tenant\",\"n\":1}");
        assertThat(merged).isEqualTo(json("{\"k\":\"tenant\",\"p\":1,\"t\":1,\"n\":1}"));
    }

    @Test
    void typeChangesAreAllowedByTheHigherLayer() {
        assertThat(merge("{\"a\":{\"x\":1}}", "{\"a\":5}")).isEqualTo(json("{\"a\":5}"));
        assertThat(merge("{\"a\":5}", "{\"a\":{\"x\":1}}")).isEqualTo(json("{\"a\":{\"x\":1}}"));
    }

    @Test
    void inputsAreNeverModified() {
        JsonNode low = json("{\"o\":{\"x\":1}}");
        JsonNode high = json("{\"o\":{\"y\":2}}");
        JsonNode lowBefore = low.deepCopy();
        JsonNode highBefore = high.deepCopy();
        merger.merge(List.of(low, high));
        assertThat(low).isEqualTo(lowBefore);
        assertThat(high).isEqualTo(highBefore);
    }

    @Test
    void aSingleLayerIsReturnedAsACopyAndNoLayersIsAnError() {
        JsonNode only = json("{\"a\":1}");
        assertThat(merger.merge(List.of(only))).isEqualTo(only).isNotSameAs(only);
        assertThatThrownBy(() -> merger.merge(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
