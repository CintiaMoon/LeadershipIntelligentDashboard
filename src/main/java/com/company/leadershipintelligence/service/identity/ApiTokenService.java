package com.company.leadershipintelligence.service.identity;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.identity.TokenRoles;
import com.company.leadershipintelligence.repository.persistence.entity.ApiTokenRow;
import com.company.leadershipintelligence.repository.persistence.repository.ApiTokenStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * API tokens for non-browser callers (decision D70). A token belongs to one company and to the user who created it, carries one
 * role (never OWNER, and no stronger than the creator holds) and optionally a list of organizations (never wider than the creator
 * reaches), and is a random opaque value of which only the SHA-256 is stored. The secret is returned once, at creation.
 *
 * <p>A token is checked on every request: revoking it or letting it expire stops it at once, and so does its creator losing the
 * company, being disabled, or being left with less access than the token has. A token never manages tokens.
 */
@Service
public class ApiTokenService {

    public static final String PREFIX = "lis_";
    public static final int MAX_NAME = 100;
    /** A company may have this many usable tokens at once. */
    public static final int MAX_USABLE = 50;
    private static final int MAX_ORGANIZATIONS = 100;
    private static final int PREFIX_SHOWN = PREFIX.length() + 8;

    private final ApiTokenStore tokens;
    private final MembershipService memberships;
    private final TokenCodec codec;
    private final AuthRateLimiter limiter;
    private final SecurityAudit audit;
    private final Clock clock;

