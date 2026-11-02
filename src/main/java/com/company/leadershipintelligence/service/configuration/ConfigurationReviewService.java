package com.company.leadershipintelligence.service.configuration;

import com.company.leadershipintelligence.domain.enums.ConfigurationScope;
import com.company.leadershipintelligence.domain.enums.ConfigurationStatus;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.repository.persistence.repository.ConfigurationReviewStore;
import com.company.leadershipintelligence.repository.persistence.repository.ConfigurationReviewStore.FixtureRow;
import com.company.leadershipintelligence.repository.persistence.repository.ConfigurationReviewStore.ReviewRow;
import com.company.leadershipintelligence.service.ConfigurationService;
import com.company.leadershipintelligence.service.command.Approval;
import com.company.leadershipintelligence.service.command.ConfigurationTarget;
import com.company.leadershipintelligence.service.query.ConfigurationVersionView;
import com.company.leadershipintelligence.service.query.ConfigurationViews.ConfigurationLayer;
import com.company.leadershipintelligence.service.query.ConfigurationViews.ConfigurationVersionDetail;
import com.company.leadershipintelligence.service.query.ConfigurationViews.ConfigurationVersionSummary;
import com.company.leadershipintelligence.service.query.ConfigurationViews.FixtureResultView;
import com.company.leadershipintelligence.service.query.ConfigurationViews.FixtureView;
import com.company.leadershipintelligence.service.query.ConfigurationViews.PreviewResult;
import com.company.leadershipintelligence.service.query.ConfigurationViews.PreviewSummary;
import com.company.leadershipintelligence.service.query.ConfigurationViews.ReviewView;
import com.company.leadershipintelligence.service.query.ValidationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Managing scoring configuration in the product (decisions D52 to D58; specv3.md Sections 23.5, 23.6 and 42). The lifecycle itself is
 * {@link ConfigurationService}; this adds what a person needs around it: the layers they may manage, a draft to work on, every
 * validation problem, a preview, the fixtures of a layer, and an approval by a different person with a calibration record.
 *
 * <p>The second-person rule (D53): the approver must not be anyone who created, edited or submitted the version, unless
 * {@code lis.configuration.approval.require-second-person} is switched off. Approval is refused while a fixture of the layer fails
 * with the draft applied (D56). Template layers belong to the questionnaire builder and are not managed here; an organization layer
 * is reachable only for an organization in the caller's token. Nothing in a record holds an answer.
 */
@Service
public class ConfigurationReviewService {

    private final ConfigurationService configurations;
    private final ConfigurationPreviewService preview;
    private final ConfigurationReviewStore store;
    private final ObjectMapper json;
    private final Clock clock;
    private final boolean requireSecondPerson;

    public ConfigurationReviewService(ConfigurationService configurations, ConfigurationPreviewService preview, ConfigurationReviewStore store,
                                      ObjectMapper json, Clock clock,
                                      @Value("${lis.configuration.approval.require-second-person:true}") boolean requireSecondPerson) {
        this.configurations = configurations;
        this.preview = preview;
        this.store = store;
        this.json = json;
        this.clock = clock;
        this.requireSecondPerson = requireSecondPerson;
    }

    // ---------------- layers and versions ----------------

    /** The layers this caller manages, each with its active version and the one being worked on. */
    @Transactional(readOnly = true)
    public List<ConfigurationLayer> layers(Manager manager) {
        List<ConfigurationLayer> layers = new ArrayList<>();
        for (ConfigurationTarget target : targets(manager)) {
            List<ConfigurationVersionView> history = configurations.history(manager.caller(), target);
            ConfigurationVersionSummary active = history.stream().filter(v -> v.status() == ConfigurationStatus.ACTIVE).findFirst().map(this::summary).orElse(null);
            ConfigurationVersionSummary open = history.stream().filter(v -> v.status() == ConfigurationStatus.DRAFT || v.status() == ConfigurationStatus.VALIDATING)
                    .findFirst().map(this::summary).orElse(null);
            layers.add(new ConfigurationLayer(history.isEmpty() ? null : history.get(0).configurationId(), target.scope(), target.scopeId(), label(target), active, open));
        }
        return layers;
    }

