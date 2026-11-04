package com.company.leadershipintelligence.service.identity;

import com.company.leadershipintelligence.config.properties.IdentityProperties;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.identity.AccountStatus;
import com.company.leadershipintelligence.domain.identity.EmailAddress;
import com.company.leadershipintelligence.domain.identity.PasswordPolicy;
import com.company.leadershipintelligence.domain.identity.TokenPurpose;
import com.company.leadershipintelligence.repository.persistence.entity.UserRow;
import com.company.leadershipintelligence.repository.persistence.repository.AuthTokenStore;
import com.company.leadershipintelligence.repository.persistence.repository.UserStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Registration and email verification (decisions D65, D66). Registration creates an account that cannot sign in until its
 * email is verified, and no company. It answers the same whether or not the email was already known, so the endpoint cannot be
 * used to find out who has an account: an existing owner of the address gets an email instead. A repeated registration never
 * changes an existing account, so nobody can take over a pending account by registering it again.
 */
@Service
public class AccountService {

    public static final int MAX_NAME = 200;

    private final UserStore users;
    private final AuthTokenStore tokens;
    private final PasswordHasher hasher;
    private final TokenCodec codec;
    private final AccountMessages messages;
    private final SecurityAudit audit;
    private final IdentityProperties properties;
    private final Clock clock;

    public AccountService(UserStore users, AuthTokenStore tokens, PasswordHasher hasher, TokenCodec codec, AccountMessages messages,
                          SecurityAudit audit, IdentityProperties properties, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.hasher = hasher;
        this.codec = codec;
        this.messages = messages;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void register(String rawEmail, String password, String rawName, ClientInfo client) {
        String email = EmailAddress.normalize(rawEmail);
        if (email == null) {
            throw new ScoringException(ScoringErrorCode.INVALID_ACCOUNT_REQUEST, "A valid email address is required.");
        }
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw new ScoringException(ScoringErrorCode.INVALID_ACCOUNT_REQUEST, "The name must have 1 to " + MAX_NAME + " characters.");
        }
        String problem = PasswordPolicy.violation(password, email);
        if (problem != null) {
            throw new ScoringException(ScoringErrorCode.WEAK_PASSWORD, problem);
        }
        Instant now = clock.instant();
        // the password is hashed before anyone looks for the email, so a new and a known address take the same time
        String hash = hasher.hash(password);
        UUID id = UUID.randomUUID();
        if (users.insertPending(id, email, name, hash, now)) {
            sendVerification(id, email, name, now);
            audit.record("REGISTERED", SecurityAudit.SUCCESS, id, null, null, client, Map.of());
            return;
        }
        Optional<UserRow> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            UserRow user = existing.get();
            if (user.status() == AccountStatus.PENDING_VERIFICATION) {
                sendVerification(user.id(), user.email(), user.displayName(), now);
            } else if (user.status() == AccountStatus.ACTIVE) {
                messages.alreadyRegistered(user.email(), user.displayName());
            }
            audit.record("REGISTERED", SecurityAudit.FAILURE, user.id(), null, null, client, Map.of("reason", "ALREADY_REGISTERED"));
        }
    }

    @Transactional
    public void verifyEmail(String token, ClientInfo client) {
        Optional<UUID> userId = token == null || token.isBlank() ? Optional.empty()
                : tokens.consume(codec.hash(token.trim()), TokenPurpose.VERIFY_EMAIL, clock.instant());
        if (userId.isEmpty() || !users.activate(userId.get(), clock.instant())) {
            audit.record("EMAIL_VERIFIED", SecurityAudit.FAILURE, userId.orElse(null), null, null, client, Map.of("reason", "INVALID_TOKEN"));
            throw new ScoringException(ScoringErrorCode.INVALID_TOKEN, "The link is not valid or has expired.");
        }
        audit.success("EMAIL_VERIFIED", userId.get(), client);
    }

    /** Sends a new verification link to a pending account. Silent when the email is unknown or already verified. */
    @Transactional
    public void resendVerification(String rawEmail, ClientInfo client) {
        String email = EmailAddress.normalize(rawEmail);
        Optional<UserRow> user = email == null ? Optional.empty() : users.findByEmail(email);
        if (user.isPresent() && user.get().status() == AccountStatus.PENDING_VERIFICATION) {
            sendVerification(user.get().id(), user.get().email(), user.get().displayName(), clock.instant());
            audit.success("VERIFICATION_RESENT", user.get().id(), client);
        }
    }

    private void sendVerification(UUID userId, String email, String name, Instant now) {
        tokens.revokeOpen(userId, TokenPurpose.VERIFY_EMAIL, now);
        String token = codec.newToken();
        tokens.insert(userId, TokenPurpose.VERIFY_EMAIL, codec.hash(token), now.plus(properties.verificationTtl()), now);
        messages.verification(email, name, token);
    }
}
