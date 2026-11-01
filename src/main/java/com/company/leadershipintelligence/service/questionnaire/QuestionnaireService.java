package com.company.leadershipintelligence.service.questionnaire;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.repository.persistence.entity.QuestionnaireRows.QuestionnaireListRow;
import com.company.leadershipintelligence.repository.persistence.entity.QuestionnaireRows.QuestionnaireRow;
import com.company.leadershipintelligence.repository.persistence.entity.QuestionnaireRows.VersionRow;
import com.company.leadershipintelligence.repository.persistence.repository.QuestionnaireStore;
import com.company.leadershipintelligence.service.ConfigurationService;
import com.company.leadershipintelligence.service.EffectiveConfigurationService;
import com.company.leadershipintelligence.service.command.Approval;
import com.company.leadershipintelligence.service.command.Caller;
import com.company.leadershipintelligence.service.command.ConfigurationTarget;
import com.company.leadershipintelligence.service.query.ConfigurationVersionView;
import com.company.leadershipintelligence.service.query.QuestionnaireViews.Detail;
import com.company.leadershipintelligence.service.query.QuestionnaireViews.Summary;
import com.company.leadershipintelligence.service.query.QuestionnaireViews.VersionInfo;
import com.company.leadershipintelligence.service.query.ValidationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The questionnaire library (decisions D47, D50; specv3.md Section 4.14). A questionnaire is edited as a document in a draft and
 * published as an ACTIVE TEMPLATE layer of the caller's tenant through the ordinary configuration lifecycle, so there is no second
 * validator and no second activation path. A published version never changes: editing it opens a new draft, and publishing that
 * supersedes the previous version. Archiving hides a questionnaire from new use; it leaves the active layer alone so assessments
 * already taken stay reproducible. Every operation is tenant-scoped, and a questionnaire of another tenant looks like a missing one.
 * Nothing in a questionnaire is written to logs.
 */
@Service
public class QuestionnaireService {

    private static final int MAX_REPORTED_PROBLEMS = 50;

    private final QuestionnaireStore store;
    private final ConfigurationService configurations;
    private final EffectiveConfigurationService effective;
    private final ObjectMapper json;
    private final Clock clock;