    @Transactional(readOnly = true)
    public List<ConfigurationVersionDetail> versions(Manager manager, ConfigurationTarget target) {
        requireTarget(manager, target);
        return configurations.history(manager.caller(), target).stream().map(v -> detail(v, false)).toList();
    }

    @Transactional(readOnly = true)
    public ConfigurationVersionDetail get(Manager manager, UUID versionId) {
        return detail(managed(manager, versionId), true);
    }

    /** A new draft of a layer: the given document, or a copy of the active version (an empty document when there is none). */
    @Transactional
    public ConfigurationVersionDetail createDraft(Manager manager, ConfigurationTarget target, String payload) {
        requireTarget(manager, target);
        String content = payload;
        if (content == null) {
            content = configurations.history(manager.caller(), target).stream().filter(v -> v.status() == ConfigurationStatus.ACTIVE).findFirst()
                    .map(ConfigurationVersionView::payload).orElse("{}");
        }
        return detail(configurations.createDraft(manager.caller(), target, label(target), content), true);
    }

    /** A new draft with the content of any version of the layer: how a change is rolled back. */
    @Transactional
    public ConfigurationVersionDetail copyAsDraft(Manager manager, UUID versionId) {
        managed(manager, versionId);
        return detail(configurations.copyAsDraft(manager.caller(), versionId), true);
    }

    @Transactional
    public ConfigurationVersionDetail updateDraft(Manager manager, UUID versionId, String payload) {
        managed(manager, versionId);
        return detail(configurations.updateDraft(manager.caller(), versionId, payload), true);
    }

    /** Every problem of a version, without changing it. */
    @Transactional(readOnly = true)
    public ValidationResult check(Manager manager, UUID versionId) {
        managed(manager, versionId);
        return configurations.check(manager.caller(), versionId);
    }

    @Transactional
    public PreviewResult preview(Manager manager, UUID versionId) {
        return preview.preview(manager, managed(manager, versionId));
    }

    // ---------------- the approval workflow ----------------

    /**
     * Submits a draft for approval: validates it (every problem is returned) and, when it is valid, records the validation, the
     * preview summary and the fixture results and leaves it awaiting approval. A draft that is not valid stays a draft.
     */
    @Transactional
    public ReviewView submit(Manager manager, UUID versionId) {
        ConfigurationVersionView version = managed(manager, versionId);
        if (version.status() != ConfigurationStatus.DRAFT) {
            throw state("version " + versionId + " is " + version.status() + "; only a draft can be submitted for approval");
        }
        ValidationResult result = configurations.submitForValidation(manager.caller(), versionId);
        if (!result.valid()) {
            return new ReviewView(versionId, manager.actor(), null, result.problems(), null, List.of(), null, null, null, null, null, null, null, null);
        }
        ConfigurationVersionView awaiting = configurations.get(manager.caller(), versionId);
        PreviewResult previewed = preview.preview(manager, awaiting);
        store.submit(versionId, tenantOf(manager), manager.actor(), clock.instant(), write(List.of()),
                write(previewed.summary()), write(previewed.fixtures()));
        return review(store.review(versionId).orElseThrow());
    }

