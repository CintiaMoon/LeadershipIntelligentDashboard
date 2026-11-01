package com.company.leadershipintelligence.service;

import com.company.leadershipintelligence.domain.enums.ConfigurationScope;
import com.company.leadershipintelligence.domain.enums.ConfigurationStatus;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.repository.configuration.CanonicalJson;
import com.company.leadershipintelligence.repository.configuration.ConfigurationSamples;
import com.company.leadershipintelligence.service.command.Approval;
import com.company.leadershipintelligence.service.command.Caller;
import com.company.leadershipintelligence.service.command.ConfigurationTarget;
import com.company.leadershipintelligence.service.query.ConfigurationVersionView;
import com.company.leadershipintelligence.service.query.ValidationResult;
import com.company.leadershipintelligence.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationLifecycleIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    ConfigurationService service;

    private final Caller platform = Caller.platform("ops@platform");
    private final String sample = ConfigurationSamples.sampleText();
    private final Approval approval = new Approval("approver@company", null);

    private static void assertCode(Runnable call, ScoringErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ScoringException.class, e -> assertThat(e.code()).isEqualTo(expected));
    }

    private ConfigurationVersionView activePlatform() {
        ConfigurationVersionView draft = service.createDraft(platform, ConfigurationTarget.platform(), "platform default", sample);
        assertThat(service.submitForValidation(platform, draft.id()).valid()).isTrue();
        return service.activate(platform, draft.id(), approval);
    }

    private List<String> auditActions(UUID versionId) {
        return jdbc.queryForList("select action from scoring_configuration_audit where version_id = ? order by id", String.class, versionId);
    }

    @Test
    void aVersionMovesThroughTheWholeLifecycleWithAnAuditTrail() {
        ConfigurationVersionView draft = service.createDraft(platform, ConfigurationTarget.platform(), "platform default", sample);
        assertThat(draft.status()).isEqualTo(ConfigurationStatus.DRAFT);
        assertThat(draft.version()).isEqualTo(1);
        assertThat(draft.scope()).isEqualTo(ConfigurationScope.PLATFORM_DEFAULT);
        assertThat(draft.configHash()).isEqualTo(CanonicalJson.hash(ConfigurationSamples.sampleDocument()));

        assertThat(service.submitForValidation(platform, draft.id()).valid()).isTrue();
        assertThat(service.get(platform, draft.id()).status()).isEqualTo(ConfigurationStatus.VALIDATING);

        ConfigurationVersionView active = service.activate(platform, draft.id(), approval);
        assertThat(active.status()).isEqualTo(ConfigurationStatus.ACTIVE);
        assertThat(active.approvedBy()).isEqualTo("approver@company");
        assertThat(active.approvedAt()).isEqualTo(clock.instant());
        assertThat(active.effectiveFrom()).isEqualTo(clock.instant());

        assertThat(service.pause(platform, draft.id()).status()).isEqualTo(ConfigurationStatus.PAUSED);
        assertThat(service.resume(platform, draft.id()).status()).isEqualTo(ConfigurationStatus.ACTIVE);
        assertThat(service.archive(platform, draft.id()).status()).isEqualTo(ConfigurationStatus.ARCHIVED);

        assertThat(auditActions(draft.id())).containsExactly("CREATED", "VALIDATED", "ACTIVATED", "PAUSED", "RESUMED", "ARCHIVED");
        assertThat(jdbc.queryForList("select distinct actor from scoring_configuration_audit", String.class))
                .containsExactlyInAnyOrder("ops@platform");
    }

    @Test
    void theAuditTrailNeverStoresTheConfigurationContent() {
        activePlatform();
        String details = String.join(" ", jdbc.queryForList("select coalesce(detail::text, '') from scoring_configuration_audit", String.class));
        assertThat(details).doesNotContain("TEAM_SUSTAINABILITY").doesNotContain("keywords").doesNotContain("Q01");
    }

    @Test
    void illegalTransitionsAreRejected() {
        ConfigurationVersionView draft = service.createDraft(platform, ConfigurationTarget.platform(), "p", sample);
        assertCode(() -> service.activate(platform, draft.id(), approval), ScoringErrorCode.INVALID_CONFIGURATION_VERSION);
        assertCode(() -> service.pause(platform, draft.id()), ScoringErrorCode.INVALID_CONFIGURATION_VERSION);
        assertCode(() -> service.archive(platform, draft.id()), ScoringErrorCode.INVALID_CONFIGURATION_VERSION);
        service.submitForValidation(platform, draft.id());
        assertCode(() -> service.submitForValidation(platform, draft.id()), ScoringErrorCode.INVALID_CONFIGURATION_VERSION);
        service.activate(platform, draft.id(), approval);
        assertCode(() -> service.activate(platform, draft.id(), approval), ScoringErrorCode.INVALID_CONFIGURATION_VERSION);
        service.archive(platform, draft.id());
        assertCode(() -> service.resume(platform, draft.id()), ScoringErrorCode.INVALID_CONFIGURATION_VERSION);
        assertCode(() -> service.pause(platform, draft.id()), ScoringErrorCode.INVALID_CONFIGURATION_VERSION);
    }

    @Test
    void validationFailureReturnsToDraftAndKeepsTheReason() {
        String broken = sample.replace("\"tolerance\": 0.0001", "\"tolerance\": 0.0001, \"surprise\": 1");
        ConfigurationVersionView draft = service.createDraft(platform, ConfigurationTarget.platform(), "p", broken);
        ValidationResult result = service.submitForValidation(platform, draft.id());
        assertThat(result.valid()).isFalse();
        assertThat(result.problems().get(0)).contains("surprise");
        assertThat(service.get(platform, draft.id()).status()).isEqualTo(ConfigurationStatus.DRAFT);
        assertThat(auditActions(draft.id())).containsExactly("CREATED", "VALIDATION_FAILED");
        assertThat(jdbc.queryForObject("select detail->>'problem' from scoring_configuration_audit where action = 'VALIDATION_FAILED'", String.class))
                .contains("surprise");
        // fix the draft and it passes
        service.updateDraft(platform, draft.id(), sample);
        assertThat(service.submitForValidation(platform, draft.id()).valid()).isTrue();
    }

    @Test
    void activationRevalidatesAndAFailedActivationIsAuditedAndReturnsToDraft() {
        ConfigurationVersionView draft = service.createDraft(platform, ConfigurationTarget.platform(), "p", sample);
        service.submitForValidation(platform, draft.id());
        // approval metadata is required
        assertCode(() -> service.activate(platform, draft.id(), new Approval(" ", null)), ScoringErrorCode.CONFIGURATION_ACTIVATION_FAILED);
        assertThat(service.get(platform, draft.id()).status()).isEqualTo(ConfigurationStatus.DRAFT);
        assertThat(auditActions(draft.id())).containsExactly("CREATED", "VALIDATED", "ACTIVATION_FAILED");
        // a second time it can pass
        service.submitForValidation(platform, draft.id());
        assertThat(service.activate(platform, draft.id(), approval).status()).isEqualTo(ConfigurationStatus.ACTIVE);
    }

    @Test
    void anActiveVersionCannotBeEditedByTheServiceOrByTheDatabase() {
        ConfigurationVersionView active = activePlatform();
        assertCode(() -> service.updateDraft(platform, active.id(), "{}"), ScoringErrorCode.INVALID_CONFIGURATION_VERSION);
        assertThatThrownBy(() -> jdbc.update("update scoring_configuration_version set payload = '{}'::jsonb where id = ?", active.id()))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("immutable");
        assertThat(service.get(platform, active.id()).configHash()).isEqualTo(active.configHash());
    }

    @Test
    void aNewActiveVersionArchivesThePreviousOneAndHistoryIsKept() {
        ConfigurationVersionView first = activePlatform();
        ConfigurationVersionView second = service.createDraft(platform, ConfigurationTarget.platform(), "p",
                sample.replace("\"tolerance\": 0.0001", "\"tolerance\": 0.0002"));
        assertThat(second.version()).isEqualTo(2);
        service.submitForValidation(platform, second.id());
        service.activate(platform, second.id(), approval);

        assertThat(service.get(platform, first.id()).status()).isEqualTo(ConfigurationStatus.ARCHIVED);
        assertThat(service.get(platform, second.id()).status()).isEqualTo(ConfigurationStatus.ACTIVE);
        assertThat(auditActions(first.id())).containsExactly("CREATED", "VALIDATED", "ACTIVATED", "ARCHIVED");
        // the old version's content is still there for reproducing old runs
        assertThat(service.get(platform, first.id()).configHash()).isEqualTo(first.configHash());
        assertThat(service.history(platform, ConfigurationTarget.platform())).extracting(ConfigurationVersionView::version).containsExactly(2, 1);
        assertThat(jdbc.queryForObject("select count(*) from scoring_configuration_version where status = 'ACTIVE'", Integer.class)).isEqualTo(1);
    }

    @Test
    void aPausedVersionCannotResumeWhileAnotherIsActive() {
        ConfigurationVersionView first = activePlatform();
        service.pause(platform, first.id());
        ConfigurationVersionView second = service.createDraft(platform, ConfigurationTarget.platform(), "p", sample);
        service.submitForValidation(platform, second.id());
        service.activate(platform, second.id(), approval);
        assertCode(() -> service.resume(platform, first.id()), ScoringErrorCode.INVALID_CONFIGURATION_VERSION);
    }

    @Test
    void theDatabaseAllowsOnlyOneActiveVersionPerConfiguration() {
        ConfigurationVersionView first = activePlatform();
        UUID slot = jdbc.queryForObject("select configuration_id from scoring_configuration_version where id = ?", UUID.class, first.id());
        assertThatThrownBy(() -> jdbc.update("insert into scoring_configuration_version (id, configuration_id, tenant_id, version, status, payload, config_hash, created_by, effective_from) "
                + "values (?, ?, null, 9, 'ACTIVE', '{}'::jsonb, 'h', 't', now())", UUID.randomUUID(), slot))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void futureEffectiveDatesAreStoredAndThePayloadIsIdenticalWhateverItsFormatting() {
        ConfigurationVersionView draft = service.createDraft(platform, ConfigurationTarget.platform(), "p", sample);
        service.submitForValidation(platform, draft.id());
        Instant later = clock.instant().plusSeconds(3600);
        ConfigurationVersionView active = service.activate(platform, draft.id(), new Approval("approver", later));
        assertThat(active.effectiveFrom()).isEqualTo(later);
        ConfigurationVersionView reformatted = service.createDraft(platform, ConfigurationTarget.platform(), "p",
                ConfigurationSamples.sampleDocument().toPrettyString());
        assertThat(reformatted.configHash()).isEqualTo(draft.configHash());
    }

    // ---- tenant isolation (specv3.md Section 25.6) ----

    @Test
    void aTenantCannotSeeChangeOrActivateAnotherTenantsVersion() {
        tenant("iso-a");
        tenant("iso-b");
        Caller a = Caller.tenant(new TenantId("iso-a"), "a@iso");
        Caller b = Caller.tenant(new TenantId("iso-b"), "b@iso");
        ConfigurationVersionView aDraft = service.createDraft(a, ConfigurationTarget.tenant(new TenantId("iso-a")), "a override", "{\"overall\":{\"tolerance\":0.0003}}");

        assertCode(() -> service.get(b, aDraft.id()), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
        assertCode(() -> service.updateDraft(b, aDraft.id(), "{}"), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
        assertCode(() -> service.submitForValidation(b, aDraft.id()), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
        assertCode(() -> service.activate(b, aDraft.id(), approval), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
        assertCode(() -> service.pause(b, aDraft.id()), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
        assertCode(() -> service.archive(b, aDraft.id()), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
        assertThat(service.history(b, ConfigurationTarget.tenant(new TenantId("iso-b")))).isEmpty();
        assertThat(service.get(a, aDraft.id()).status()).isEqualTo(ConfigurationStatus.DRAFT);
    }

    @Test
    void callersCannotManageSlotsOutsideTheirOwnScope() {
        tenant("iso-c");
        tenant("iso-d");
        Caller c = Caller.tenant(new TenantId("iso-c"), "c@iso");
        assertCode(() -> service.createDraft(c, ConfigurationTarget.tenant(new TenantId("iso-d")), "x", "{}"), ScoringErrorCode.TENANT_ACCESS_DENIED);
        assertCode(() -> service.createDraft(c, ConfigurationTarget.platform(), "x", "{}"), ScoringErrorCode.TENANT_ACCESS_DENIED);
        assertCode(() -> service.createDraft(platform, ConfigurationTarget.tenant(new TenantId("iso-c")), "x", "{}"), ScoringErrorCode.TENANT_ACCESS_DENIED);
    }

    @Test
    void platformDefaultsAndTenantVersionsAreInvisibleToTheWrongCaller() {
        tenant("iso-e");
        Caller e = Caller.tenant(new TenantId("iso-e"), "e@iso");
        ConfigurationVersionView platformVersion = service.createDraft(platform, ConfigurationTarget.platform(), "p", sample);
        ConfigurationVersionView tenantVersion = service.createDraft(e, ConfigurationTarget.tenant(new TenantId("iso-e")), "t", "{}");
        assertCode(() -> service.get(e, platformVersion.id()), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
        assertCode(() -> service.get(platform, tenantVersion.id()), ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND);
    }

    @Test
    void twoTenantsCanHoldTheSameSlotIndependently() {
        tenant("iso-f");
        tenant("iso-g");
        ConfigurationVersionView f = service.createDraft(Caller.tenant(new TenantId("iso-f"), "f"), ConfigurationTarget.tenant(new TenantId("iso-f")), "t", "{}");
        ConfigurationVersionView g = service.createDraft(Caller.tenant(new TenantId("iso-g"), "g"), ConfigurationTarget.tenant(new TenantId("iso-g")), "t", "{}");
        assertThat(f.version()).isEqualTo(1);
        assertThat(g.version()).isEqualTo(1);
        assertThat(f.configurationId()).isNotEqualTo(g.configurationId());
    }
}
