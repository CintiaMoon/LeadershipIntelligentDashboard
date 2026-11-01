package com.company.leadershipintelligence.config.beans;

import com.company.leadershipintelligence.service.ConfigurationService;
import com.company.leadershipintelligence.service.command.Approval;
import com.company.leadershipintelligence.service.command.Caller;
import com.company.leadershipintelligence.service.command.ConfigurationTarget;
import com.company.leadershipintelligence.service.query.ConfigurationVersionView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Local development only (profile {@code dev}): creates two demo tenants and activates the ILLUSTRATIVE sample
 * platform configuration (decisions D4, D29) when none exists. Never active in production; the sample thresholds
 * are unvalidated.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DevDataSeeder.class);

    private final JdbcTemplate jdbc;
    private final ConfigurationService configurations;

    public DevDataSeeder(JdbcTemplate jdbc, ConfigurationService configurations) {
        this.jdbc = jdbc;
        this.configurations = configurations;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        for (String tenant : new String[]{"demo-tenant", "other-tenant"}) {
            jdbc.update("insert into tenant (id, name) values (?, ?) on conflict do nothing", tenant, tenant);
        }
        Caller platform = Caller.platform("dev-seeder");
        if (!configurations.history(platform, ConfigurationTarget.platform()).isEmpty()) {
            return;
        }
        String payload = new String(new ClassPathResource("dev/sample-platform-default.json").getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        ConfigurationVersionView draft = configurations.createDraft(platform, ConfigurationTarget.platform(), "sample (illustrative)", payload);
        configurations.submitForValidation(platform, draft.id());
        configurations.activate(platform, draft.id(), new Approval("dev-seeder", null));
        LOG.info("seeded the illustrative sample platform configuration for local development");
    }
}
