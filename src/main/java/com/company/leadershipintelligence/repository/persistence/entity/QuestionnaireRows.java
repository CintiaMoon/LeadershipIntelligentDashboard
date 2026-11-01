package com.company.leadershipintelligence.repository.persistence.entity;

import java.time.Instant;
import java.util.UUID;

/** Rows of the questionnaire library (decisions D47, D48). The definition is the builder document as JSON text. */
public final class QuestionnaireRows {

    private QuestionnaireRows() {
    }

    public record QuestionnaireRow(String id, String name, String createdBy, Instant createdAt, Instant archivedAt) {
    }

    /** A questionnaire with the versions that matter for a list: the open draft and the latest published version, if any. */
    public record QuestionnaireListRow(String id, String name, Instant createdAt, Instant archivedAt, Integer draftVersion,
                                       Integer publishedVersion, Instant publishedAt, Instant updatedAt) {
    }

    public record VersionRow(String questionnaireId, int version, String status, String definition, UUID configurationVersionId,
                             String createdBy, Instant createdAt, Instant updatedAt, String publishedBy, Instant publishedAt) {
    }
}
