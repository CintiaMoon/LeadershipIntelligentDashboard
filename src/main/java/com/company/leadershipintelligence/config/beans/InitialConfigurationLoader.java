package com.company.leadershipintelligence.config.beans;

import com.company.leadershipintelligence.config.properties.InitialConfigurationProperties;
import com.company.leadershipintelligence.domain.enums.ConfigurationStatus;
import com.company.leadershipintelligence.service.ConfigurationService;
import com.company.leadershipintelligence.service.command.Approval;
import com.company.leadershipintelligence.service.command.Caller;
import com.company.leadershipintelligence.service.configuration.ConfigurationReviewService;
import com.company.leadershipintelligence.service.configuration.FixtureSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.leadershipintelligence.service.command.ConfigurationTarget;
import com.company.leadershipintelligence.service.query.ConfigurationVersionView;
import com.company.leadershipintelligence.service.query.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Puts the platform scoring configuration in place from a JSON file when the application starts (specv3.md Section 23.5:
 * draft, validate, activate with an approval record). The file is the initial configuration bundled with the application,
 * or one named by {@code lis.configuration.initial.location}. Nothing is invented here: the document goes through the same
 * validation as any configuration and a document that fails stops the application, so a broken file can never leave it
 * running without rules. An active version is never edited; a changed file only becomes a new version when the mode is
 * {@code SYNC}.
 *
 * <p>With the {@code dev} profile the application uses the illustrative sample of {@link DevDataSeeder} instead.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class InitialConfigurationLoader implements ApplicationRunner {

    /** What a load did. */
    public enum Result {
        /** No platform configuration was active, so the file was validated and activated. */
        ACTIVATED,
        /** The active version is exactly the file. */
        UP_TO_DATE,
        /** A different version is active and was left alone (mode IF_MISSING). */
        DIFFERS_NOT_CHANGED,
        /** The file differed and became a new active version; the previous one was archived (mode SYNC). */
        REPLACED
    }

    private static final Logger LOG = LoggerFactory.getLogger(InitialConfigurationLoader.class);
    private static final String ACTOR = "initial-configuration";

    private final ConfigurationService configurations;
    private final ResourceLoader resources;
    private final InitialConfigurationProperties properties;
    private final Environment environment;
    private final ConfigurationReviewService review;
    private final ObjectMapper json;

    public InitialConfigurationLoader(ConfigurationService configurations, ResourceLoader resources,
                                      InitialConfigurationProperties properties, Environment environment,
                                      ConfigurationReviewService review, ObjectMapper json) {
        this.configurations = configurations;
        this.resources = resources;
        this.properties = properties;
        this.environment = environment;
        this.review = review;
        this.json = json;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            LOG.info("the initial platform configuration is not read (lis.configuration.initial.enabled=false)");
            return;
        }
        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            LOG.info("the dev profile uses its own illustrative sample configuration; {} is not read", properties.location());
            return;
        }
        load();
    }

    /**
     * Reads the file and brings the platform configuration in line with it. Fails with a clear message if the file is unusable. When
     * the platform default has no fixtures yet and a file of starter fixtures sits beside the configuration (the same name with
     * {@code .fixtures.json}), they are put in place (decision D56).
     */
    public Result load() {
        Result result = loadDocument();
        addStarterFixtures();
        return result;
    }

    private void addStarterFixtures() {
        String location = properties.location();
        if (!location.endsWith(".json")) {
            return;
        }
        Resource resource = resources.getResource(location.substring(0, location.length() - ".json".length()) + ".fixtures.json");
        if (!resource.exists()) {
            return;
        }
        List<ConfigurationVersionView> history = configurations.history(Caller.platform(ACTOR), ConfigurationTarget.platform());
        if (history.isEmpty()) {
            return;
        }
        try (var in = resource.getInputStream()) {
            List<FixtureSpec> fixtures = json.readValue(in, new com.fasterxml.jackson.core.type.TypeReference<List<FixtureSpec>>() {
            });
            int added = review.addStarterFixtures(history.get(0).configurationId(), ACTOR, fixtures);
            if (added > 0) {
                LOG.info("added {} starter fixtures to the platform configuration", added);
            }
        } catch (IOException e) {
            throw new IllegalStateException("the starter fixtures of " + location + " could not be read: " + e.getMessage(), e);
        }
    }

    private Result loadDocument() {
        String payload = read();
        Caller platform = Caller.platform(ACTOR);
        ConfigurationTarget target = ConfigurationTarget.platform();
        String hash;
        try {
            hash = configurations.hashOf(payload);
        } catch (RuntimeException e) {
            throw new IllegalStateException("the platform configuration " + properties.location() + " is not a valid JSON document: " + e.getMessage(), e);
        }
        List<ConfigurationVersionView> history = configurations.history(platform, target);
        Optional<ConfigurationVersionView> active = history.stream().filter(v -> v.status() == ConfigurationStatus.ACTIVE).findFirst();
        if (active.isPresent()) {
            ConfigurationVersionView current = active.get();
            if (hash.equals(current.configHash())) {
                LOG.info("the active platform configuration (version {}) is {}", current.version(), properties.location());
                return Result.UP_TO_DATE;
            }
            if (properties.mode() == InitialConfigurationProperties.Mode.IF_MISSING) {
                LOG.warn("the active platform configuration (version {}) differs from {} and was not changed; set "
                        + "lis.configuration.initial.mode=SYNC to activate the file as a new version", current.version(), properties.location());
                return Result.DIFFERS_NOT_CHANGED;
            }
        }
        ConfigurationVersionView created = configurations.createDraft(platform, target, "platform default (" + properties.location() + ")", payload);
        ValidationResult validation = configurations.submitForValidation(platform, created.id());
        if (!validation.valid()) {
            throw new IllegalStateException("the platform configuration " + properties.location() + " is not valid: " + String.join("; ", validation.problems()));
        }
        ConfigurationVersionView activated = configurations.activate(platform, created.id(), new Approval(ACTOR, null));
        LOG.info("activated version {} of the platform configuration from {}", activated.version(), properties.location());
        return active.isPresent() ? Result.REPLACED : Result.ACTIVATED;
    }

    private String read() {
        Resource resource = resources.getResource(properties.location());
        if (!resource.exists()) {
            throw new IllegalStateException("the platform configuration " + properties.location() + " does not exist; set lis.configuration.initial.location "
                    + "to a classpath: or file: resource, or set lis.configuration.initial.enabled=false and load a configuration another way");
        }
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("the platform configuration " + properties.location() + " could not be read: " + e.getMessage(), e);
        }
    }
}
