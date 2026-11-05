package com.company.leadershipintelligence.service.identity;

import com.company.leadershipintelligence.config.properties.IdentityProperties;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.identity.AccountStatus;
import com.company.leadershipintelligence.domain.identity.EmailAddress;
import com.company.leadershipintelligence.repository.persistence.entity.MembershipRow;
import com.company.leadershipintelligence.repository.persistence.entity.UserRow;
import com.company.leadershipintelligence.repository.persistence.repository.MembershipStore;
import com.company.leadershipintelligence.repository.persistence.repository.UserStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Company memberships (decision D69). A membership is the only thing that gives a user access to a company, and it is read on
 * every request, so suspending or removing one takes effect at the next call. Companies are listed only from ACTIVE memberships.
 * A company is invited to its first owner when the platform administrator creates it; the invitation is accepted by a signed-in
 * user whose verified email is the one it was sent to.
 */
@Service
public class MembershipService {

    private final MembershipStore memberships;
    private final UserStore users;
    private final TokenCodec codec;
    private final AccountMessages messages;
    private final AuthRateLimiter limiter;
    private final SecurityAudit audit;
    private final IdentityProperties properties;
    private final Clock clock;

    public MembershipService(MembershipStore memberships, UserStore users, TokenCodec codec, AccountMessages messages, AuthRateLimiter limiter,
                             SecurityAudit audit, IdentityProperties properties, Clock clock) {
        this.memberships = memberships;
        this.users = users;
        this.codec = codec;
        this.messages = messages;
        this.limiter = limiter;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    /** What a request of a signed-in user may use: the account, as it is now. Empty when the account is gone or no longer ACTIVE. */
    public Optional<SessionAccount> activeUser(UUID userId) {
        return activeRow(userId).map(u -> new SessionAccount(u.id(), u.email(), u.platformAdmin()));
    }

    private Optional<UserRow> activeRow(UUID userId) {
        return users.findById(userId).filter(u -> u.status() == AccountStatus.ACTIVE);
    }

    /** The ACTIVE membership of this user in this company, or empty. The same empty answer for a company that does not exist. */
    public Optional<CompanyMembership> access(UUID userId, String tenantId) {
        return memberships.findActive(userId, tenantId).map(MembershipService::view);
    }

    /**
     * What a membership or token reaches in a company. A list means exactly those organizations. No list means every organization of
     * the company, including ones created later, so an owner of a new company can create its first organization.
     */
    public OrganizationReach reach(String tenantId, Set<String> restriction) {
        if (!restriction.isEmpty()) {
            return new OrganizationReach(Set.copyOf(restriction), false);
        }
        return new OrganizationReach(Set.copyOf(memberships.organizationIds(tenantId)), true);
    }

    /** The companies the user may open: ACTIVE memberships only. */
    public List<CompanyMembership> companies(UUID userId) {
        return memberships.listActive(userId).stream().map(MembershipService::view).toList();
    }

    /**
     * Invites the first owner of a new company. Called in the transaction that creates the company, so a company is never left
     * without its invitation. The email is sent once the transaction has committed.
     */
    @Transactional
    public void inviteOwner(String tenantId, String tenantName, String ownerEmail, String actor) {
        String email = EmailAddress.normalize(ownerEmail);
        if (email == null) {
            throw new ScoringException(ScoringErrorCode.INVALID_TENANT, "the owner email is not a valid email address");
        }
        Instant now = clock.instant();
        UUID membership = memberships.createInvited(tenantId, email, actor, Set.of("OWNER"), now);
        String token = codec.newToken();
        memberships.insertInvitationToken(membership, codec.hash(token), now.plus(properties.invitationTtl()), now);
        messages.invitation(email, tenantName, token);
        audit.record("MEMBERSHIP_INVITED", SecurityAudit.SUCCESS, null, tenantId, null, ClientInfo.unknown(), Map.of("role", "OWNER", "by", actor));
    }

    /**
     * Accepts an invitation. The signed-in user's verified email must be the one the invitation was sent to; every refusal looks
     * the same (unknown, used or expired link, another person's invitation, a company the user already belongs to).
     */
    @Transactional
    public void accept(UUID userId, String token, ClientInfo client) {
        limiter.checkTokenUse(client);
        String hash = token == null || token.isBlank() ? null : codec.hash(token.trim());
        Optional<UserRow> user = activeRow(userId);
        Optional<MembershipRow> invited = hash == null ? Optional.empty() : memberships.findInvitedByToken(hash, clock.instant());
        boolean mine = user.isPresent() && invited.isPresent() && invited.get().invitedEmail() != null
                && invited.get().invitedEmail().equalsIgnoreCase(user.get().email());
        if (!mine || !memberships.accept(hash, userId, clock.instant())) {
            audit.record("MEMBERSHIP_ACCEPTED", SecurityAudit.FAILURE, userId, invited.map(MembershipRow::tenantId).orElse(null), null, client,
                    Map.of("reason", invited.isPresent() && !mine ? "OTHER_PERSON" : "INVALID_TOKEN"));
            throw new ScoringException(ScoringErrorCode.INVALID_TOKEN, "The invitation is not valid or has expired.");
        }
        audit.record("MEMBERSHIP_ACCEPTED", SecurityAudit.SUCCESS, userId, invited.get().tenantId(), null, client, Map.of());
    }
private static CompanyMembership view(MembershipRow row) {        return new CompanyMembership(row.tenantId(), row.tenantName(), row.roles(), row.organizationIds());    }
}
