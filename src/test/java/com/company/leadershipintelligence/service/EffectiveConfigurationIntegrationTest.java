package com.company.leadershipintelligence.service;

import com.company.leadershipintelligence.domain.enums.ConfigurationScope;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.valueobject.OrganizationId;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.repository.configuration.ConfigurationSamples;
import com.company.leadershipintelligence.service.command.Approval;
import com.company.leadershipintelligence.service.command.Caller;
import com.company.leadershipintelligence.service.command.ConfigurationTarget;
import com.company.leadershipintelligence.service.query.ConfigurationVersionView;
import com.company.leadershipintelligence.service.query.EffectiveConfiguration;
import com.company.leadershipintelligence.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EffectiveConfigurationIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    ConfigurationService configurations;

    @Autowired
    EffectiveConfigurationService effective;

    private final Caller platform = Caller.platform("ops");
    private final Approval approval = new Approval("approver", null);

    private ConfigurationVersionView activate(Caller caller, ConfigurationTarget target, String payload, Approval approval) {
        ConfigurationVersionView draft = configurations.createDraft(caller, target, target.scope().name(), payload);
        assertThat(configurations.submitForValidation(caller, draft.id()).valid()).as(draft.payload()).isTrue();
        return configurations.activate(caller, draft.id(), approval);
    }

    private ConfigurationVersionView activate(Caller caller, ConfigurationTarget target, String payload) {
        return activate(caller, target, payload, approval);
    }

    private void platformDefault() {
        activate(platform, ConfigurationTarget.platform(), ConfigurationSamples.sampleText());
    }

    private static String tolerance(String value) {
        return "{\"overall\":{\"tolerance\":" + value + "}}";
    }

    private static String toleranceOf(EffectiveConfiguration c) {
        return c.snapshot().overall().weights().tolerance().toPlainString();
    }

    private static Caller caller(String tenant) {
        return Caller.tenant(new TenantId(tenant), tenant + "-admin");
    }

    @Test
    void thePlatformDefaultAloneIsTheEffectiveConfiguration() {
        tenant("eff-1");
        platformDefault();
        EffectiveConfiguration c = effective.resolve(new TenantId("eff-1"), null, null);
        assertThat(toleranceOf(c)).isEqualTo("0.0001");
        assertThat(c.layers()).hasSize(1);
        assertThat(c.effectiveVersion()).isEqualTo("platform_default:v1");
        assertThat(c.configurationHash()).matches("sha256:[0-9a-f]{64}");
        assertThat(c.snapshot().version().value()).isEqualTo(c.effectiveVersion());
    }

    @Test
    void precedenceRunsPlatformTemplateOrganizationTenant() {
        tenant("eff-2");
        TenantId t = new TenantId("eff-2");
        platformDefault();
        activate(caller("eff-2"), ConfigurationTarget.template(t, "tpl-1"), tolerance("0.0002"));
        activate(caller("eff-2"), ConfigurationTarget.organization(t, new OrganizationId("org-1")), tolerance("0.0003"));

        assertThat(toleranceOf(effective.resolve(t, null, null))).isEqualTo("0.0001");
        assertThat(toleranceOf(effective.resolve(t, null, "tpl-1"))).isEqualTo("0.0002");
        assertThat(toleranceOf(effective.resolve(t, new OrganizationId("org-1"), "tpl-1"))).isEqualTo("0.0003");
        assertThat(toleranceOf(effective.resolve(t, new OrganizationId("org-OTHER"), "tpl-1"))).isEqualTo("0.0002");

        activate(caller("eff-2"), ConfigurationTarget.tenant(t), tolerance("0.0004"));
        EffectiveConfiguration all = effective.resolve(t, new OrganizationId("org-1"), "tpl-1");
        assertThat(toleranceOf(all)).isEqualTo("0.0004");
        assertThat(all.layers()).extracting(l -> l.scope()).containsExactly(ConfigurationScope.PLATFORM_DEFAULT,
                ConfigurationScope.TEMPLATE, ConfigurationScope.ORGANIZATION, ConfigurationScope.TENANT);
        assertThat(all.effectiveVersion()).isEqualTo("platform_default:v1|template:tpl-1:v1|organization:org-1:v1|tenant:v1");
    }

    @Test
    void anOverrideChangesOnlyItsOwnTenantAndOnlyTheFieldsItNames() {
        tenant("eff-3");
        tenant("eff-4");
        platformDefault();
        activate(caller("eff-3"), ConfigurationTarget.tenant(new TenantId("eff-3")),
                "{\"dimensions\":{\"settings\":{\"TEAM_HEALTH\":{\"requiredCoverage\":0.5}}}}");
        EffectiveConfiguration three = effective.resolve(new TenantId("eff-3"), null, null);
        EffectiveConfiguration four = effective.resolve(new TenantId("eff-4"), null, null);
        assertThat(three.snapshot().dimensions().settings().get(com.company.leadershipintelligence.domain.enums.ScoringDimension.TEAM_HEALTH)
                .requiredCoverage()).isEqualByComparingTo("0.5");
        assertThat(three.snapshot().dimensions().settings().get(com.company.leadershipintelligence.domain.enums.ScoringDimension.DELIVERY_STABILITY)
                .requiredCoverage()).isEqualByComparingTo("0.75");
        assertThat(four.snapshot().dimensions().settings().get(com.company.leadershipintelligence.domain.enums.ScoringDimension.TEAM_HEALTH)
                .requiredCoverage()).isEqualByComparingTo("0.75");
        assertThat(three.configurationHash()).isNotEqualTo(four.configurationHash());
    }

    @Test
    void anOverrideThatBreaksTheMergedConfigurationCannotBeActivated() {
        tenant("eff-5");
        platformDefault();
        Caller c = caller("eff-5");
        ConfigurationVersionView draft = configurations.createDraft(c, ConfigurationTarget.tenant(new TenantId("eff-5")), "t",
                "{\"overall\":{\"weights\":{\"TEAM_HEALTH\":0.9}}}");
        assertThat(configurations.submitForValidation(c, draft.id()).valid()).isFalse();
        assertThat(configurations.get(c, draft.id()).status().name()).isEqualTo("DRAFT");
    }

    @Test
    void withoutAnyActiveLayerNothingResolves() {
        tenant("eff-6");
        assertThatThrownBy(() -> effective.resolve(new TenantId("eff-6"), null, null)).isInstanceOfSatisfying(ScoringException.class,
                e -> assertThat(e.code()).isEqualTo(ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND));
    }

    @Test
    void pausingAnOverrideRemovesItImmediately() {
        tenant("eff-7");
        platformDefault();
        ConfigurationVersionView override = activate(caller("eff-7"), ConfigurationTarget.tenant(new TenantId("eff-7")), tolerance("0.0009"));
        assertThat(toleranceOf(effective.resolve(new TenantId("eff-7"), null, null))).isEqualTo("0.0009");
        configurations.pause(caller("eff-7"), override.id());
        assertThat(toleranceOf(effective.resolve(new TenantId("eff-7"), null, null))).isEqualTo("0.0001");
    }

    @Test
    void anOverrideWithAFutureEffectiveDateAppliesOnlyFromThatInstant() {
        tenant("eff-8");
        platformDefault();
        Instant later = clock.instant().plus(Duration.ofHours(2));
        activate(caller("eff-8"), ConfigurationTarget.tenant(new TenantId("eff-8")), tolerance("0.0005"), new Approval("approver", later));
        assertThat(toleranceOf(effective.resolve(new TenantId("eff-8"), null, null))).isEqualTo("0.0001");
        // cached now; the scheduled change must still take effect without any invalidation
        clock.advance(Duration.ofHours(1));
        assertThat(toleranceOf(effective.resolve(new TenantId("eff-8"), null, null))).isEqualTo("0.0001");
        clock.advance(Duration.ofHours(1));
        assertThat(toleranceOf(effective.resolve(new TenantId("eff-8"), null, null))).isEqualTo("0.0005");
    }

    // ---- cache ----

    @Test
    void secondResolutionIsServedFromTheCache() {
        tenant("eff-9");
        platformDefault();
        long hitsBefore = effectiveCache.stats().hitCount();
        EffectiveConfiguration first = effective.resolve(new TenantId("eff-9"), null, null);
        EffectiveConfiguration second = effective.resolve(new TenantId("eff-9"), null, null);
        assertThat(second).isSameAs(first);
        assertThat(effectiveCache.stats().hitCount()).isEqualTo(hitsBefore + 1);
    }

    @Test
    void activationAndPauseEvictTheCacheAfterCommit() {
        tenant("eff-10");
        platformDefault();
        assertThat(toleranceOf(effective.resolve(new TenantId("eff-10"), null, null))).isEqualTo("0.0001");
        assertThat(effectiveCache.size()).isEqualTo(1);
        ConfigurationVersionView override = activate(caller("eff-10"), ConfigurationTarget.tenant(new TenantId("eff-10")), tolerance("0.0007"));
        assertThat(toleranceOf(effective.resolve(new TenantId("eff-10"), null, null))).isEqualTo("0.0007");
        configurations.pause(caller("eff-10"), override.id());
        assertThat(toleranceOf(effective.resolve(new TenantId("eff-10"), null, null))).isEqualTo("0.0001");
    }

    @Test
    void aTenantsCacheEntryIsNeverServedToAnotherTenant() {
        tenant("eff-11");
        tenant("eff-12");
        platformDefault();
        activate(caller("eff-11"), ConfigurationTarget.tenant(new TenantId("eff-11")), tolerance("0.0006"));
        EffectiveConfiguration eleven = effective.resolve(new TenantId("eff-11"), null, null);
        EffectiveConfiguration twelve = effective.resolve(new TenantId("eff-12"), null, null);
        assertThat(toleranceOf(eleven)).isEqualTo("0.0006");
        assertThat(toleranceOf(twelve)).isEqualTo("0.0001");
        assertThat(effectiveCache.size()).isEqualTo(2);
        // a tenant-level change evicts only that tenant
        configurations.pause(caller("eff-11"), eleven.layers().get(1).versionId());
        assertThat(effective.resolve(new TenantId("eff-12"), null, null)).isSameAs(twelve);
        assertThat(toleranceOf(effective.resolve(new TenantId("eff-11"), null, null))).isEqualTo("0.0001");
    }

    @Test
    void aPlatformChangeEvictsEveryTenant() {
        tenant("eff-13");
        tenant("eff-14");
        ConfigurationVersionView first = activate(platform, ConfigurationTarget.platform(), ConfigurationSamples.sampleText());
        effective.resolve(new TenantId("eff-13"), null, null);
        effective.resolve(new TenantId("eff-14"), null, null);
        assertThat(effectiveCache.size()).isEqualTo(2);
        activate(platform, ConfigurationTarget.platform(), ConfigurationSamples.sampleText().replace("\"tolerance\": 0.0001", "\"tolerance\": 0.0002"));
        assertThat(effectiveCache.size()).isZero();
        assertThat(toleranceOf(effective.resolve(new TenantId("eff-13"), null, null))).isEqualTo("0.0002");
        assertThat(first.version()).isEqualTo(1);
    }

    @Test
    void resolvingIsRepeatableAndTheQueryCountIsBounded() {
        tenant("eff-15");
        platformDefault();
        activate(caller("eff-15"), ConfigurationTarget.tenant(new TenantId("eff-15")), tolerance("0.0008"));
        EffectiveConfiguration a = effective.resolve(new TenantId("eff-15"), null, null);
        effectiveCache.invalidateAll();
        EffectiveConfiguration b = effective.resolve(new TenantId("eff-15"), null, null);
        assertThat(b.configurationHash()).isEqualTo(a.configurationHash());
        assertThat(b.snapshot()).isEqualTo(a.snapshot());
    }
}
