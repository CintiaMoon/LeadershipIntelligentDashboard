package com.company.leadershipintelligence.service.identity;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.identity.AccountStatus;
import com.company.leadershipintelligence.domain.identity.EmailAddress;
import com.company.leadershipintelligence.domain.identity.PasswordPolicy;
import com.company.leadershipintelligence.repository.persistence.entity.UserRow;
import com.company.leadershipintelligence.repository.persistence.repository.UserStore;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Checking credentials (decisions D65, D67). A sign-in succeeds only when the password matches, the account is ACTIVE and its
 * email is verified. Every other case, an unknown email, a wrong password, an unverified or a disabled account, gives the same
 * error and takes about the same time; the real reason is written to the security audit only. Throttling (429) is in
 * {@link AuthRateLimiter} and runs before any password is checked. The success event is recorded
 * by the caller once the session exists, so the audit never shows a sign-in that did not happen.
 */
@Service
public class AuthenticationService {

    private final UserStore users;
    private final PasswordHasher hasher;
    private final SecurityAudit audit;
    private final AuthRateLimiter limiter;

    public AuthenticationService(UserStore users, PasswordHasher hasher, SecurityAudit audit, AuthRateLimiter limiter) {
        this.users = users;
        this.hasher = hasher;
        this.audit = audit;
        this.limiter = limiter;
    }

    public AccountView authenticate(String rawEmail, String password, ClientInfo client) {
        String email = EmailAddress.normalize(rawEmail);
        // both checks refuse with 429 before any password is hashed; the second one is the delay after repeated failures
        limiter.checkLoginAddress(client);
        if (email != null) {
            limiter.checkLoginOpen(email, client);
        }
        // a password longer than the policy allows is refused before any hashing, so it cannot be used to burn server time
        boolean usable = email != null && password != null && password.length() <= PasswordPolicy.MAX_LENGTH;
        Optional<UserRow> found = usable ? users.findByEmail(email) : Optional.empty();
        if (found.isEmpty()) {
            if (usable) {
                hasher.spendTime(password);
            }
            audit.record("LOGIN", SecurityAudit.FAILURE, null, null, email == null ? "" : email, client, Map.of("reason", "UNKNOWN_ACCOUNT"));
            throw failed(email);
        }
        UserRow user = found.get();
        boolean passwordOk = hasher.matches(password, user.passwordHash());
        String reason = !passwordOk ? "BAD_PASSWORD"
                : user.status() == AccountStatus.PENDING_VERIFICATION || user.emailVerifiedAt() == null ? "NOT_VERIFIED"
                : user.status() == AccountStatus.DISABLED ? "DISABLED" : null;
        if (reason != null) {
            audit.record("LOGIN", SecurityAudit.FAILURE, user.id(), null, null, client, Map.of("reason", reason));
            throw failed(email);
        }
        limiter.loginSucceeded(email);
        return view(user);
    }

    /** The account behind a live session, or an authentication failure when it is gone or no longer ACTIVE. */
    public AccountView current(UUID userId) {
        return users.findById(userId).filter(u -> u.status() == AccountStatus.ACTIVE).map(AuthenticationService::view).orElseThrow(AuthenticationService::failed);
    }

    static AccountView view(UserRow user) {
        return new AccountView(user.id(), user.email(), user.displayName(), user.platformAdmin());
    }

    private ScoringException failed(String email) {
        if (email != null) {
            limiter.loginFailed(email);
        }
        return failed();
    }

    private static ScoringException failed() {
        return new ScoringException(ScoringErrorCode.AUTHENTICATION_FAILED, "The email or the password is not correct.");
    }
}
