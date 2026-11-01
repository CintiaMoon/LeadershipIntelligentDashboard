package com.company.leadershipintelligence.service.questionnaire;

import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.company.leadershipintelligence.repository.configuration.ConfigurationDocumentParser;
import com.company.leadershipintelligence.repository.configuration.ConfigurationMerger;
import com.company.leadershipintelligence.repository.configuration.ConfigurationSamples;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Copying the platform default into the builder and publishing the copy must give a configuration the engine accepts. */
class PlatformDefaultImporterTest {

    private static final ConfigurationDocumentParser PARSER = new ConfigurationDocumentParser();
    private static final ConfigurationMerger MERGER = new ConfigurationMerger();

    private static JsonNode bundled() throws IOException {
        try (InputStream in = PlatformDefaultImporterTest.class.getResourceAsStream("/config/initial-platform-default.json")) {
            return PARSER.read(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void theBundledInitialConfigurationBecomesAPublishableDraftWhoseLayerPassesTheParser() throws IOException {
        JsonNode platform = bundled();
        ScoringConfigurationSnapshot snapshot = PARSER.parse(platform, new ConfigurationVersion("initial"));

        PlatformDefaultImporter.Imported imported = PlatformDefaultImporter.from(snapshot, "Initial questionnaire");
        QuestionnaireDocument tidy = QuestionnaireNormalizer.tidy(imported.document());

        assertThat(tidy.questions()).hasSize(snapshot.questions().size());
        assertThat(QuestionnaireValidator.publishable(tidy)).isEmpty();
        // every question keeps its id, its text from the presentation catalog and its dimension
        assertThat(tidy.questions()).extracting(QuestionnaireDocument.QuestionSpec::id).containsExactlyElementsOf(snapshot.questions().keySet());
        assertThat(tidy.questions()).allSatisfy(q -> assertThat(q.text()).isNotBlank());
        // patterns that rest on text flags or gaps are left out and said so; the others come across
        assertThat(tidy.rules()).isNotEmpty();
        assertThat(tidy.rules().size()).isLessThanOrEqualTo(snapshot.patterns().size());
        assertThat(imported.notes()).anyMatch(n -> n.contains("imbalance"));

        ScoringConfigurationSnapshot republished = PARSER.parse(MERGER.merge(List.of(platform, QuestionnaireGenerator.layer(tidy))),
                new ConfigurationVersion("copy"));
        assertThat(republished.questions().keySet()).containsExactlyElementsOf(snapshot.questions().keySet());
        assertThat(republished.dimensions().rules()).hasSameSizeAs(snapshot.dimensions().rules());
        assertThat(republished.patterns()).hasSameSizeAs(tidy.rules());
    }

    @Test
    void scoresDirectionsAndWeightsSurviveTheRoundTrip() throws IOException {
        JsonNode platform = bundled();
        ScoringConfigurationSnapshot snapshot = PARSER.parse(platform, new ConfigurationVersion("initial"));
        QuestionnaireDocument tidy = QuestionnaireNormalizer.tidy(PlatformDefaultImporter.from(snapshot, "Copy").document());
        ScoringConfigurationSnapshot copy = PARSER.parse(MERGER.merge(List.of(platform, QuestionnaireGenerator.layer(tidy))), new ConfigurationVersion("copy"));

        // TH02 is a negative 1 to 5 scale, DS01 a multiple choice with explicit scores, DS05 a boolean where "no" is desirable
        assertThat(copy.question("TH02").orElseThrow().normalization()).isEqualTo(snapshot.question("TH02").orElseThrow().normalization());
        assertThat(copy.question("DS01").orElseThrow().normalization()).isEqualTo(snapshot.question("DS01").orElseThrow().normalization());
        assertThat(copy.question("DS05").orElseThrow().normalization()).isEqualTo(snapshot.question("DS05").orElseThrow().normalization());
        assertThat(copy.dimensions().rules()).isEqualTo(snapshot.dimensions().rules());
    }

    @Test
    void theSampleDefaultWithATextQuestionKeepsItAsFreeTextAndSaysWhatItDropped() {
        QuestionnaireDocument document = PlatformDefaultImporter.from(ConfigurationSamples.sampleSnapshot(), "Sample copy").document();
        assertThat(document.questions()).extracting(QuestionnaireDocument.QuestionSpec::kind).contains(QuestionKind.FREE_TEXT);
        PlatformDefaultImporter.Imported imported = PlatformDefaultImporter.from(ConfigurationSamples.sampleSnapshot(), "Sample copy");
        assertThat(imported.notes()).anyMatch(n -> n.contains("free text"));
    }

    @Test
    void labelsAreReadableFromKeys() {
        assertThat(PlatformDefaultImporter.label("ALWAYS_ON_CALL")).isEqualTo("Always on call");
        assertThat(PlatformDefaultImporter.label("NEVER")).isEqualTo("Never");
    }
}
