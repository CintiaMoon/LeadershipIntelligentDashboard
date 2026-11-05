package com.company.leadershipintelligence.service.identity;

import com.company.leadershipintelligence.config.properties.IdentityProperties;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.identity.EmailAddress;
import com.company.leadershipintelligence.domain.identity.MemberRules;
import com.company.leadershipintelligence.domain.identity.MembershipStatus;
import com.company.leadershipintelligence.repository.persistence.entity.MemberRow;
import com.company.leadershipintelligence.repository.persistence.entity.UserRow;
import com.company.leadershipintelligence.repository.persistence.repository.MembershipStore;
import com.company.leadershipintelligence.repository.persistence.repository.UserStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Managing the members of a company and inviting people (decision D72). Owners and administrators act, always as a signed-in person.
 *
 * <ul>
 *   <li>An OWNER may grant and remove every role; an ADMIN may manage ENGINEERING_LEADER, ANALYST, VIEWER and RESPONDENT but not
 *       OWNER or ADMIN ({@link MemberRules}).</li>
 *   <li>Nobody changes, suspends or removes their own membership.</li>
 *   <li>A company always keeps at least one ACTIVE OWNER: removing, suspending or demoting the last one is refused.</li>
 *   <li>An actor limited to some organizations can only grant a subset of them and only manage members inside them.</li>
 * </ul>
 *
 * Suspending or removing a member takes effect at their next request (the membership is read on every request), and ends the API
 * tokens they created. An invitation is an INVITED membership and a single-use emailed link; the invited person accepts it signed in,
 * or registers with it ({@link MembershipService}).
 */
@Service
public class MemberService {

    private static final int MAX_ORGANIZATIONS = 100;

    private final MembershipStore memberships;
    private final UserStore users;
    private final MembershipService access;
    private final TokenCodec codec;
    private final AccountMessages messages;
    private final AuthRateLimiter limiter;
    private final SecurityAudit audit;
    private final IdentityProperties properties;
    private final Clock clock;