    public ApiTokenService(ApiTokenStore tokens, MembershipService memberships, TokenCodec codec, AuthRateLimiter limiter, SecurityAudit audit, Clock clock) {
        this.tokens = tokens;
        this.memberships = memberships;
        this.codec = codec;
        this.limiter = limiter;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public IssuedApiToken create(UUID creatorId, String tenantId, String rawName, String role, Instant expiresAt,
                                 Collection<String> rawOrganizations, ClientInfo client) {
        CompanyMembership creator = memberships.access(creatorId, tenantId)
                .orElseThrow(() -> new ScoringException(ScoringErrorCode.TENANT_ACCESS_DENIED, "You do not have access to this company."));
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.length() > MAX_NAME) {
            throw invalid("The name must have 1 to " + MAX_NAME + " characters.");
        }
        if (role == null || !TokenRoles.issuable().contains(role)) {
            throw invalid("The role must be one of " + String.join(", ", TokenRoles.issuable()) + ".");
        }
        if (!TokenRoles.mayIssue(creator.roles(), role)) {
            throw invalid("A token cannot have a stronger role than the person who creates it.");
        }
        Instant now = clock.instant();
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw invalid("The expiry must be in the future.");
        }
        Set<String> organizations = organizations(rawOrganizations);
        Set<String> reach = creator.organizationIds();
        if (!reach.isEmpty() && (organizations.isEmpty() || !reach.containsAll(organizations))) {
            throw invalid("A token cannot reach organizations that the person who creates it cannot reach.");
        }
        if (tokens.countUsable(tenantId, now) >= MAX_USABLE) {
            throw invalid("The company already has " + MAX_USABLE + " usable tokens; revoke one first.");
        }
        String secret = PREFIX + codec.newToken();
        UUID id = tokens.insert(tenantId, creatorId, name, role, codec.hash(secret), secret.substring(0, PREFIX_SHOWN), expiresAt, organizations, now);
        audit.record("API_TOKEN_CREATED", SecurityAudit.SUCCESS, creatorId, tenantId, null, client, Map.of("token", id.toString(), "role", role));
        return new IssuedApiToken(tokens.find(tenantId, id).map(this::view).orElseThrow(), secret);
    }

    public List<ApiTokenView> list(String tenantId) {
        return tokens.list(tenantId).stream().map(this::view).toList();
    }

    /** Revokes a token of this company. Revoking one that is already revoked is not an error. */
    @Transactional
    public void revoke(String tenantId, UUID id, UUID by, ClientInfo client) {
        ApiTokenRow row = tokens.find(tenantId, id).orElseThrow(() -> new ScoringException(ScoringErrorCode.API_TOKEN_NOT_FOUND, "No such API token."));
        if (tokens.revoke(tenantId, row.id(), by, clock.instant())) {
            audit.record("API_TOKEN_REVOKED", SecurityAudit.SUCCESS, by, tenantId, null, client, Map.of("token", id.toString()));
        }
    }

    /**
     * The caller behind a secret, or empty when it is not a usable token. Every reason a token is refused looks the same to the
     * caller and is written to the security audit. A token over its call limit, or an address that keeps presenting tokens that
     * are not valid, is refused with a {@link com.company.leadershipintelligence.domain.exception.RateLimitedException}.
     */
    public Optional<ApiTokenCaller> authenticate(String secret, ClientInfo client) {
        Optional<ApiTokenRow> found = secret == null || secret.isBlank() ? Optional.empty() : tokens.findByHash(codec.hash(secret.trim()));
        if (found.isEmpty()) {
            limiter.apiTokenRejected(client);
            return Optional.empty();
        }
        ApiTokenRow row = found.get();
        Instant now = clock.instant();
        String problem = problem(row, now);
        if (problem != null) {
            audit.record("API_TOKEN_REJECTED", SecurityAudit.FAILURE, row.createdBy(), row.tenantId(), null, client, Map.of("token", row.id().toString(), "reason", problem));
            limiter.apiTokenRejected(client);
            return Optional.empty();
        }
        limiter.checkApiToken(row.id(), client);
        // the time of last use is kept to within the hour; the audit notes the first use of each day
        Instant last = row.lastUsedAt();
        if (last == null || last.isBefore(now.minusSeconds(3600))) {
            tokens.touch(row.id(), now);
        }
        if (last == null || !last.atZone(ZoneOffset.UTC).toLocalDate().equals(now.atZone(ZoneOffset.UTC).toLocalDate())) {
            audit.record("API_TOKEN_USED", SecurityAudit.SUCCESS, row.createdBy(), row.tenantId(), null, client, Map.of("token", row.id().toString()));
        }
        return Optional.of(new ApiTokenCaller(row.id(), row.tenantId(), row.role(), row.organizationIds()));
    }

    /** Why this token may not be used right now, or null when it may. */
    private String problem(ApiTokenRow row, Instant now) {
        if (row.revokedAt() != null) {
            return "REVOKED";
        }
        if (row.expiresAt() != null && !now.isBefore(row.expiresAt())) {
            return "EXPIRED";
        }
        if (memberships.activeUser(row.createdBy()).isEmpty()) {
            return "CREATOR_DISABLED";
        }
        Optional<CompanyMembership> creator = memberships.access(row.createdBy(), row.tenantId());
        if (creator.isEmpty()) {
            return "CREATOR_NOT_MEMBER";
        }
        if (!TokenRoles.mayIssue(creator.get().roles(), row.role())) {
            return "CREATOR_ROLE_LOWER";
        }
        Set<String> reach = creator.get().organizationIds();
        if (!reach.isEmpty() && (row.organizationIds().isEmpty() || !reach.containsAll(row.organizationIds()))) {
            return "CREATOR_SCOPE_NARROWER";
        }
        return null;
    }

    private ApiTokenView view(ApiTokenRow row) {
        Instant now = clock.instant();
        String status = row.revokedAt() != null ? "REVOKED" : row.expiresAt() != null && !now.isBefore(row.expiresAt()) ? "EXPIRED" : "ACTIVE";
        return new ApiTokenView(row.id(), row.name(), row.role(), row.prefix(), row.organizationIds(), row.createdBy(), row.createdByEmail(),
                row.createdAt(), row.expiresAt(), row.lastUsedAt(), row.revokedAt(), status);
    }

    private static Set<String> organizations(Collection<String> raw) {
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
            throw invalid("A token can be limited to at most " + MAX_ORGANIZATIONS + " organizations.");
        }
        return result;
    }

    private static ScoringException invalid(String message) {
        return new ScoringException(ScoringErrorCode.INVALID_API_TOKEN, message);
    }
}