    /**
     * Approves and activates a version awaiting approval (decisions D53, D56, D57). Refused when the approver worked on the version
     * (unless the rule is switched off), when a fixture of the layer fails, or when there is no note.
     *
     * @param effectiveFrom when it starts to apply, or null for now
     */
    @Transactional
    public ConfigurationVersionDetail approve(Manager manager, UUID versionId, String note, Instant effectiveFrom, String calibrationReference,
                                              String knownLimitations) {
        ConfigurationVersionView version = managed(manager, versionId);
        if (version.status() != ConfigurationStatus.VALIDATING) {
            throw state("version " + versionId + " is " + version.status() + "; only a version awaiting approval can be approved");
        }
        ReviewRow review = store.review(versionId).orElseThrow(() -> state("version " + versionId + " was not submitted for approval"));
        if (note == null || note.isBlank()) {
            throw new ScoringException(ScoringErrorCode.CONFIGURATION_REVIEW_INVALID, "an approval needs a note saying what was reviewed", Map.of());
        }
        if (requireSecondPerson) {
            Set<String> workers = new LinkedHashSet<>(store.workedOnBy(versionId));
            workers.add(review.submittedBy());
            workers.add(version.createdBy());
            if (workers.contains(manager.actor())) {
                throw new ScoringException(ScoringErrorCode.CONFIGURATION_APPROVAL_REFUSED,
                        "a version must be approved by someone who did not create, edit or submit it", Map.of());
            }
        }
        List<FixtureResultView> fixtures = preview.runFixtures(manager, version);
        List<FixtureResultView> failed = fixtures.stream().filter(f -> !f.passed()).toList();
        if (!failed.isEmpty()) {
            Map<String, String> details = new LinkedHashMap<>();
            for (int i = 0; i < Math.min(failed.size(), 10); i++) {
                details.put(String.format("fixture%02d", i + 1), failed.get(i).name() + ": " + String.join("; ", failed.get(i).failures()));
            }
            throw new ScoringException(ScoringErrorCode.CONFIGURATION_FIXTURES_FAILED,
                    failed.size() + " of " + fixtures.size() + " fixtures fail with this version, so it cannot be approved", details);
        }
        configurations.activate(manager.caller(), versionId, new Approval(manager.actor(), effectiveFrom));
        PreviewSummary summary = review.previewJson() == null ? null : read(review.previewJson(), PreviewSummary.class);
        store.approve(versionId, manager.actor(), clock.instant(), note.trim(), blankToNull(calibrationReference), blankToNull(knownLimitations),
                summary == null ? null : write(summary), write(fixtures));
        configurations.recordAction(manager.caller(), versionId, "APPROVED", Map.of("fixtures", fixtures.size(), "calibrated", calibrationReference != null && !calibrationReference.isBlank()));
        return detail(configurations.get(manager.caller(), versionId), true);
    }

    /** Sends a version awaiting approval back to draft, with the reason. Anyone who may manage the layer can reject, including the author. */
    @Transactional
    public ConfigurationVersionDetail reject(Manager manager, UUID versionId, String comment) {
        managed(manager, versionId);
        if (comment == null || comment.isBlank()) {
            throw new ScoringException(ScoringErrorCode.CONFIGURATION_REVIEW_INVALID, "a rejection needs a comment saying what must change", Map.of());
        }
        ConfigurationVersionView rejected = configurations.reject(manager.caller(), versionId, comment.trim());
        store.reject(versionId, manager.actor(), clock.instant(), comment.trim());
        return detail(rejected, true);
    }

    @Transactional
    public ConfigurationVersionDetail pause(Manager manager, UUID versionId) {
        managed(manager, versionId);
        return detail(configurations.pause(manager.caller(), versionId), false);
    }

    @Transactional
    public ConfigurationVersionDetail resume(Manager manager, UUID versionId) {
        managed(manager, versionId);
        return detail(configurations.resume(manager.caller(), versionId), false);
    }

    @Transactional
    public ConfigurationVersionDetail archive(Manager manager, UUID versionId) {
        managed(manager, versionId);
        return detail(configurations.archive(manager.caller(), versionId), false);
    }

    // ---------------- fixtures ----------------

    @Transactional(readOnly = true)
    public List<FixtureView> fixtures(Manager manager, UUID layerId) {
        layer(manager, layerId);
        return store.fixtures(layerId).stream().map(this::view).toList();
    }

    @Transactional
    public FixtureView saveFixture(Manager manager, UUID layerId, UUID fixtureId, FixtureSpec spec) {
        layer(manager, layerId);
        List<String> problems = problems(spec);
        if (!problems.isEmpty()) {
            Map<String, String> details = new LinkedHashMap<>();
            for (int i = 0; i < problems.size(); i++) {
                details.put(String.format("problem%02d", i + 1), problems.get(i));
            }
            throw new ScoringException(ScoringErrorCode.FIXTURE_INVALID, problems.get(0), details);
        }
        String answers = write(spec.answers());
        String expectations = write(spec.expectations() == null ? new FixtureSpec.Expectations(null, null, null, null, null) : spec.expectations());
        try {
            if (fixtureId == null) {
                UUID id = UUID.randomUUID();
                store.insertFixture(id, layerId, manager.platform() ? null : manager.caller().tenantId().value(), spec.name().trim(), answers, expectations,
                        manager.actor(), clock.instant());
                return view(store.fixture(layerId, id).orElseThrow());
            }
            store.fixture(layerId, fixtureId).orElseThrow(() -> new ScoringException(ScoringErrorCode.FIXTURE_NOT_FOUND, "fixture not found", Map.of()));
            store.updateFixture(layerId, fixtureId, spec.name().trim(), answers, expectations, clock.instant());
            return view(store.fixture(layerId, fixtureId).orElseThrow());
        } catch (DuplicateKeyException e) {
            throw new ScoringException(ScoringErrorCode.FIXTURE_INVALID, "the layer already has a fixture with this name", Map.of());
        }
    }