    public MemberService(MembershipStore memberships, UserStore users, MembershipService access, TokenCodec codec, AccountMessages messages,
                         AuthRateLimiter limiter, SecurityAudit audit, IdentityProperties properties, Clock clock) {
        this.memberships = memberships;
        this.users = users;
        this.access = access;
        this.codec = codec;
        this.messages = messages;
        this.limiter = limiter;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    /** The members of the company other than the removed ones, by email. */
    public List<MemberView> list(String tenantId) {
        return memberships.members(tenantId).stream().map(MemberService::view).toList();
    }

    @Transactional
    public MemberView invite(UUID actorId, String tenantId, String rawEmail, Collection<String> rawRoles, Collection<String> rawOrganizations, ClientInfo client) {
        CompanyMembership actor = actor(actorId, tenantId);
        String email = EmailAddress.normalize(rawEmail);
        if (email == null) {
            throw invalid("A valid email address is required.");
        }
        Set<String> roles = roles(rawRoles);
        requireMayGrant(actor, roles);
        Set<String> organizations = organizations(actor, rawOrganizations);
        limiter.checkInvitation(tenantId, email, client);

        Optional<UserRow> account = users.findByEmail(email);
        Optional<MemberRow> existing = memberships.memberOf(tenantId, account.map(UserRow::id).orElse(null), email);
        Instant now = clock.instant();
        UUID id;
        if (existing.isPresent()) {
            MemberRow row = existing.get();
            if (row.status() != MembershipStatus.REMOVED) {
                throw new ScoringException(ScoringErrorCode.MEMBER_CONFLICT, row.status() == MembershipStatus.INVITED
                        ? "This address has already been invited; resend the invitation instead." : "This person is already a member of the company.");
            }
            // someone who was removed is invited again on the same membership, so its history stays
            id = row.id();
            memberships.reinvite(tenantId, id, email, actorId.toString(), now);
        } else {
            id = memberships.createInvited(tenantId, email, actorId.toString(), roles, now);
        }
        memberships.replaceRoles(tenantId, id, roles, now);
        memberships.replaceOrganizations(tenantId, id, organizations, now);
        sendInvitation(tenantId, id, email, now);
        audit.record("MEMBERSHIP_INVITED", SecurityAudit.SUCCESS, actorId, tenantId, null, client, Map.of("member", id.toString(), "roles", String.join(",", roles)));
        return view(memberships.member(tenantId, id).orElseThrow());
    }

    @Transactional
    public MemberView update(UUID actorId, String tenantId, UUID memberId, MemberChange change, ClientInfo client) {
        CompanyMembership actor = actor(actorId, tenantId);
        MemberRow target = target(actor, actorId, tenantId, memberId);
        if (change == null || (change.roles() == null && change.organizationIds() == null && change.status() == null)) {
            throw invalid("There is nothing to change.");
        }
        Set<String> roles = change.roles() == null ? target.roles() : roles(change.roles());
        if (change.roles() != null) {
            requireMayGrant(actor, roles);
        }
        Set<String> organizations = change.organizationIds() == null ? target.organizationIds() : organizations(actor, change.organizationIds());
        MembershipStatus status = target.status();
        if (change.status() != null) {
            status = switch (change.status()) {
                case "ACTIVE" -> MembershipStatus.ACTIVE;
                case "SUSPENDED" -> MembershipStatus.SUSPENDED;
                default -> throw invalid("The status must be ACTIVE or SUSPENDED.");
            };
            if (target.status() == MembershipStatus.INVITED) {
                throw invalid("An invitation has no status to change; remove it or resend it.");
            }
        }
        requireOwnerRemains(tenantId, target, roles, status);

        Instant now = clock.instant();
        String member = memberId.toString();
        if (!roles.equals(target.roles())) {
            memberships.replaceRoles(tenantId, memberId, roles, now);
            audit.record("MEMBERSHIP_ROLE_CHANGED", SecurityAudit.SUCCESS, actorId, tenantId, null, client,
                    Map.of("member", member, "from", String.join(",", target.roles()), "to", String.join(",", roles)));
        }
        if (!organizations.equals(target.organizationIds())) {
            memberships.replaceOrganizations(tenantId, memberId, organizations, now);
            audit.record("MEMBERSHIP_SCOPE_CHANGED", SecurityAudit.SUCCESS, actorId, tenantId, null, client, Map.of("member", member));
        }
        if (status != target.status()) {
            memberships.changeStatus(tenantId, memberId, status, now);
            audit.record(status == MembershipStatus.SUSPENDED ? "MEMBERSHIP_SUSPENDED" : "MEMBERSHIP_REACTIVATED", SecurityAudit.SUCCESS, actorId, tenantId,
                    null, client, Map.of("member", member));
        }
        return view(memberships.member(tenantId, memberId).orElseThrow());
    }

    /** Removes a member, or withdraws an invitation. The membership row is kept; the person loses access at once. */
    @Transactional
    public void remove(UUID actorId, String tenantId, UUID memberId, ClientInfo client) {
        CompanyMembership actor = actor(actorId, tenantId);
        MemberRow target = target(actor, actorId, tenantId, memberId);
        requireOwnerRemains(tenantId, target, Set.of(), MembershipStatus.REMOVED);
        Instant now = clock.instant();
        memberships.changeStatus(tenantId, memberId, MembershipStatus.REMOVED, now);
        memberships.retireInvitations(memberId, now);
        audit.record("MEMBERSHIP_REMOVED", SecurityAudit.SUCCESS, actorId, tenantId, null, client, Map.of("member", memberId.toString()));
    }

    /** Sends a new invitation link for a pending invitation; the earlier link stops working. */
    @Transactional
    public void resendInvitation(UUID actorId, String tenantId, UUID memberId, ClientInfo client) {
        CompanyMembership actor = actor(actorId, tenantId);
        MemberRow target = target(actor, actorId, tenantId, memberId);
        if (target.status() != MembershipStatus.INVITED) {
            throw new ScoringException(ScoringErrorCode.MEMBER_CONFLICT, "Only a pending invitation can be sent again.");
        }
        limiter.checkInvitation(tenantId, target.email(), client);
        Instant now = clock.instant();
        memberships.retireInvitations(memberId, now);
        sendInvitation(tenantId, memberId, target.email(), now);
        audit.record("MEMBERSHIP_INVITATION_RESENT", SecurityAudit.SUCCESS, actorId, tenantId, null, client, Map.of("member", memberId.toString()));
    }

    // ---------------- rules ----------------

    private CompanyMembership actor(UUID actorId, String tenantId) {
        return access.access(actorId, tenantId)
                .orElseThrow(() -> new ScoringException(ScoringErrorCode.TENANT_ACCESS_DENIED, "You do not have access to this company."));
    }

    /** The member to act on, after the checks that depend on who acts: not themselves, not above them, not outside their organizations. */
    private MemberRow target(CompanyMembership actor, UUID actorId, String tenantId, UUID memberId) {
        MemberRow row = memberships.member(tenantId, memberId).filter(m -> m.status() != MembershipStatus.REMOVED)
                .orElseThrow(() -> new ScoringException(ScoringErrorCode.MEMBER_NOT_FOUND, "No such member."));
        if (actorId.equals(row.userId())) {
            throw refused("You cannot change your own membership.");
        }
        if (!MemberRules.mayManage(actor.roles(), row.roles())) {
            throw refused("Your role does not allow you to manage this member.");
        }
        Set<String> reach = actor.organizationIds();
        if (!reach.isEmpty() && (row.organizationIds().isEmpty() || !reach.containsAll(row.organizationIds()))) {
            throw refused("This member reaches organizations that you cannot reach.");
        }
        return row;
    }

    private static void requireMayGrant(CompanyMembership actor, Set<String> roles) {
        if (!MemberRules.mayGrant(actor.roles(), roles)) {
            throw refused("Your role does not allow you to give these roles.");
        }
    }

    /** A company keeps one ACTIVE OWNER: the last one cannot be removed, suspended or demoted. */
    private void requireOwnerRemains(String tenantId, MemberRow target, Set<String> rolesAfter, MembershipStatus statusAfter) {
        boolean ownerNow = target.status() == MembershipStatus.ACTIVE && target.roles().contains("OWNER");
        boolean ownerAfter = statusAfter == MembershipStatus.ACTIVE && rolesAfter.contains("OWNER");
        if (ownerNow && !ownerAfter && memberships.activeOwners(tenantId) <= 1) {
            throw new ScoringException(ScoringErrorCode.MEMBER_CONFLICT, "A company needs at least one active owner.");
        }
    }

    private static Set<String> roles(Collection<String> raw) {
        Set<String> roles = raw == null ? Set.of() : new TreeSet<>(raw);
        if (!MemberRules.validRoles(roles)) {
            throw invalid("Roles must be one or more of OWNER, ADMIN, ENGINEERING_LEADER, ANALYST, VIEWER, or RESPONDENT on its own.");
        }
        return roles;
    }

    /** The organizations for a membership. No list means every organization, which only an actor with that reach may give. */
    private static Set<String> organizations(CompanyMembership actor, Collection<String> raw) {
        Set<String> result = new TreeSet<>();
        if (raw != null) {
            for (String organization : raw) {
                String trimmed = organization == null ? "" : organization.trim();
                if (trimmed.isEmpty() || trimmed.length() > 64) {
                    throw invalid("An organization id has 1 to 64 characters.");
                }
                result.add(trimmed);
            }
        }
        if (result.size() > MAX_ORGANIZATIONS) {
            throw invalid("A member can be limited to at most " + MAX_ORGANIZATIONS + " organizations.");
        }
        Set<String> reach = actor.organizationIds();
        if (!reach.isEmpty() && (result.isEmpty() || !reach.containsAll(result))) {
            throw refused("You cannot give access to organizations that you cannot reach yourself.");
        }
        return result;
    }

    private void sendInvitation(String tenantId, UUID membershipId, String email, Instant now) {
        String token = codec.newToken();
        memberships.insertInvitationToken(membershipId, codec.hash(token), now.plus(properties.invitationTtl()), now);
        messages.invitation(email, memberships.companyName(tenantId), token);
    }

    private static MemberView view(MemberRow row) {
        return new MemberView(row.id(), row.email(), row.displayName(), row.status().name(), row.roles(), row.organizationIds(), row.invitedBy(),
                row.createdAt(), row.updatedAt());
    }

    private static ScoringException invalid(String message) {
        return new ScoringException(ScoringErrorCode.INVALID_MEMBER, message);
    }

    private static ScoringException refused(String message) {
        return new ScoringException(ScoringErrorCode.MEMBER_ACTION_REFUSED, message);
    }
}
