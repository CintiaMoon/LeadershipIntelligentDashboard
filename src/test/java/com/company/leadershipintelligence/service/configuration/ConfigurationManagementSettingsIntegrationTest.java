package com.company.leadershipintelligence.service.configuration;

import com.company.leadershipintelligence.domain.enums.ConfigurationStatus;
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
import com.company.leadershipintelligence.service.query.ConfigurationViews.ConfigurationVersionDetail;
import com.company.leadershipintelligence.service.query.ConfigurationViews.PreviewResult;
import com.company.leadershipintelligence.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The two settings of configuration management that can be switched (decisions D53 and D55). Each needs its own application context. */
class ConfigurationManagementSettingsIntegrationTest {

    abstract static class Base extends PostgresIntegrationTest {

        @Autowired
        ConfigurationReviewService review;

        @Autowired
        ConfigurationService configurations;

        @Autowired
        ScoreAssessmentService scoring;

        @BeforeEach
        void platformDefault() {
            tenant("cs-a");
            Caller ops = Caller.platform("ops");
            ConfigurationVersionView draft = configurations.createDraft(ops, ConfigurationTarget.platform(), "platform", ConfigurationSamples.sampleText());
            configurations.submitForValidation(ops, draft.id());
            configurations.activate(ops, draft.id(), new Approval("ops-approver", null));
        }
    }

    @Nested
    @TestPropertySource(properties = "lis.configuration.approval.require-second-person=false")
    class WithTheSecondPersonRuleSwitchedOff extends Base {

        @Test
        void theSamePersonMayApproveWhatTheySubmittedAndEverythingElseStillApplies() {
            Manager only = Manager.platform("only-admin");
            ConfigurationVersionDetail draft = review.createDraft(only, ConfigurationTarget.platform(), null);
            review.submit(only, draft.id());
            ConfigurationVersionDetail approved = review.approve(only, draft.id(), "Single administrator.", null, null, null);
            assertThat(approved.status()).isEqualTo(ConfigurationStatus.ACTIVE);
            assertThat(approved.review().submittedBy()).isEqualTo("only-admin");
            assertThat(approved.review().approvedBy()).isEqualTo("only-admin");
            // the note is still required and the record is still kept
            assertThat(approved.review().approvalNote()).isEqualTo("Single administrator.");
        }
    }

    @Nested
    @TestPropertySource(properties = "lis.configuration.preview.use-recent-assessments=false")
    class WithPreviewOfRecentAssessmentsSwitchedOff extends Base {

        @Test
        void aTenantPreviewUsesFixturesOnlyAndReadsNoAssessmentAndRecordsNoReadOfOne() {
            scoring.calculate(new CalculateCommand(new TenantId("cs-a"), new OrganizationId("org-1"), "tpl-1", 1, "COMPLETED", Instant.parse("2026-11-01T09:00:00Z"),
                    List.of(new ScoringInput("Q01", 2), new ScoringInput("Q02", "OFTEN"), new ScoringInput("Q03", true), new ScoringInput("Q04", "x"),
                            new ScoringInput("Q05", 80)), null));
            Manager admin = new Manager(Caller.tenant(new TenantId("cs-a"), "admin-1"), OrganizationScope.of(List.of("org-1")));
            ConfigurationVersionDetail draft = review.createDraft(admin, ConfigurationTarget.tenant(new TenantId("cs-a")), "{}");
            PreviewResult result = review.preview(admin, draft.id());
            assertThat(result.summary().assessmentsUsed()).isFalse();
            assertThat(result.summary().assessments()).isZero();
            assertThat(result.items()).isEmpty();
            assertThat(jdbc.queryForObject("select count(*) from scoring_access_audit where action = 'CONFIGURATION_PREVIEW'", Integer.class)).isZero();
        }
    }
}
