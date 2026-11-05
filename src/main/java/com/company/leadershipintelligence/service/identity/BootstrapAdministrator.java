package com.company.leadershipintelligence.service.identity;

import com.company.leadershipintelligence.config.properties.IdentityProperties;
import com.company.leadershipintelligence.domain.identity.AccountStatus;
import com.company.leadershipintelligence.domain.identity.EmailAddress;
import com.company.leadershipintelligence.domain.identity.TokenPurpose;
import com.company.leadershipintelligence.repository.persistence.entity.UserRow;
import com.company.leadershipintelligence.repository.persistence.repository.AuthTokenStore;
import com.company.leadershipintelligence.repository.persistence.repository.UserStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates the first platform administrator (decision D69, question 4). When {@code lis.identity.bootstrap-admin-email} is set and
 * no ACTIVE platform administrator exists, the account with that email gets the flag; if it does not exist it is created already
 * verified with a password nobody knows, and the address is sent a link to choose one. With an administrator in place, or no
 * email configured, nothing happens, so restarting is safe.
 */
@Component
public class BootstrapAdministrator implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(BootstrapAdministrator.class);

    private final UserStore users;
    private final AuthTokenStore tokens;
    private final PasswordHasher hasher;
    private final TokenCodec codec;
    private final AccountMessages messages;
    private final SecurityAudit audit;
    private final IdentityProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public BootstrapAdministrator(UserStore users, AuthTokenStore tokens, PasswordHasher hasher, TokenCodec codec, AccountMessages messages,
                                  SecurityAudit audit, IdentityProperties properties, TransactionTemplate transaction, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.hasher = hasher;
        this.codec = codec;
        this.messages = messages;
        this.audit = audit;
        this.properties = properties;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        String configured = properties.bootstrapAdminEmail();
        if (configured == null || configured.isBlank()) {
            return;
        }
        String email = EmailAddress.normalize(configured);
        if (email == null) {
            LOG.warn("lis.identity.bootstrap-admin-email is not a valid email address; no platform administrator was created");
            return;
        }
        transaction.executeWithoutResult(status -> bootstrap(email));
    }

    private void bootstrap(String email) {
        if (users.existsPlatformAdmin()) {
            return;
        }
        Instant now = clock.instant();
        Optional<UserRow> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            UserRow user = existing.get();
            if (user.status() != AccountStatus.ACTIVE) {
                LOG.warn("the bootstrap administrator account exists but is not ACTIVE; no platform administrator was created");
                return;
            }
            users.setPlatformAdmin(user.id(), true, now);
            audit.record("PLATFORM_ADMIN_BOOTSTRAPPED", SecurityAudit.SUCCESS, user.id(), null, null, ClientInfo.unknown(), Map.of("account", "EXISTING"));
            LOG.info("an existing account was made the platform administrator");
            return;
        }
        UUID id = UUID.randomUUID();
        // a random password nobody sees: the owner of the address sets a real one through the emailed link
        byte[] noise = new byte[32];
        new SecureRandom().nextBytes(noise);
        users.insertVerified(id, email, "Platform administrator", hasher.hash(Base64.getEncoder().encodeToString(noise)), true, now);
        String token = codec.newToken();
        tokens.insert(id, TokenPurpose.RESET_PASSWORD, codec.hash(token), now.plus(properties.resetTtl()), now);
        messages.platformAdministrator(email, "Platform administrator", token);
        audit.record("PLATFORM_ADMIN_BOOTSTRAPPED", SecurityAudit.SUCCESS, id, null, null, ClientInfo.unknown(), Map.of("account", "CREATED"));
        LOG.info("a platform administrator account was created and sent a link to set its password");
    }
}