    @Transactional
    public void deleteFixture(Manager manager, UUID layerId, UUID fixtureId) {
        layer(manager, layerId);
        store.fixture(layerId, fixtureId).orElseThrow(() -> new ScoringException(ScoringErrorCode.FIXTURE_NOT_FOUND, "fixture not found", Map.of()));
        store.deleteFixture(layerId, fixtureId);
    }

    /** Bundled starter fixtures are put in place when a layer has none (used once, when the platform default is first activated). */
    @Transactional
    public int addStarterFixtures(UUID layerId, String actor, List<FixtureSpec> fixtures) {
        if (store.fixtureCount(layerId) > 0) {
            return 0;
        }
        for (FixtureSpec spec : fixtures) {
            store.insertFixture(UUID.randomUUID(), layerId, null, spec.name(), write(spec.answers()), write(spec.expectations()), actor, clock.instant());
        }
        return fixtures.size();
    }

    // ---------------- internals ----------------

    private List<ConfigurationTarget> targets(Manager manager) {
        List<ConfigurationTarget> targets = new ArrayList<>();
        if (manager.platform()) {
            targets.add(ConfigurationTarget.platform());
            return targets;
        }
        targets.add(ConfigurationTarget.tenant(manager.caller().tenantId()));
        manager.scope().organizationIds().stream().sorted().forEach(o -> targets.add(ConfigurationTarget.organization(manager.caller().tenantId(), new OrganizationId(o))));
        return targets;
    }

    private static String label(ConfigurationTarget target) {
        return switch (target.scope()) {
            case PLATFORM_DEFAULT -> "Platform default";
            case TENANT -> "Tenant override";
            case ORGANIZATION -> "Organization override " + target.scopeId();
            default -> "Questionnaire layer " + target.scopeId();
        };
    }

    /** A target the manager may work on: platform for the platform role, tenant and own organizations otherwise; never a template layer. */
    private static void requireTarget(Manager manager, ConfigurationTarget target) {
        boolean allowed = switch (target.scope()) {
            case PLATFORM_DEFAULT -> manager.platform();
            case TENANT -> !manager.platform() && manager.caller().tenantId().equals(target.tenantId());
            case ORGANIZATION -> !manager.platform() && manager.caller().tenantId().equals(target.tenantId()) && manager.scope().organizationIds().contains(target.scopeId());
            case TEMPLATE -> false;
        };
        if (!allowed) {
            throw new ScoringException(ScoringErrorCode.TENANT_ACCESS_DENIED, "the caller cannot manage this configuration layer", Map.of());
        }
    }

    /** The version, visible to this caller and in a layer they manage; anything else looks missing. */
    private ConfigurationVersionView managed(Manager manager, UUID versionId) {
        ConfigurationVersionView version = configurations.get(manager.caller(), versionId);
        boolean visible = switch (version.scope()) {
            case PLATFORM_DEFAULT, TENANT -> true;
            case ORGANIZATION -> manager.scope().organizationIds().contains(version.scopeId());
            case TEMPLATE -> false;
        };
        if (!visible) {
            throw new ScoringException(ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND, "configuration version not found", Map.of());
        }
        return version;
    }

    private ConfigurationTarget layer(Manager manager, UUID layerId) {
        ConfigurationTarget target = configurations.targetOf(manager.caller(), layerId);
        if (target.scope() == ConfigurationScope.TEMPLATE
                || (target.scope() == ConfigurationScope.ORGANIZATION && !manager.scope().organizationIds().contains(target.scopeId()))) {
            throw new ScoringException(ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND, "configuration layer not found", Map.of());
        }
        return target;
    }

