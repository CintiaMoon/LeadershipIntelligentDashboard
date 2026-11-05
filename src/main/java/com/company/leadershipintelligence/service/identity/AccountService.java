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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Registration and email verification (decisions D65, D66). Registration creates an account that cannot sign in until its
 * email is verified, and no company. It answers the same whether or not the email was already known, so the endpoint cannot be
 * used to find out who has an account: an existing owner of the address gets an email instead.
 *
 * <p>Nobody can take over an address by registering it first. Verifying needs both the link (proof of the mailbox) and the
 * password chosen at registration (proof of the registrant), so a stranger's pending account cannot be activated by its real
 * owner clicking the link, and a second registration of a still pending address replaces the first (the latest wins).
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
    private final AuthRateLimiter limiter;
    private final BackgroundWork work;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public AccountService(UserStore users, AuthTokenStore tokens, PasswordHasher hasher, TokenCodec codec, AccountMessages messages,
                          SecurityAudit audit, IdentityProperties properties, AuthRateLimiter limiter, BackgroundWork work,
                          TransactionTemplate transaction, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.hasher = hasher;
        this.codec = codec;
        this.messages = messages;
        this.audit = audit;
        this.properties = properties;
        this.limiter = limiter;
        this.work = work;
        this.transaction = transaction;
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
        if (!limiter.allowMail(email)) {
            // answered like any other registration; nothing is created or sent, so the address cannot be flooded with emails
            audit.record("REGISTERED", SecurityAudit.FAILURE, null, null, email, client, Map.of("reason", "RATE_LIMITED"));
            return;
        }
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
                // the latest registration of a pending address wins, and only its link works (earlier links are retired)
                users.replacePendingCredentials(user.id(), name, hash, now);
                sendVerification(user.id(), user.email(), name, now);
            } else if (user.status() == AccountStatus.ACTIVE) {
                messages.alreadyRegistered(user.email(), user.displayName());
            }
            audit.record("REGISTERED", SecurityAudit.FAILURE, user.id(), null, null, client, Map.of("reason", "ALREADY_REGISTERED"));
        }
    }

    /**
     * Activates an account from the link in the email and the password chosen at registration. A wrong password leaves the link
     * unused, and every refusal looks the same (unknown, used or expired link, wrong password).
     */
    @Transactional
    public void verifyEmail(String token, String password, ClientInfo client) {
        Instant now = clock.instant();
        String tokenHash = token == null || token.isBlank() ? null : codec.hash(token.trim());
        Optional<UserRow> owner = tokenHash == null ? Optional.empty()
                : tokens.findOpen(tokenHash, TokenPurpose.VERIFY_EMAIL, now).flatMap(users::findById)
                        .filter(u -> u.status() == AccountStatus.PENDING_VERIFICATION);
        boolean usable = password != null && password.length() <= PasswordPolicy.MAX_LENGTH;
        boolean passwordOk;
        if (owner.isPresent() && usable) {
            passwordOk = hasher.matches(password, owner.get().passwordHash());
        } else {
            if (usable) {
                hasher.spendTime(password);
            }
            passwordOk = false;
        }
        Optional<UUID> userId = Optional.empty();
        if (passwordOk) {
            userId = tokens.consume(tokenHash, TokenPurpose.VERIFY_EMAIL, now);
        }
        if (userId.isEmpty() || !users.activate(userId.get(), now)) {
            String reason = owner.isPresent() && !passwordOk ? "BAD_PASSWORD" : "INVALID_TOKEN";
            audit.record("EMAIL_VERIFIED", SecurityAudit.FAILURE, owner.map(UserRow::id).orElse(null), null, null, client, Map.of("reason", reason));
            throw new ScoringException(ScoringErrorCode.INVALID_TOKEN, "The link or the password is not valid.");
        }
        audit.success("EMAIL_VERIFIED", userId.get(), client);
    }

    /**
     * Sends a new verification link to a pending account. Silent when the email is unknown or already verified. The lookup
     * and the audit row are the same for every address; the link itself is issued in the background, so the answer takes the
     * same time whether or not an account exists.
     */
    public void resendVerification(String rawEmail, ClientInfo client) {
        String email = EmailAddress.normalize(rawEmail);
        boolean allowed = email == null || limiter.allowMail(email);
        Optional<UserRow> user = email == null ? Optional.empty() : users.findByEmail(email);
        if (user.isPresent() && user.get().status() == AccountStatus.PENDING_VERIFICATION) {
            if (!allowed) {
                audit.record("VERIFICATION_RESENT", SecurityAudit.FAILURE, user.get().id(), null, null, client, Map.of("reason", "RATE_LIMITED"));
                return;
            }
            UserRow pending = user.get();
            audit.success("VERIFICATION_RESENT", pending.id(), client);
            work.run(() -> transaction.executeWithoutResult(status ->
                    sendVerification(pending.id(), pending.email(), pending.displayName(), clock.instant())));
        } else {
            audit.record("VERIFICATION_RESENT", SecurityAudit.FAILURE, null, null, email == null ? "" : email, client, Map.of("reason", "NOT_PENDING"));
        }
    }

    private void sendVerification(UUID userId, String email, String name, Instant now) {
        tokens.revokeOpen(userId, TokenPurpose.VERIFY_EMAIL, now);
        String token = codec.newToken();
        tokens.insert(userId, TokenPurpose.VERIFY_EMAIL, codec.hash(token), now.plus(properties.verificationTtl()), now);
        messages.verification(email, name, token);
    }
}
