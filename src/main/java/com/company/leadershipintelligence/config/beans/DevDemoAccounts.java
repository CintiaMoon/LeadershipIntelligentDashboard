package com.company.leadershipintelligence.config.beans;

import com.company.leadershipintelligence.repository.persistence.repository.UserStore;
import com.company.leadershipintelligence.service.identity.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Local development only (profile {@code dev}): creates a set of demo accounts, one for each kind of person the application knows,
 * already confirmed and already members of the demo companies, so a developer can sign in at once instead of registering and
 * reading an inbox. It replaces the persona picker of the external identity provider that the application no longer uses. Every
 * account has the same published password; the profile is never active in production, and an account that already exists is left
 * alone.
 *
 * <p>The email address of an account is {@code <name>@demo.test}; the respondents' addresses are the ones the demo roster uses.
 */
@Component
@Profile("dev")
@Order(20)
public class DevDemoAccounts implements ApplicationRunner {

    /** Published on purpose: a demo credential for the local stack only. */
    public static final String PASSWORD = "demo-password-1234";

    private static final Logger LOG = LoggerFactory.getLogger(DevDemoAccounts.class);

    /** What a demo account is: its name, its company (none for a platform administrator), its roles and the organizations it is limited to. */
    record Demo(String name, String display, boolean platformAdmin, String tenant, List<String> roles, List<String> organizations) {
        String email() {
            return name + "@demo.test";
        }
    }

    static final List<Demo> ACCOUNTS = List.of(
            new Demo("owner", "Demo owner", false, "demo-tenant", List.of("OWNER"), List.of("org-1", "org-2")),
            new Demo("admin", "Demo administrator", false, "demo-tenant", List.of("ADMIN"), List.of("org-1", "org-2")),
            new Demo("leader", "Demo engineering leader", false, "demo-tenant", List.of("ENGINEERING_LEADER"), List.of("org-1", "org-2")),
            new Demo("analyst", "Demo analyst", false, "demo-tenant", List.of("ANALYST"), List.of("org-1", "org-2")),
            new Demo("viewer", "Demo viewer", false, "demo-tenant", List.of("VIEWER"), List.of("org-1", "org-2")),
            new Demo("org1-viewer", "Demo viewer of one organization", false, "demo-tenant", List.of("VIEWER"), List.of("org-1")),
            new Demo("other-admin", "Administrator of another company", false, "other-tenant", List.of("ADMIN"), List.of("org-1")),
            new Demo("sandbox-admin", "Sandbox administrator", false, "demo-tenant", List.of("ADMIN"), List.of("org-sandbox")),
            new Demo("sandbox-leader", "Sandbox engineering leader", false, "demo-tenant", List.of("ENGINEERING_LEADER"), List.of("org-sandbox")),
            new Demo("assess-admin", "Assessments administrator", false, "demo-tenant", List.of("ADMIN"), List.of("org-assess")),
            new Demo("assess-owner", "Assessments owner", false, "demo-tenant", List.of("OWNER"), List.of("org-assess")),
            new Demo("respondent1", "Respondent 1", false, "demo-tenant", List.of("RESPONDENT"), List.of()),
            new Demo("respondent2", "Respondent 2", false, "demo-tenant", List.of("RESPONDENT"), List.of()),
            new Demo("respondent3", "Respondent 3", false, "demo-tenant", List.of("RESPONDENT"), List.of()),
            new Demo("respondent4", "Respondent 4", false, "demo-tenant", List.of("RESPONDENT"), List.of()),
            new Demo("respondent5", "Respondent 5", false, "demo-tenant", List.of("RESPONDENT"), List.of()),
            new Demo("respondent6", "Respondent 6", false, "demo-tenant", List.of("RESPONDENT"), List.of()),
            new Demo("platform-admin", "Platform administrator", true, null, List.of(), List.of()),
            new Demo("platform-admin-2", "Second platform administrator", true, null, List.of(), List.of()),
            // kept apart for the browser test of the account journey, which resets this account's password; the others keep the published one
            new Demo("platform-admin-3", "Third platform administrator", true, null, List.of(), List.of()));

    private final JdbcTemplate jdbc;
    private final UserStore users;
    private final PasswordHasher hasher;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public DevDemoAccounts(JdbcTemplate jdbc, UserStore users, PasswordHasher hasher, TransactionTemplate transaction, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.hasher = hasher;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        int created = 0;
        for (Demo demo : ACCOUNTS) {
            if (transaction.execute(status -> create(demo)) == Boolean.TRUE) {
                created++;
            }
        }
        if (created > 0) {
            LOG.info("created {} demo accounts for local development: <name>@demo.test, password {}", created, PASSWORD);
        }
    }

    private boolean create(Demo demo) {
        if (demo.tenant() != null) {
            jdbc.update("insert into tenant (id, name) values (?, ?) on conflict do nothing", demo.tenant(), demo.tenant());
        }
        UUID id = UUID.randomUUID();
        if (!users.insertVerified(id, demo.email(), demo.display(), hasher.hash(PASSWORD), demo.platformAdmin(), clock.instant())) {
            return false;
        }
        if (demo.tenant() != null) {
            UUID membership = UUID.randomUUID();
            jdbc.update("insert into company_membership (id, tenant_id, user_id, status, created_at, updated_at) values (?, ?, ?, 'ACTIVE', now(), now())",
                    membership, demo.tenant(), id);
            for (String role : demo.roles()) {
                jdbc.update("insert into company_membership_role (membership_id, role) values (?, ?)", membership, role);
            }
            for (String organization : demo.organizations()) {
                jdbc.update("insert into company_membership_organization (membership_id, organization_id) values (?, ?)", membership, organization);
            }
        }
        return true;
    }
}
