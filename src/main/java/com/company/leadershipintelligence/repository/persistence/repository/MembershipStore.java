package com.company.leadershipintelligence.repository.persistence.repository;

import com.company.leadershipintelligence.domain.identity.MembershipStatus;
import com.company.leadershipintelligence.repository.persistence.entity.MemberRow;
import com.company.leadershipintelligence.repository.persistence.entity.MembershipRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Company memberships and their invitations (decision D69). Reads of access ({@link #findActive}, {@link #listActive}) are one
 * indexed lookup each and are made on every request, so there is no copy to go stale. Invitation tokens are stored as hashes.
 */
@Repository
public class MembershipStore {

    private static final String SELECT = "select m.id, m.tenant_id, t.name, m.user_id, m.invited_email, m.status from company_membership m "
            + "join tenant t on t.id = m.tenant_id ";

    private final JdbcTemplate jdbc;

    public MembershipStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** The ACTIVE membership of this user in this company, or empty (no membership, or one that is not active). */
    public Optional<MembershipRow> findActive(UUID userId, String tenantId) {
        return load(SELECT + "where m.user_id = ? and m.tenant_id = ? and m.status = 'ACTIVE'", userId, tenantId).stream().findFirst();
    }

    /** The organizations that exist in this company, in a fixed order. */
    public List<String> organizationIds(String tenantId) {
        return jdbc.queryForList("select id from organization where tenant_id = ? order by id", String.class, tenantId);
    }

    /** Every company the user may open, by company name then id so the order is the same on every read. */
    public List<MembershipRow> listActive(UUID userId) {
        return load(SELECT + "where m.user_id = ? and m.status = 'ACTIVE' order by t.name, m.tenant_id", userId);
    }

    @Transactional
    public UUID createInvited(String tenantId, String email, String invitedBy, Collection<String> roles, Instant at) {
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(at);
        jdbc.update("insert into company_membership (id, tenant_id, user_id, invited_email, status, invited_by, created_at, updated_at) "
                + "values (?, ?, null, ?, 'INVITED', ?, ?, ?)", id, tenantId, email, invitedBy, now, now);
        for (String role : roles) {
            jdbc.update("insert into company_membership_role (membership_id, role) values (?, ?)", id, role);
        }
        return id;
    }

    @Transactional
    public void insertInvitationToken(UUID membershipId, String tokenHash, Instant expiresAt, Instant at) {
        jdbc.update("insert into membership_invitation (id, membership_id, token_hash, expires_at, created_at) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(), membershipId, tokenHash, Timestamp.from(expiresAt), Timestamp.from(at));
    }

    /** The invited membership behind an unused, unexpired token, without using the token. */
    public Optional<MembershipRow> findInvitedByToken(String tokenHash, Instant at) {
        return load(SELECT + "join membership_invitation i on i.membership_id = m.id "
                + "where i.token_hash = ? and i.used_at is null and i.expires_at > ? and m.status = 'INVITED'", tokenHash, Timestamp.from(at)).stream().findFirst();
    }

    /**
     * Uses the token and makes the membership ACTIVE for this user, in one transaction. False when the token is no longer
     * valid or the user already has a membership in the company (nothing is changed then).
     */
    @Transactional
    public boolean accept(String tokenHash, UUID userId, Instant at) {
        Timestamp now = Timestamp.from(at);
        // lock the invitation first so two accepts of one token cannot both pass; the token is used last, so a refusal leaves it intact
        List<UUID> found = jdbc.query("select i.membership_id from membership_invitation i join company_membership m on m.id = i.membership_id "
                + "where i.token_hash = ? and i.used_at is null and i.expires_at > ? and m.status = 'INVITED' for update of i",
                (rs, n) -> rs.getObject(1, UUID.class), tokenHash, now);
        if (found.isEmpty()) {
            return false;
        }
        int changed = jdbc.update("update company_membership set user_id = ?, status = 'ACTIVE', updated_at = ? where id = ? and status = 'INVITED' "
                + "and not exists (select 1 from company_membership o where o.tenant_id = company_membership.tenant_id and o.user_id = ? and o.id <> company_membership.id)",
                userId, now, found.get(0), userId);
        if (changed != 1) {
            return false;
        }
        jdbc.update("update membership_invitation set used_at = ? where token_hash = ?", now, tokenHash);
        return true;
    }

    @Transactional
    public void setStatus(UUID membershipId, MembershipStatus status, Instant at) {
        jdbc.update("update company_membership set status = ?, updated_at = ? where id = ?", status.name(), Timestamp.from(at), membershipId);
    }

    // ---------------- the member list and its changes (decision D72); every method takes the company ----------------

    private static final String MEMBER_SELECT = "select m.id, m.tenant_id, m.user_id, coalesce(u.email, m.invited_email), u.display_name, m.status, "
            + "m.invited_by, m.created_at, m.updated_at from company_membership m left join app_user u on u.id = m.user_id ";

    /** The members of a company other than the removed ones, by email so the order is the same on every read. */
    public List<MemberRow> members(String tenantId) {
        return loadMembers(MEMBER_SELECT + "where m.tenant_id = ? and m.status <> 'REMOVED' order by lower(coalesce(u.email, m.invited_email)), m.id", tenantId);
    }

    public Optional<MemberRow> member(String tenantId, UUID id) {
        return loadMembers(MEMBER_SELECT + "where m.tenant_id = ? and m.id = ?", tenantId, id).stream().findFirst();
    }

    /** The membership of this account in this company in any status, or the invitation sent to this email while it has no account. */
    public Optional<MemberRow> memberOf(String tenantId, UUID userId, String email) {
        Optional<MemberRow> byUser = userId == null ? Optional.empty()
                : loadMembers(MEMBER_SELECT + "where m.tenant_id = ? and m.user_id = ?", tenantId, userId).stream().findFirst();
        if (byUser.isPresent()) {
            return byUser;
        }
        return loadMembers(MEMBER_SELECT + "where m.tenant_id = ? and m.user_id is null and lower(m.invited_email) = ?", tenantId, email).stream().findFirst();
    }

    public long activeOwners(String tenantId) {
        Long n = jdbc.queryForObject("select count(*) from company_membership m join company_membership_role r on r.membership_id = m.id "
                + "where m.tenant_id = ? and m.status = 'ACTIVE' and r.role = 'OWNER'", Long.class, tenantId);
        return n == null ? 0 : n;
    }

    public String companyName(String tenantId) {
        return jdbc.queryForObject("select name from tenant where id = ?", String.class, tenantId);
    }

    @Transactional
    public void replaceRoles(String tenantId, UUID membershipId, Collection<String> roles, Instant at) {
        jdbc.update("delete from company_membership_role where membership_id = ? and exists (select 1 from company_membership m where m.id = ? and m.tenant_id = ?)",
                membershipId, membershipId, tenantId);
        for (String role : roles) {
            jdbc.update("insert into company_membership_role (membership_id, role) select m.id, ? from company_membership m where m.id = ? and m.tenant_id = ?",
                    role, membershipId, tenantId);
        }
        touch(tenantId, membershipId, at);
    }

    @Transactional
    public void replaceOrganizations(String tenantId, UUID membershipId, Collection<String> organizations, Instant at) {
        jdbc.update("delete from company_membership_organization where membership_id = ? and exists (select 1 from company_membership m where m.id = ? and m.tenant_id = ?)",
                membershipId, membershipId, tenantId);
        for (String organization : organizations) {
            jdbc.update("insert into company_membership_organization (membership_id, organization_id) select m.id, ? from company_membership m where m.id = ? and m.tenant_id = ?",
                    organization, membershipId, tenantId);
        }
        touch(tenantId, membershipId, at);
    }

    @Transactional
    public void changeStatus(String tenantId, UUID membershipId, MembershipStatus status, Instant at) {
        jdbc.update("update company_membership set status = ?, updated_at = ? where tenant_id = ? and id = ?", status.name(), Timestamp.from(at), tenantId, membershipId);
    }

    /** Invites again someone whose membership was removed: the row is kept (its history stays) and goes back to INVITED. */
    @Transactional
    public void reinvite(String tenantId, UUID membershipId, String email, String invitedBy, Instant at) {
        jdbc.update("update company_membership set status = 'INVITED', invited_email = ?, invited_by = ?, updated_at = ? where tenant_id = ? and id = ?",
                email, invitedBy, Timestamp.from(at), tenantId, membershipId);
    }

    /** Retires the unused invitation links of a membership, so only a newer one works. */
    @Transactional
    public void retireInvitations(UUID membershipId, Instant at) {
        jdbc.update("update membership_invitation set used_at = ? where membership_id = ? and used_at is null", Timestamp.from(at), membershipId);
    }

    private void touch(String tenantId, UUID membershipId, Instant at) {
        jdbc.update("update company_membership set updated_at = ? where tenant_id = ? and id = ?", Timestamp.from(at), tenantId, membershipId);
    }

    private List<MemberRow> loadMembers(String sql, Object... args) {
        record Head(UUID id, String tenantId, UUID userId, String email, String name, MembershipStatus status, String invitedBy, Instant created, Instant updated) {
        }
        List<Head> heads = jdbc.query(sql, (rs, n) -> new Head(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4),
                rs.getString(5), MembershipStatus.valueOf(rs.getString(6)), rs.getString(7), rs.getTimestamp(8).toInstant(), rs.getTimestamp(9).toInstant()), args);
        List<MemberRow> rows = new ArrayList<>(heads.size());
        for (Head h : heads) {
            Set<String> roles = new TreeSet<>(jdbc.queryForList("select role from company_membership_role where membership_id = ?", String.class, h.id()));
            Set<String> organizations = new TreeSet<>(jdbc.queryForList("select organization_id from company_membership_organization where membership_id = ?", String.class, h.id()));
            rows.add(new MemberRow(h.id(), h.tenantId(), h.userId(), h.email(), h.name(), h.status(), roles, organizations, h.invitedBy(), h.created(), h.updated()));
        }
        return rows;
    }

    private List<MembershipRow> load(String sql, Object... args) {
        record Head(UUID id, String tenantId, String tenantName, UUID userId, String invitedEmail, MembershipStatus status) {
        }
        List<Head> heads = jdbc.query(sql, (rs, n) -> new Head(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                rs.getObject(4, UUID.class), rs.getString(5), MembershipStatus.valueOf(rs.getString(6))), args);
        List<MembershipRow> rows = new ArrayList<>(heads.size());
        for (Head h : heads) {
            Set<String> roles = new TreeSet<>(jdbc.queryForList("select role from company_membership_role where membership_id = ?", String.class, h.id()));
            Set<String> organizations = new TreeSet<>(jdbc.queryForList("select organization_id from company_membership_organization where membership_id = ?", String.class, h.id()));
            rows.add(new MembershipRow(h.id(), h.tenantId(), h.tenantName(), h.userId(), h.invitedEmail(), h.status(), roles, organizations));
        }
        return rows;
    }
}
