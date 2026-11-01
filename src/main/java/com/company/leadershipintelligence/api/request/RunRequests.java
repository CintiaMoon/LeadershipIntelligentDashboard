package com.company.leadershipintelligence.api.request;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Bodies of the assessment run and respondent endpoints (decision D48). Unknown fields are rejected. */
public final class RunRequests {

    private RunRequests() {
    }

    public record StartRun(@NotBlank @Size(max = 200) String name, @NotBlank @Size(max = 64) String organizationId,
                           @NotBlank @Size(max = 64) String questionnaireId, @NotEmpty @Size(max = 100) List<@NotBlank String> teamIds,
                           LocalDate dueDate) {
    }

    /** One answer; a null or empty answer removes it from the draft. */
    public record DraftAnswer(@NotBlank @Size(max = 64) String questionId, JsonNode answer) {
    }

    public record SaveDraft(@NotEmpty @Size(max = 300) List<@Valid DraftAnswer> answers) {
    }
}
