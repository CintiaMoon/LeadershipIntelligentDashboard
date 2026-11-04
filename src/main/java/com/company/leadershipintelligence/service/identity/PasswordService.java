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
import com.company.leadershipintelligence.repository.security.SessionRevoker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Password reset and change (decision D73). Asking for a reset answers the same for every address. A reset link is valid once
 * and for an hour; asking again retires the earlier link. Setting a new password ends every session of the user, so whoever
 * knew the old password is signed out, and the owner is told by email.
 */
@Service
public class PasswordService {

    private final UserStore users;
    private final AuthTokenStore tokens;
    private final PasswordHasher hasher;
    private final TokenCodec codec;
    private final AccountMessages messages;
    private final SessionRevoker sessions;
    private final SecurityAudit audit;
    private final IdentityProperties properties;
    private final Clock clock;

    public PasswordService(UserStore users, AuthTokenStore tokens, PasswordHasher hasher, TokenCodec codec, AccountMessages messages,
                           SessionRevoker sessions, SecurityAudit audit, IdentityProperties properties, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.hasher = hasher;
        this.codec = codec;
        this.messages = messages;
        this.sessions = sessions;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    /** Sends a reset link to an ACTIVE account (a verification link to a pending one). Silent for any other address. */
    @Transactional
    public void requestReset(String rawEmail, ClientInfo client) {
        String email = EmailAddress.normalize(rawEmail);
        Optional<UserRow> found = email == null ? Optional.empty() : users.findByEmail(email);
        if (found.isEmpty()) {
            audit.record("PASSWORD_RESET_REQUESTED", SecurityAudit.FAILURE, null, null, email == null ? "" : email, client, Map.of("reason", "UNKNOWN_ACCOUNT"));
            return;
        }
        UserRow user = found.get();
        Instant now = clock.instant();
        if (user.status() == AccountStatus.ACTIVE) {
            tokens.revokeOpen(user.id(), TokenPurpose.RESET_PASSWORD, now);
            String token = codec.newToken();
            tokens.insert(user.id(), TokenPurpose.RESET_PASSWORD, codec.hash(token), now.plus(properties.resetTtl()), now);
            messages.passwordReset(user.email(), user.displayName(), token);
            audit.success("PASSWORD_RESET_REQUESTED", user.id(), client);
        } else if (user.status() == AccountStatus.PENDING_VERIFICATION) {
            tokens.revokeOpen(user.id(), TokenPurpose.VERIFY_EMAIL, now);
            String token = codec.newToken();
            tokens.insert(user.id(), TokenPurpose.VERIFY_EMAIL, codec.hash(token), now.plus(properties.verificationTtl()), now);
            messages.verification(user.email(), user.displayName(), token);
            audit.record("PASSWORD_RESET_REQUESTED", SecurityAudit.FAILURE, user.id(), null, null, client, Map.of("reason", "NOT_VERIFIED"));
        } else {
            audit.record("PASSWORD_RESET_REQUESTED", SecurityAudit.FAILURE, user.id(), null, null, client, Map.of("reason", "DISABLED"));
        }
    }

    @Transactional
    public void reset(String token, String newPassword, ClientInfo client) {
        String early = PasswordPolicy.violation(newPassword, null);
        if (early != null) {
            throw new ScoringException(ScoringErrorCode.WEAK_PASSWORD, early);
        }
        Optional<UUID> userId = token == null || token.isBlank() ? Optional.empty()
                : tokens.consume(codec.hash(token.trim()), TokenPurpose.RESET_PASSWORD, clock.instant());
        Optional<UserRow> user = userId.flatMap(users::findById).filter(u -> u.status() == AccountStatus.ACTIVE);
        if (user.isEmpty()) {
            audit.record("PASSWORD_RESET_COMPLETED", SecurityAudit.FAILURE, userId.orElse(null), null, null, client, Map.of("reason", "INVALID_TOKEN"));
            throw new ScoringException(ScoringErrorCode.INVALID_TOKEN, "The link is not valid or has expired.");
        }
        // checked once the owner is known; the exception rolls the token use back, so the link still works for a better password
        String problem = PasswordPolicy.violation(newPassword, user.get().email());
        if (problem != null) {
            throw new ScoringException(ScoringErrorCode.WEAK_PASSWORD, problem);
        }
        apply(user.get(), newPassword, null, "PASSWORD_RESET_COMPLETED", client);
    }

    @Transactional
    public void change(UUID userId, String currentPassword, String newPassword, String keepSessionId, ClientInfo client) {
        UserRow user = users.findById(userId).filter(u -> u.status() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new ScoringException(ScoringErrorCode.AUTHENTICATION_FAILED, "Authentication is required."));
        if (currentPassword == null || currentPassword.length() > PasswordPolicy.MAX_LENGTH || !hasher.matches(currentPassword, user.passwordHash())) {
            audit.record("PASSWORD_CHANGED", SecurityAudit.FAILURE, userId, null, null, client, Map.of("reason", "BAD_CURRENT_PASSWORD"));
            throw new ScoringException(ScoringErrorCode.INVALID_ACCOUNT_REQUEST, "The current password is not correct.");
        }
        String problem = PasswordPolicy.violation(newPassword, user.email());
        if (problem != null) {
            throw new ScoringException(ScoringErrorCode.WEAK_PASSWORD, problem);
        }
        apply(user, newPassword, keepSessionId, "PASSWORD_CHANGED", client);
    }

    private void apply(UserRow user, String newPassword, String keepSessionId, String event, ClientInfo client) {
        Instant now = clock.instant();
        users.updatePassword(user.id(), hasher.hash(newPassword), now);
        // a link sent before the change must not work after it
        tokens.revokeOpen(user.id(), TokenPurpose.RESET_PASSWORD, now);
        int ended = sessions.revokeAll(user.id().toString(), keepSessionId);
        messages.passwordChanged(user.email(), user.displayName());
        audit.record(event, SecurityAudit.SUCCESS, user.id(), null, null, client, Map.of("sessionsEnded", Integer.toString(ended)));
    }
}