    public QuestionnaireService(QuestionnaireStore store, ConfigurationService configurations, EffectiveConfigurationService effective,
                                ObjectMapper json, Clock clock) {
        this.store = store;
        this.configurations = configurations;
        this.effective = effective;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Summary> list(Caller caller) {
        return store.list(tenant(caller)).stream().map(QuestionnaireService::summary).toList();
    }

    /** The open draft if there is one, else the latest published version; or exactly {@code version} when given. */
    @Transactional(readOnly = true)
    public Detail get(Caller caller, String id, Integer version) {
        String tenant = tenant(caller);
        QuestionnaireRow questionnaire = require(store.find(tenant, id), id);
        VersionRow shown = version != null
                ? store.version(tenant, id, version).orElseThrow(() -> notFound(id))
                : store.draft(tenant, id).or(() -> store.latestPublished(tenant, id)).orElseThrow(() -> notFound(id));
        return detail(questionnaire, shown, store.versions(tenant, id));
    }

    @Transactional
    public Detail create(Caller caller, String name) {
        String tenant = tenant(caller);
        String cleanName = name == null || name.isBlank() ? null : name.trim();
        if (cleanName == null || cleanName.length() > 200) {
            throw invalid(List.of("a questionnaire needs a name of at most 200 characters"));
        }
        return createFrom(caller, tenant, new QuestionnaireDocument(cleanName, null, List.of(), List.of()));
    }

    /**
     * A new questionnaire whose first draft is a copy of the configuration that applies to the tenant (the platform default with the
     * tenant's own override), as far as the builder can express it. What was left out is listed in {@code notes}. Nothing is published.
     */
    @Transactional
    public Detail fromInitial(Caller caller, String name) {
        String tenant = tenant(caller);
        String cleanName = name == null || name.isBlank() ? null : name.trim();
        if (cleanName == null || cleanName.length() > 200) {
            throw invalid(List.of("a questionnaire needs a name of at most 200 characters"));
        }
        PlatformDefaultImporter.Imported imported = PlatformDefaultImporter.from(effective.resolve(caller.tenantId(), null, null).snapshot(), cleanName);
        return createFrom(caller, tenant, imported.document()).withNotes(imported.notes());
    }

    private Detail createFrom(Caller caller, String tenant, QuestionnaireDocument document) {
        QuestionnaireDocument tidy = QuestionnaireNormalizer.tidy(document);
        requireShape(tidy);
        if (tidy.name() == null) {
            throw invalid(List.of("a questionnaire needs a name"));
        }
        Instant now = clock.instant();
        String id = "q-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        store.insertQuestionnaire(tenant, id, tidy.name(), caller.actor(), now);
        store.insertDraft(tenant, id, 1, write(tidy), caller.actor(), now);
        return get(caller, id, null);
    }

    /**
     * Saves the document as the open draft. When the latest version is published, this opens the next draft from it. The draft may be
     * incomplete; only its shape is checked here and the publish checks are reported in {@code problems}.
     */
    @Transactional
    public Detail saveDraft(Caller caller, String id, QuestionnaireDocument document) {
        String tenant = tenant(caller);
        QuestionnaireRow questionnaire = require(store.lock(tenant, id), id);
        requireOpen(questionnaire);
        QuestionnaireDocument tidy = QuestionnaireNormalizer.tidy(document);
        requireShape(tidy);
        Instant now = clock.instant();
        VersionRow draft = store.draft(tenant, id).orElse(null);
        if (draft != null) {
            store.updateDraft(tenant, id, draft.version(), write(tidy), now);
        } else {
            store.insertDraft(tenant, id, store.maxVersion(tenant, id) + 1, write(tidy), caller.actor(), now);
        }
        if (tidy.name() != null && !tidy.name().equals(questionnaire.name())) {
            store.rename(tenant, id, tidy.name());
        }
        return get(caller, id, null);
    }

    /**
     * Publishes the open draft: the document is checked, turned into a configuration layer, validated and activated by the
     * configuration lifecycle, all in one transaction. Any failure leaves nothing behind.
     */
    @Transactional
    public Detail publish(Caller caller, String id) {
        String tenant = tenant(caller);
        QuestionnaireRow questionnaire = require(store.lock(tenant, id), id);
        requireOpen(questionnaire);
        VersionRow draft = store.draft(tenant, id)
                .orElseThrow(() -> new ScoringException(ScoringErrorCode.QUESTIONNAIRE_STATE, "there is no open draft to publish; edit the questionnaire first"));
        QuestionnaireDocument document = QuestionnaireNormalizer.tidy(read(draft.definition()));
        List<String> problems = QuestionnaireValidator.publishable(document);
        if (!problems.isEmpty()) {
            throw invalid(problems);
        }
        String layer = QuestionnaireGenerator.layer(document).toString();
        ConfigurationTarget target = ConfigurationTarget.template(caller.tenantId(), id);
        ConfigurationVersionView created = configurations.createDraft(caller, target, document.name(), layer);
        ValidationResult validation = configurations.submitForValidation(caller, created.id());
        if (!validation.valid()) {
            throw invalid(validation.problems());
        }
        ConfigurationVersionView active = configurations.activate(caller, created.id(), new Approval(caller.actor(), null));
        Instant now = clock.instant();
        store.markPublished(tenant, id, draft.version(), active.id(), write(document), caller.actor(), now);
        store.rename(tenant, id, document.name());
        store.registerTemplate(tenant, id, document.name(), draft.version());
        return get(caller, id, null);
    }

    /** Hides the questionnaire from new use. Published layers stay active so past assessments can be explained and recalculated. */
    @Transactional
    public Detail archive(Caller caller, String id) {
        String tenant = tenant(caller);
        QuestionnaireRow questionnaire = require(store.lock(tenant, id), id);
        if (questionnaire.archivedAt() == null) {
            store.archive(tenant, id, clock.instant());
        }
        return get(caller, id, null);
    }

    /** A new questionnaire with a copy of the current draft, or of the latest published version, as its first draft. */
    @Transactional
    public Detail duplicate(Caller caller, String id) {
        String tenant = tenant(caller);
        require(store.find(tenant, id), id);
        VersionRow source = store.draft(tenant, id).or(() -> store.latestPublished(tenant, id)).orElseThrow(() -> notFound(id));
        QuestionnaireDocument document = read(source.definition());
        String name = (document.name() == null ? "Questionnaire" : document.name()) + " (copy)";
        return createFrom(caller, tenant, new QuestionnaireDocument(name.length() > 200 ? name.substring(0, 200) : name,
                document.description(), document.questions(), document.rules()));
    }

    // ---------------- internals ----------------

    private static String tenant(Caller caller) {
        if (caller.isPlatform()) {
            throw new ScoringException(ScoringErrorCode.TENANT_ACCESS_DENIED, "questionnaires belong to a tenant");
        }
        return caller.tenantId().value();
    }

    private static QuestionnaireRow require(java.util.Optional<QuestionnaireRow> found, String id) {
        return found.orElseThrow(() -> notFound(id));
    }

    private static ScoringException notFound(String id) {
        return new ScoringException(ScoringErrorCode.QUESTIONNAIRE_NOT_FOUND, "questionnaire not found", Map.of("questionnaireId", id));
    }

    private static void requireOpen(QuestionnaireRow questionnaire) {
        if (questionnaire.archivedAt() != null) {
            throw new ScoringException(ScoringErrorCode.QUESTIONNAIRE_STATE, "the questionnaire is archived and cannot change");
        }
    }

    private static void requireShape(QuestionnaireDocument document) {
        List<String> problems = QuestionnaireValidator.shape(document);
        if (!problems.isEmpty()) {
            throw invalid(problems);
        }
    }

    static ScoringException invalid(List<String> problems) {
        Map<String, String> details = new LinkedHashMap<>();
        for (int i = 0; i < Math.min(problems.size(), MAX_REPORTED_PROBLEMS); i++) {
            details.put(String.format("problem%02d", i + 1), problems.get(i));
        }
        String message = problems.size() == 1 ? problems.get(0) : problems.get(0) + " (and " + (problems.size() - 1) + " more)";
        return new ScoringException(ScoringErrorCode.QUESTIONNAIRE_INVALID, message, details);
    }

    private Detail detail(QuestionnaireRow questionnaire, VersionRow shown, List<VersionRow> versions) {
        QuestionnaireDocument document = read(shown.definition());
        boolean draft = "DRAFT".equals(shown.status());
        List<String> problems = draft ? QuestionnaireValidator.publishable(QuestionnaireNormalizer.tidy(document)) : List.of();
        Integer draftVersion = versions.stream().filter(v -> "DRAFT".equals(v.status())).map(VersionRow::version).findFirst().orElse(null);
        Integer publishedVersion = versions.stream().filter(v -> "PUBLISHED".equals(v.status())).map(VersionRow::version).findFirst().orElse(null);
        return new Detail(questionnaire.id(), questionnaire.name(), state(questionnaire.archivedAt() != null, draftVersion, publishedVersion),
                questionnaire.archivedAt() != null, shown.version(), shown.status(), document, problems,
                versions.stream().map(v -> new VersionInfo(v.version(), v.status(), v.createdAt(), v.publishedAt(), v.publishedBy(),
                        v.configurationVersionId())).toList());
    }

    private static Summary summary(QuestionnaireListRow row) {
        return new Summary(row.id(), row.name(), state(row.archivedAt() != null, row.draftVersion(), row.publishedVersion()),
                row.archivedAt() != null, row.draftVersion(), row.publishedVersion(), row.createdAt(), row.updatedAt(), row.publishedAt());
    }

    private static String state(boolean archived, Integer draftVersion, Integer publishedVersion) {
        if (archived) {
            return "ARCHIVED";
        }
        if (publishedVersion == null) {
            return "DRAFT";
        }
        return draftVersion == null ? "PUBLISHED" : "PUBLISHED_WITH_DRAFT";
    }

    private String write(QuestionnaireDocument document) {
        try {
            return json.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private QuestionnaireDocument read(String definition) {
        try {
            return json.readValue(definition, QuestionnaireDocument.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("a stored questionnaire cannot be read", e);
        }
    }
}
