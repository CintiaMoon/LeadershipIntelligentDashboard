package com.company.leadershipintelligence.service.query;

import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Questionnaires as the application exposes them (decisions D47, D50). */
public final class QuestionnaireViews {

    private QuestionnaireViews() {
    }

    /**
     * @param state ARCHIVED, DRAFT (never published), PUBLISHED, or PUBLISHED_WITH_DRAFT (a newer draft is open)
     */
    public record Summary(String id, String name, String state, boolean archived, Integer draftVersion, Integer publishedVersion,
                          Instant createdAt, Instant updatedAt, Instant publishedAt) {
    }

    public record VersionInfo(int version, String status, Instant createdAt, Instant publishedAt, String publishedBy,
                              UUID configurationVersionId) {
    }

    /**
     * One version of a questionnaire. {@code problems} is what would stop it from being published right now, so the builder can
     * show it while editing (empty for a published version). {@code notes} is set only when a questionnaire was just copied from the
     * platform default, and lists what the builder could not carry over.
     */
    public record Detail(String id, String name, String state, boolean archived, int version, String versionStatus,
                         QuestionnaireDocument document, List<String> problems, List<VersionInfo> versions, List<String> notes) {

        public Detail {
            notes = notes == null ? List.of() : List.copyOf(notes);
        }

        public Detail(String id, String name, String state, boolean archived, int version, String versionStatus,
                      QuestionnaireDocument document, List<String> problems, List<VersionInfo> versions) {
            this(id, name, state, archived, version, versionStatus, document, problems, versions, List.of());
        }

        public Detail withNotes(List<String> notes) {
            return new Detail(id, name, state, archived, version, versionStatus, document, problems, versions, notes);
        }
    }
}