    private static String tenantOf(Manager manager) {
        return manager.platform() ? null : manager.caller().tenantId().value();
    }

    private ConfigurationVersionSummary summary(ConfigurationVersionView v) {
        return new ConfigurationVersionSummary(v.id(), v.version(), v.status(), v.effectiveFrom(), unvalidated(v.id()));
    }

    private boolean unvalidated(UUID versionId) {
        return store.review(versionId).map(r -> r.calibrationReference() == null || r.calibrationReference().isBlank()).orElse(true);
    }

    private ConfigurationVersionDetail detail(ConfigurationVersionView v, boolean withPayload) {
        Optional<ReviewRow> review = store.review(v.id());
        boolean unvalidated = review.map(r -> r.calibrationReference() == null || r.calibrationReference().isBlank()).orElse(true);
        return new ConfigurationVersionDetail(v.id(), v.configurationId(), v.scope(), v.scopeId(), v.version(), v.status(), v.configHash(), v.createdBy(),
                v.createdAt(), v.approvedBy(), v.approvedAt(), v.effectiveFrom(), unvalidated, review.map(this::review).orElse(null), withPayload ? v.payload() : null);
    }

    private ReviewView review(ReviewRow r) {
        return new ReviewView(r.versionId(), r.submittedBy(), r.submittedAt(), r.validationJson() == null ? List.of() : read(r.validationJson(), new TypeReference<List<String>>() {
        }), r.previewJson() == null ? null : read(r.previewJson(), PreviewSummary.class),
                r.fixturesJson() == null ? List.of() : read(r.fixturesJson(), new TypeReference<List<FixtureResultView>>() {
                }), r.approvedBy(), r.approvedAt(), r.approvalNote(), r.calibrationReference(), r.knownLimitations(), r.rejectedBy(), r.rejectedAt(),
                r.rejectionComment());
    }

    private FixtureView view(FixtureRow row) {
        return new FixtureView(row.id(), row.name(), read(row.answersJson(), Object.class), read(row.expectationsJson(), Object.class), row.createdBy(), row.updatedAt());
    }

    private static List<String> problems(FixtureSpec spec) {
        List<String> problems = new ArrayList<>();
        if (spec == null || spec.name() == null || spec.name().isBlank() || spec.name().trim().length() > 200) {
            problems.add("a fixture needs a name of 1 to 200 characters");
        }
        if (spec == null) {
            return problems;
        }
        if (spec.answers().isEmpty() || spec.answers().size() > 200) {
            problems.add("a fixture needs between 1 and 200 answers");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (FixtureSpec.Answer a : spec.answers()) {
            if (a.questionId() == null || a.questionId().isBlank() || a.answer() == null) {
                problems.add("every answer needs a question id and a value");
            } else if (!seen.add(a.questionId())) {
                problems.add("question " + a.questionId() + " is answered twice");
            }
        }
        FixtureSpec.Expectations e = spec.expectations();
        if (e != null) {
            if (e.status() != null && !e.status().equals("VALID") && !e.status().equals("INSUFFICIENT_DATA")) {
                problems.add("the expected status is VALID or INSUFFICIENT_DATA");
            }
            if (e.overall() != null) {
                rangeProblem("the overall range", e.overall(), problems);
            }
            e.dimensions().forEach((dimension, range) -> rangeProblem("the " + dimension + " range", range, problems));
            Set<String> both = new LinkedHashSet<>(e.patternsMatched());
            both.retainAll(e.patternsNotMatched());
            both.forEach(id -> problems.add("pattern " + id + " cannot be both expected and not expected"));
        }
        return problems;
    }

    private static void rangeProblem(String what, FixtureSpec.Range range, List<String> problems) {
        if (range.min() != null && range.max() != null && range.min().compareTo(range.max()) > 0) {
            problems.add(what + " has a minimum above its maximum");
        }
    }

    private ScoringException state(String message) {
        return new ScoringException(ScoringErrorCode.INVALID_CONFIGURATION_VERSION, message, Map.of());
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private <T> T read(String text, Class<T> type) {
        try {
            return json.readValue(text, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("a stored review cannot be read", e);
        }
    }

    private <T> T read(String text, TypeReference<T> type) {
        try {
            return json.readValue(text, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("a stored review cannot be read", e);
        }
    }
}
