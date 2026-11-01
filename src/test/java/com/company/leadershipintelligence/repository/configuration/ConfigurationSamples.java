package com.company.leadershipintelligence.repository.configuration;

import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads the illustrative sample configuration (decision D29). It is test data, never a platform default. */
public final class ConfigurationSamples {

    public static final ConfigurationDocumentParser PARSER = new ConfigurationDocumentParser();

    private ConfigurationSamples() {
    }

    public static String sampleText() {
        try (InputStream in = ConfigurationSamples.class.getResourceAsStream("/sample-config/sample-platform-default.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static ObjectNode sampleDocument() {
        return (ObjectNode) PARSER.read(sampleText());
    }

    public static ScoringConfigurationSnapshot sampleSnapshot() {
        return PARSER.parse(sampleDocument(), new ConfigurationVersion("sample-1"));
    }

    public static JsonNode json(String text) {
        return PARSER.read(text);
    }
}
