package com.company.leadershipintelligence.service.configuration;

import com.company.leadershipintelligence.domain.enums.ConfigurationStatus;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.repository.configuration.ConfigurationSamples;
import com.company.leadershipintelligence.service.ConfigurationService;
import com.company.leadershipintelligence.service.ScoreAssessmentService;
import com.company.leadershipintelligence.service.command.Approval;
import com.company.leadershipintelligence.service.command.CalculateCommand;
import com.company.leadershipintelligence.service.command.Caller;
import com.company.leadershipintelligence.service.command.ConfigurationTarget;
import com.company.leadershipintelligence.service.command.OrganizationScope;
import com.company.leadershipintelligence.service.query.ConfigurationVersionView;
import com.company.leadershipintelligence.service.query.ConfigurationViews.ConfigurationLayer;
import com.company.leadershipintelligence.service.query.ConfigurationViews.ConfigurationVersionDetail;
import com.company.leadershipintelligence.service.query.ConfigurationViews.FixtureView;
import com.company.leadershipintelligence.service.query.ConfigurationViews.PreviewItem;
import com.company.leadershipintelligence.service.query.ConfigurationViews.PreviewResult;
import com.company.leadershipintelligence.service.query.ConfigurationViews.ReviewView;
import com.company.leadershipintelligence.support.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Managing configuration in the product (decisions D52 to D57): the draft, validation with every problem, submission, approval by
 * a different person with a calibration record, the fixtures gate, preview and impact analysis, rollback, and who can reach what.
 * The platform default is the illustrative test sample.
 */
class ConfigurationManagementIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    ConfigurationReviewService review;

    @Autowired
    ConfigurationService configurations;

    @Autowired
    ScoreAssessmentService scoring;

    @Autowired
    ConfigurationEvaluator evaluator;

    @Autowired
    ObjectMapper json;

    private static final Manager A = Manager.platform("platform-a");
    private static final Manager B = Manager.platform("platform-b");
    private static final Manager C = Manager.platform("platform-c");
    private static final Manager D = Manager.platform("platform-d");

    private static final List<FixtureSpec.Answer> ANSWERS = List.of(new FixtureSpec.Answer("Q01", 2), new FixtureSpec.Answer("Q02", "OFTEN"),
            new FixtureSpec.Answer("Q03", true), new FixtureSpec.Answer("Q04", "only one key person"), new FixtureSpec.Answer("Q05", 80));

    private UUID activePlatform;

    @BeforeEach
    void platformDefault() {
        tenant("cm-a");
        tenant("cm-b");
        Caller ops = Caller.platform("ops");
        ConfigurationVersionView draft = configurations.createDraft(ops, ConfigurationTarget.platform(), "platform", ConfigurationSamples.sampleText());
        configurations.submitForValidation(ops, draft.id());
        configurations.activate(ops, draft.id(), new Approval("ops-approver", null));
        activePlatform = draft.id();
    }

    private static Manager tenantManager(String tenant, String actor, String... organizations) {
        return new Manager(Caller.tenant(new TenantId(tenant), actor), OrganizationScope.of(List.of(organizations)));
    }

    private static ConfigurationTarget platformTarget() {
        return ConfigurationTarget.platform();
    }

    /** The active platform document with the overall weights moved towards Team Health, which changes every score. */
    private String heavyOnTeamHealth() throws Exception {
        ObjectNode doc = (ObjectNode) json.readTree(review.get(A, activePlatform).payload());
        ObjectNode weights = (ObjectNode) doc.get("overall").get("weights");
        weights.put("TEAM_HEALTH", 0.7).put("DELIVERY_STABILITY", 0.1).put("TECHNICAL_RISK", 0.1).put("LEADERSHIP_LOAD", 0.1);
        return json.writeValueAsString(doc);
    }

    private String slightChange() throws Exception {
        return review.get(A, activePlatform).payload().replace("\"tolerance\": 0.0001", "\"tolerance\": 0.0002");
    }

    private UUID submittedDraft(Manager author, String payload) {
        ConfigurationVersionDetail draft = review.createDraft(author, platformTarget(), payload);
        review.submit(author, draft.id());
        return draft.id();
    }

    private static void assertCode(Runnable action, ScoringErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ScoringException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    private void assessment(String tenant, String organization, int q01) {
        scoring.calculate(new CalculateCommand(new TenantId(tenant), new OrganizationId(organization), "tpl-1", 1, "COMPLETED",
                Instant.parse("2026-11-01T09:00:00Z"), List.of(new ScoringInput("Q01", q01), new ScoringInput("Q02", "OFTEN"), new ScoringInput("Q03", true),
                new ScoringInput("Q04", "only one key person"), new ScoringInput("Q05", 80)), null));
    }

    // ---------------- the workflow ----------------

    @Test
    void aDraftIsSubmittedAndApprovedByAnotherPersonWithACalibrationRecord() throws Exception {
        ConfigurationVersionDetail draft = review.createDraft(A, platformTarget(), null);
        assertThat(draft.status()).isEqualTo(ConfigurationStatus.DRAFT);
        assertThat(json.readTree(draft.payload())).isEqualTo(json.readTree(review.get(A, activePlatform).payload()));
        review.updateDraft(A, draft.id(), slightChange());

        ReviewView submitted = review.submit(A, draft.id());
        assertThat(submitted.submittedBy()).isEqualTo("platform-a");
        assertThat(submitted.validationProblems()).isEmpty();
        assertThat(review.get(A, draft.id()).status()).isEqualTo(ConfigurationStatus.VALIDATING);

        // the person who made it cannot approve it
        assertCode(() -> review.approve(A, draft.id(), "looks right", null, null, null), ScoringErrorCode.CONFIGURATION_APPROVAL_REFUSED);
        assertThat(review.get(A, draft.id()).status()).isEqualTo(ConfigurationStatus.VALIDATING);
        // a note is required
        assertCode(() -> review.approve(B, draft.id(), "  ", null, null, null), ScoringErrorCode.CONFIGURATION_REVIEW_INVALID);

        ConfigurationVersionDetail approved = review.approve(B, draft.id(), "Checked the weights against the pilot.", null, "Pilot of 12 teams, Q3 2026", "Small sample");
        assertThat(approved.status()).isEqualTo(ConfigurationStatus.ACTIVE);
        assertThat(approved.approvedBy()).isEqualTo("platform-b");
        assertThat(approved.unvalidated()).isFalse();
        assertThat(approved.review().approvedBy()).isEqualTo("platform-b");
        assertThat(approved.review().approvalNote()).isEqualTo("Checked the weights against the pilot.");
        assertThat(approved.review().calibrationReference()).isEqualTo("Pilot of 12 teams, Q3 2026");
        assertThat(approved.review().knownLimitations()).isEqualTo("Small sample");
        // the earlier active version was superseded, and the audit records the whole path
        assertThat(review.get(A, activePlatform).status()).isEqualTo(ConfigurationStatus.ARCHIVED);
        assertThat(jdbc.queryForList("select action from scoring_configuration_audit where version_id = ? order by id", String.class, draft.id()))
                .containsExactly("CREATED", "UPDATED", "VALIDATED", "ACTIVATED", "APPROVED");
        // an approved review never changes
        assertThatThrownBy(() -> jdbc.update("update configuration_review set approval_note = 'changed' where version_id = ?", draft.id()))
                .hasMessageContaining("approved and cannot change");
    }

    @Test
    void anApprovalWithoutACalibrationReferenceIsShownAsUnvalidated() throws Exception {
        UUID id = submittedDraft(A, slightChange());
        ConfigurationVersionDetail approved = review.approve(B, id, "Reviewed the change.", null, null, null);
        assertThat(approved.status()).isEqualTo(ConfigurationStatus.ACTIVE);
        assertThat(approved.unvalidated()).isTrue();
        // the version loaded by the startup file, which had no review at all, is unvalidated too
        assertThat(review.get(A, activePlatform).unvalidated()).isTrue();
        assertThat(review.layers(A).get(0).active().unvalidated()).isTrue();
    }

    @Test
    void everyoneWhoCreatedEditedOrSubmittedAVersionIsRefusedAsApprover() throws Exception {
        ConfigurationVersionDetail draft = review.createDraft(A, platformTarget(), null);   // A creates
        configurations.updateDraft(B.caller(), draft.id(), slightChange());                  // B edits
        review.submit(C, draft.id());                                                        // C submits
        for (Manager worker : List.of(A, B, C)) {
            assertCode(() -> review.approve(worker, draft.id(), "ok", null, null, null), ScoringErrorCode.CONFIGURATION_APPROVAL_REFUSED);
        }
        assertThat(review.approve(D, draft.id(), "ok", null, null, null).status()).isEqualTo(ConfigurationStatus.ACTIVE);
    }

    @Test
    void anInvalidDraftReportsEveryProblemAndStaysADraft() throws Exception {
        ObjectNode broken = (ObjectNode) json.readTree(review.get(A, activePlatform).payload());
        ((ObjectNode) broken.get("questions").get(0)).remove("normalization");
        ((ObjectNode) broken.get("signals").get(0)).put("dataType", "WRONG");
        broken.put("surprise", 1);
        ConfigurationVersionDetail draft = review.createDraft(A, platformTarget(), json.writeValueAsString(broken));

        assertThat(review.check(A, draft.id()).problems()).hasSize(3);
        ReviewView result = review.submit(A, draft.id());
        assertThat(result.validationProblems()).hasSize(3);
        assertThat(result.submittedAt()).isNull();
        ConfigurationVersionDetail after = review.get(A, draft.id());
        assertThat(after.status()).isEqualTo(ConfigurationStatus.DRAFT);
        assertThat(after.review()).isNull();
        // approving something that was never submitted is refused
        assertCode(() -> review.approve(B, draft.id(), "ok", null, null, null), ScoringErrorCode.INVALID_CONFIGURATION_VERSION);
    }

    @Test
    void aRejectionReturnsTheVersionToDraftWithTheReasonAndResubmittingClearsIt() throws Exception {
        UUID id = submittedDraft(A, slightChange());
        assertCode(() -> review.reject(B, id, " "), ScoringErrorCode.CONFIGURATION_REVIEW_INVALID);
        ConfigurationVersionDetail rejected = review.reject(B, id, "The tolerance change needs a reason.");
        assertThat(rejected.status()).isEqualTo(ConfigurationStatus.DRAFT);
        assertThat(rejected.review().rejectedBy()).isEqualTo("platform-b");
        assertThat(rejected.review().rejectionComment()).isEqualTo("The tolerance change needs a reason.");
        assertThat(jdbc.queryForObject("select detail->>'comment' from scoring_configuration_audit where action = 'REJECTED'", String.class))
                .isEqualTo("The tolerance change needs a reason.");

        ReviewView again = review.submit(A, id);
        assertThat(again.rejectionComment()).isNull();
        assertThat(review.get(A, id).status()).isEqualTo(ConfigurationStatus.VALIDATING);
        // the author may also withdraw a submission by rejecting it
        assertThat(review.reject(A, id, "Withdrawn").status()).isEqualTo(ConfigurationStatus.DRAFT);
    }

    @Test
    void aVersionNotAwaitingApprovalCannotBeApprovedOrRejected() throws Exception {
        ConfigurationVersionDetail draft = review.createDraft(A, platformTarget(), null);
        assertCode(() -> review.approve(B, draft.id(), "ok", null, null, null), ScoringErrorCode.INVALID_CONFIGURATION_VERSION);
        assertCode(() -> review.reject(B, draft.id(), "no"), ScoringErrorCode.INVALID_CONFIGURATION_VERSION);
        assertCode(() -> review.submit(A, activePlatform), ScoringErrorCode.INVALID_CONFIGURATION_VERSION);
    }

    @Test
    void anArchivedVersionCanBeCopiedAsANewDraftWhichIsHowAChangeIsRolledBack() throws Exception {
        UUID id = submittedDraft(A, slightChange());
        review.approve(B, id, "ok", null, null, null);
        assertThat(review.get(A, activePlatform).status()).isEqualTo(ConfigurationStatus.ARCHIVED);

        ConfigurationVersionDetail rollback = review.copyAsDraft(A, activePlatform);
        assertThat(rollback.status()).isEqualTo(ConfigurationStatus.DRAFT);
        assertThat(json.readTree(rollback.payload())).isEqualTo(json.readTree(review.get(A, activePlatform).payload()));
        assertThat(rollback.version()).isGreaterThan(review.get(A, id).version());
        review.submit(A, rollback.id());
        review.approve(B, rollback.id(), "Rolled back the tolerance.", null, null, null);
        assertThat(review.get(A, id).status()).isEqualTo(ConfigurationStatus.ARCHIVED);
        assertThat(review.get(A, rollback.id()).status()).isEqualTo(ConfigurationStatus.ACTIVE);
    }

    @Test
    void anActiveVersionCanBePausedResumedAndArchived() throws Exception {
        UUID id = submittedDraft(A, slightChange());
        review.approve(B, id, "ok", null, null, null);
        assertThat(review.pause(B, id).status()).isEqualTo(ConfigurationStatus.PAUSED);
        assertThat(review.resume(B, id).status()).isEqualTo(ConfigurationStatus.ACTIVE);
        assertThat(review.archive(B, id).status()).isEqualTo(ConfigurationStatus.ARCHIVED);
    }

    // ---------------- fixtures ----------------

    private FixtureSpec.Expectations around(com.company.leadershipintelligence.service.query.ConfigurationViews.ConfigurationOutcome outcome) {
        return new FixtureSpec.Expectations("VALID", new FixtureSpec.Range(outcome.score().subtract(BigDecimal.ONE), outcome.score().add(BigDecimal.ONE)), null, null, null);
    }

    @Test
    void fixturesAreKeptPerLayerAndCheckedWhenTheyAreSaved() throws Exception {
        UUID layer = review.get(A, activePlatform).layerId();
        FixtureView saved = review.saveFixture(A, layer, null, new FixtureSpec("Typical", ANSWERS, new FixtureSpec.Expectations("VALID", null, null, null, null)));
        assertThat(saved.name()).isEqualTo("Typical");
        assertThat(review.fixtures(A, layer)).hasSize(1);
        // a name is unique in a layer, a fixture needs answers, ranges must be ordered, a pattern cannot be both expected and not
        assertCode(() -> review.saveFixture(A, layer, null, new FixtureSpec("Typical", ANSWERS, null)), ScoringErrorCode.FIXTURE_INVALID);
        assertCode(() -> review.saveFixture(A, layer, null, new FixtureSpec("Empty", List.of(), null)), ScoringErrorCode.FIXTURE_INVALID);
        assertCode(() -> review.saveFixture(A, layer, null, new FixtureSpec("Backwards", ANSWERS,
                new FixtureSpec.Expectations(null, new FixtureSpec.Range(BigDecimal.TEN, BigDecimal.ONE), null, null, null))), ScoringErrorCode.FIXTURE_INVALID);
        assertCode(() -> review.saveFixture(A, layer, null, new FixtureSpec("Both", ANSWERS,
                new FixtureSpec.Expectations(null, null, null, List.of("TH-001"), List.of("TH-001")))), ScoringErrorCode.FIXTURE_INVALID);
        // update and delete
        FixtureView renamed = review.saveFixture(A, layer, saved.id(), new FixtureSpec("Typical team", ANSWERS, null));
        assertThat(renamed.name()).isEqualTo("Typical team");
        review.deleteFixture(A, layer, saved.id());
        assertThat(review.fixtures(A, layer)).isEmpty();
        assertCode(() -> review.deleteFixture(A, layer, saved.id()), ScoringErrorCode.FIXTURE_NOT_FOUND);
    }

    @Test
    void approvalIsRefusedWhileAFixtureFailsAndTheFailureIsExplained() throws Exception {
        UUID layer = review.get(A, activePlatform).layerId();
        var now = evaluator.evaluate(ConfigurationSamples.sampleSnapshot(), ConfigurationEvaluator.answersOf(new FixtureSpec("x", ANSWERS, null)));
        review.saveFixture(A, layer, null, new FixtureSpec("Typical team", ANSWERS, around(now)));

        UUID heavy = submittedDraft(A, heavyOnTeamHealth());
        ReviewView submitted = review.get(A, heavy).review();
        assertThat(submitted.fixtures()).hasSize(1);
        assertThat(submitted.fixtures().get(0).passed()).isFalse();
        assertThat(submitted.preview().fixturesPassed()).isZero();

        assertThatThrownBy(() -> review.approve(B, heavy, "ok", null, null, null)).isInstanceOfSatisfying(ScoringException.class, e -> {
            assertThat(e.code()).isEqualTo(ScoringErrorCode.CONFIGURATION_FIXTURES_FAILED);
            assertThat(e.details().get("fixture01")).startsWith("Typical team: the overall score is");
        });
        assertThat(review.get(A, heavy).status()).isEqualTo(ConfigurationStatus.VALIDATING);

        // sent back, corrected to a change that keeps the fixture, it passes
        review.reject(B, heavy, "The weights move the typical team out of its range.");
        review.updateDraft(A, heavy, slightChange());
        review.submit(A, heavy);
        assertThat(review.approve(B, heavy, "Fixture holds.", null, null, null).status()).isEqualTo(ConfigurationStatus.ACTIVE);
    }

    @Test
    void aVersionThatCannotScoreAFixtureFailsItWithTheReason() throws Exception {
        UUID layer = review.get(A, activePlatform).layerId();
        review.saveFixture(A, layer, null, new FixtureSpec("Asks a question that is gone", List.of(new FixtureSpec.Answer("Q99", 3)), null));
        UUID id = submittedDraft(A, slightChange());
        assertThat(review.get(A, id).review().fixtures().get(0).failures().get(0)).contains("could not be scored").contains("QUESTION_NOT_CONFIGURED");
        assertCode(() -> review.approve(B, id, "ok", null, null, null), ScoringErrorCode.CONFIGURATION_FIXTURES_FAILED);
    }

    // ---------------- preview ----------------

    @Test
    void aPreviewScoresTheFixturesAndTheTenantsRecentAssessmentsBeforeAndAfterWithoutShowingOrStoringAnything() throws Exception {
        assessment("cm-a", "org-1", 2);
        assessment("cm-a", "org-1", 4);
        assessment("cm-a", "org-1", 5);
        assessment("cm-b", "org-1", 1);   // another tenant's: never read
        Manager admin = tenantManager("cm-a", "admin-1", "org-1");
        int runsBefore = jdbc.queryForObject("select count(*) from scoring_run", Integer.class);

        // a tenant override that moves the overall weights to Team Health
        ConfigurationVersionDetail draft = review.createDraft(admin, ConfigurationTarget.tenant(new TenantId("cm-a")),
                "{\"overall\":{\"weights\":{\"TEAM_HEALTH\":0.7,\"DELIVERY_STABILITY\":0.1,\"TECHNICAL_RISK\":0.1,\"LEADERSHIP_LOAD\":0.1}}}");
        review.saveFixture(admin, draft.layerId(), null, new FixtureSpec("Typical", ANSWERS, new FixtureSpec.Expectations("VALID", null, null, null, null)));

        PreviewResult result = review.preview(admin, draft.id());
        assertThat(result.summary().assessmentsUsed()).isTrue();
        assertThat(result.summary().assessments()).isEqualTo(3);
        assertThat(result.summary().fixtures()).isEqualTo(1);
        assertThat(result.summary().scoresChanged()).isPositive();
        assertThat(result.summary().before().scored()).isEqualTo(3);
        assertThat(result.summary().before().minimum()).isLessThanOrEqualTo(result.summary().before().median());
        assertThat(result.summary().after().maximum()).isGreaterThanOrEqualTo(result.summary().after().median());
        List<PreviewItem> assessments = result.items().stream().filter(i -> i.kind().equals("ASSESSMENT")).toList();
        assertThat(assessments).hasSize(3).allSatisfy(i -> {
            assertThat(i.organizationId()).isEqualTo("org-1");
            assertThat(i.before().score()).isNotNull();
            assertThat(i.after().score()).isNotNull();
        });
        assertThat(assessments).anySatisfy(i -> assertThat(i.changes()).anyMatch(c -> c.startsWith("the overall score changes from")));

        // nothing was stored, nothing is returned that could be an answer, and the read was audited by action and count
        assertThat(jdbc.queryForObject("select count(*) from scoring_run", Integer.class)).isEqualTo(runsBefore);
        String text = json.writeValueAsString(result);
        assertThat(text).doesNotContain("only one key person").doesNotContain("OFTEN");
        assertThat(jdbc.queryForObject("select (detail->>'assessments')::int from scoring_access_audit where action = 'CONFIGURATION_PREVIEW' and tenant_id = 'cm-a'",
                Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select detail::text from scoring_access_audit where action = 'CONFIGURATION_PREVIEW'", String.class)).doesNotContain("only one key person");
    }

    @Test
    void aPreviewReadsOnlyTheOrganizationsInTheCallersToken() throws Exception {
        assessment("cm-a", "org-1", 2);
        assessment("cm-a", "org-2", 4);
        Manager orgOne = tenantManager("cm-a", "admin-1", "org-1");
        ConfigurationVersionDetail draft = review.createDraft(orgOne, ConfigurationTarget.tenant(new TenantId("cm-a")), "{}");
        PreviewResult result = review.preview(orgOne, draft.id());
        assertThat(result.items()).extracting(PreviewItem::organizationId).containsExactly("org-1");

        // an organization layer previews only its own organization
        Manager both = tenantManager("cm-a", "admin-2", "org-1", "org-2");
        ConfigurationVersionDetail orgDraft = review.createDraft(both, ConfigurationTarget.organization(new TenantId("cm-a"), new OrganizationId("org-2")), "{}");
        assertThat(review.preview(both, orgDraft.id()).items()).extracting(PreviewItem::organizationId).containsExactly("org-2");
    }

    @Test
    void aPlatformPreviewUsesFixturesOnlyBecauseThePlatformAdministratorHasNoTenant() throws Exception {
        assessment("cm-a", "org-1", 2);
        UUID layer = review.get(A, activePlatform).layerId();
        review.saveFixture(A, layer, null, new FixtureSpec("Typical", ANSWERS, null));
        ConfigurationVersionDetail draft = review.createDraft(A, platformTarget(), heavyOnTeamHealth());
        PreviewResult result = review.preview(A, draft.id());
        assertThat(result.summary().assessmentsUsed()).isFalse();
        assertThat(result.summary().assessments()).isZero();
        assertThat(result.items()).extracting(PreviewItem::kind).containsExactly("FIXTURE");
        assertThat(result.items().get(0).changes()).anyMatch(c -> c.startsWith("the overall score changes"));
        assertThat(jdbc.queryForObject("select count(*) from scoring_access_audit where action = 'CONFIGURATION_PREVIEW'", Integer.class)).isZero();
    }

    @Test
    void aDraftThatCannotBeResolvedPreviewsAsErrorsNotAnException() throws Exception {
        UUID layer = review.get(A, activePlatform).layerId();
        review.saveFixture(A, layer, null, new FixtureSpec("Typical", ANSWERS, null));
        ConfigurationVersionDetail draft = review.createDraft(A, platformTarget(), "{\"questions\": 5}");
        PreviewResult result = review.preview(A, draft.id());
        assertThat(result.items().get(0).after().status()).isEqualTo(ConfigurationEvaluator.ERROR);
        assertThat(result.fixtures().get(0).passed()).isFalse();
    }

    // ---------------- who can reach what ----------------

    @Test
    void aTenantManagerSeesItsOwnLayersAndNothingOfAnotherTenantOrThePlatform() throws Exception {
        Manager adminA = tenantManager("cm-a", "admin-a", "org-1", "org-2");
        Manager adminB = tenantManager("cm-b", "admin-b", "org-1");
        ConfigurationVersionDetail draft = review.createDraft(adminA, ConfigurationTarget.tenant(new TenantId("cm-a")), "{}");

        assertThat(review.layers(adminA)).extracting(ConfigurationLayer::name).containsExactly("Tenant override", "Organization override org-1", "Organization override org-2");
        assertThat(review.layers(adminB)).extracting(ConfigurationLayer::name).containsExactly("Tenant override", "Organization override org-1");
        assertThat(review.layers(A)).extracting(ConfigurationLayer::name).containsExactly("Platform default");

        // a version of another tenant, and a version of the platform, look missing
        assertCode(() -> review.get(adminB, draft.id()), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
        assertCode(() -> review.get(A, draft.id()), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
        assertCode(() -> review.get(adminA, activePlatform), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
        assertCode(() -> review.fixtures(adminB, draft.layerId()), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
        assertCode(() -> review.submit(adminB, draft.id()), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
        assertCode(() -> review.approve(adminB, draft.id(), "ok", null, null, null), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
        assertCode(() -> review.preview(adminB, draft.id()), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
    }

    @Test
    void aTenantManagerCannotManageThePlatformDefaultAnotherOrganizationOrAQuestionnaireLayer() throws Exception {
        Manager admin = tenantManager("cm-a", "admin-a", "org-1");
        assertCode(() -> review.createDraft(admin, platformTarget(), null), ScoringErrorCode.TENANT_ACCESS_DENIED);
        assertCode(() -> review.createDraft(admin, ConfigurationTarget.organization(new TenantId("cm-a"), new OrganizationId("org-9")), "{}"), ScoringErrorCode.TENANT_ACCESS_DENIED);
        assertCode(() -> review.createDraft(admin, ConfigurationTarget.tenant(new TenantId("cm-b")), "{}"), ScoringErrorCode.TENANT_ACCESS_DENIED);
        assertCode(() -> review.createDraft(admin, ConfigurationTarget.template(new TenantId("cm-a"), "q-1"), "{}"), ScoringErrorCode.TENANT_ACCESS_DENIED);
        // the platform manager manages the platform default only
        assertCode(() -> review.createDraft(A, ConfigurationTarget.tenant(new TenantId("cm-a")), "{}"), ScoringErrorCode.TENANT_ACCESS_DENIED);

        // a questionnaire layer, even with its id in hand, looks missing
        Caller caller = Caller.tenant(new TenantId("cm-a"), "admin-a");
        ConfigurationVersionView template = configurations.createDraft(caller, ConfigurationTarget.template(new TenantId("cm-a"), "q-1"), "q", "{}");
        assertCode(() -> review.get(admin, template.id()), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
        assertCode(() -> review.fixtures(admin, template.configurationId()), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
    }

    @Test
    void anOrganizationLayerIsReachableOnlyForAnOrganizationInTheToken() throws Exception {
        Manager both = tenantManager("cm-a", "admin-a", "org-1", "org-2");
        Manager one = tenantManager("cm-a", "admin-b", "org-1");
        ConfigurationVersionDetail orgTwo = review.createDraft(both, ConfigurationTarget.organization(new TenantId("cm-a"), new OrganizationId("org-2")), "{}");
        assertCode(() -> review.get(one, orgTwo.id()), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
        assertCode(() -> review.fixtures(one, orgTwo.layerId()), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
        assertThat(review.get(both, orgTwo.id()).scopeId()).isEqualTo("org-2");
    }

    @Test
    void aTenantApprovalChangesTheScoresOfThatTenantOnlyAndHistoryIsNotRewritten() throws Exception {
        assessment("cm-a", "org-1", 2);
        assessment("cm-b", "org-1", 2);
        BigDecimal historic = jdbc.queryForObject("select final_score from scoring_run where tenant_id = 'cm-a'", BigDecimal.class);
        Manager adminA = tenantManager("cm-a", "admin-a", "org-1");
        Manager approverA = tenantManager("cm-a", "owner-a", "org-1");
        ConfigurationVersionDetail draft = review.createDraft(adminA, ConfigurationTarget.tenant(new TenantId("cm-a")),
                "{\"overall\":{\"weights\":{\"TEAM_HEALTH\":0.7,\"DELIVERY_STABILITY\":0.1,\"TECHNICAL_RISK\":0.1,\"LEADERSHIP_LOAD\":0.1}}}");
        review.submit(adminA, draft.id());
        assertCode(() -> review.approve(adminA, draft.id(), "ok", null, null, null), ScoringErrorCode.CONFIGURATION_APPROVAL_REFUSED);
        review.approve(approverA, draft.id(), "Our teams weigh health most.", null, "Expert review, Oct 2026", null);

        assessment("cm-a", "org-1", 2);
        assessment("cm-b", "org-1", 2);
        List<BigDecimal> a = jdbc.queryForList("select final_score from scoring_run where tenant_id = 'cm-a' order by seq", BigDecimal.class);
        List<BigDecimal> b = jdbc.queryForList("select final_score from scoring_run where tenant_id = 'cm-b' order by seq", BigDecimal.class);
        assertThat(a.get(0)).isEqualByComparingTo(historic);
        assertThat(a.get(1)).isNotEqualByComparingTo(historic);
        assertThat(b.get(1)).isEqualByComparingTo(b.get(0));
        assertThat(Map.of("count", a.size())).containsEntry("count", 2);
    }
}
