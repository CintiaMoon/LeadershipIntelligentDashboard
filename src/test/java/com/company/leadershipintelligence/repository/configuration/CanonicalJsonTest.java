package com.company.leadershipintelligence.repository.configuration;

import org.junit.jupiter.api.Test;

import static com.company.leadershipintelligence.repository.configuration.ConfigurationSamples.json;
import static org.assertj.core.api.Assertions.assertThat;

class CanonicalJsonTest {

    @Test
    void keyOrderAndWhitespaceDoNotChangeTheCanonicalFormOrHash() {
        var a = json("{ \"b\": 1, \"a\": { \"y\": true, \"x\": [1, 2] } }");
        var b = json("{\"a\":{\"x\":[1,2],\"y\":true},\"b\":1}");
        assertThat(CanonicalJson.canonicalize(a)).isEqualTo("{\"a\":{\"x\":[1,2],\"y\":true},\"b\":1}").isEqualTo(CanonicalJson.canonicalize(b));
        assertThat(CanonicalJson.hash(a)).isEqualTo(CanonicalJson.hash(b));
    }

    @Test
    void equalNumbersInDifferentFormsHashTheSame() {
        assertThat(CanonicalJson.hash(json("{\"v\":1}"))).isEqualTo(CanonicalJson.hash(json("{\"v\":1.0}")))
                .isEqualTo(CanonicalJson.hash(json("{\"v\":1.000}")));
        assertThat(CanonicalJson.canonicalize(json("{\"v\":0.70}"))).isEqualTo("{\"v\":0.7}");
        assertThat(CanonicalJson.canonicalize(json("{\"v\":0.0}"))).isEqualTo("{\"v\":0}");
        assertThat(CanonicalJson.canonicalize(json("{\"v\":100}"))).isEqualTo("{\"v\":100}");
    }

    @Test
    void anyContentChangeChangesTheHash() {
        assertThat(CanonicalJson.hash(json("{\"v\":1}"))).isNotEqualTo(CanonicalJson.hash(json("{\"v\":2}")));
        assertThat(CanonicalJson.hash(json("{\"v\":[1,2]}"))).isNotEqualTo(CanonicalJson.hash(json("{\"v\":[2,1]}")));
        assertThat(CanonicalJson.hash(json("{\"v\":\"a\"}"))).isNotEqualTo(CanonicalJson.hash(json("{\"w\":\"a\"}")));
        assertThat(CanonicalJson.hash(json("{\"v\":null}"))).isNotEqualTo(CanonicalJson.hash(json("{}")));
    }

    @Test
    void hashHasTheDocumentedFormatAndIsStable() {
        String hash = CanonicalJson.hash(json("{\"a\":1}"));
        assertThat(hash).matches("sha256:[0-9a-f]{64}");
        assertThat(hash).isEqualTo(CanonicalJson.hash(json("{\"a\":1}")));
        // sha-256 of the text {"a":1}
        assertThat(hash).isEqualTo(CanonicalJson.sha256("{\"a\":1}"));
    }

    @Test
    void stringsAreEscapedAndSampleHashIsReproducible() {
        assertThat(CanonicalJson.canonicalize(json("{\"s\":\"a\\\"b\\n\"}"))).isEqualTo("{\"s\":\"a\\\"b\\n\"}");
        assertThat(CanonicalJson.hash(ConfigurationSamples.sampleDocument())).isEqualTo(CanonicalJson.hash(json(ConfigurationSamples.sampleText())));
    }
}
