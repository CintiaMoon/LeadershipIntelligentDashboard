package com.company.leadershipintelligence.repository.persistence.repository;

import com.company.leadershipintelligence.repository.persistence.entity.ApiTokenRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * API tokens (decision D70). Every method that reads or changes the tokens of a company takes the tenant, like the other
 * tenant-owned stores. {@link #findByHash} is the one exception: authenticating a request has to find the token before it knows
 * the company, which is what the token then tells. The store sees only the hash of a secret, never the secret.
 */
@Repository
public class ApiTokenStore {

    private static final String SELECT = "select t.id, t.tenant_id, t.created_by, u.email, t.name, t.role, t.token_prefix, t.expires_at, "
            + "t.revoked_at, t.last_used_at, t.created_at from api_token t join app_user u on u.id = t.created_by ";

    private final JdbcTemplate jdbc;

    public ApiTokenStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public UUID insert(String tenantId, UUID createdBy, String name, String role, String tokenHash, String prefix,
                       Instant expiresAt, Collection<String> organizations, Instant at) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into api_token (id, tenant_id, created_by, name, role, token_hash, token_prefix, expires_at, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, createdBy, name, role, tokenHash, prefix, expiresAt == null ? null : Timestamp.from(expiresAt), Timestamp.from(at));
        for (String organization : organizations) {
            jdbc.update("insert into api_token_organization (token_id, organization_id) values (?, ?)", id, organization);
        }
        return id;
    }

    /** The token behind a secret (by hash), whatever its state; the caller decides whether it may be used. */
    public Optional<ApiTokenRow> findByHash(String tokenHash) {
        return load(SELECT + "where t.token_hash = ?", tokenHash).stream().findFirst();
    }

    public Optional<ApiTokenRow> find(String tenantId, UUID id) {
        return load(SELECT + "where t.tenant_id = ? and t.id = ?", tenantId, id).stream().findFirst();
    }

    /** Newest first, then by id so the order is the same on every read. */
    public List<ApiTokenRow> list(String tenantId) {
        return load(SELECT + "where t.tenant_id = ? order by t.created_at desc, t.id", tenantId);
    }

    /** Tokens that can still be used: not revoked and not expired. */
    public long countUsable(String tenantId, Instant at) {
        Long n = jdbc.queryForObject("select count(*) from api_token where tenant_id = ? and revoked_at is null and (expires_at is null or expires_at > ?)",
                Long.class, tenantId, Timestamp.from(at));
        return n == null ? 0 : n;
    }

    /** Revokes the token of this company; false when there is none or it was already revoked (nothing changes). */
    @Transactional
    public boolean revoke(String tenantId, UUID id, UUID by, Instant at) {
        return jdbc.update("update api_token set revoked_at = ?, revoked_by = ? where tenant_id = ? and id = ? and revoked_at is null",
                Timestamp.from(at), by, tenantId, id) == 1;
    }

    @Transactional
    public void touch(UUID id, Instant at) {
        jdbc.update("update api_token set last_used_at = ? where id = ?", Timestamp.from(at), id);
    }

    private List<ApiTokenRow> load(String sql, Object... args) {
        List<ApiTokenRow> heads = jdbc.query(sql, ApiTokenStore::map, args);
        return heads.stream().map(h -> new ApiTokenRow(h.id(), h.tenantId(), h.createdBy(), h.createdByEmail(), h.name(), h.role(), h.prefix(),
                new TreeSet<>(jdbc.queryForList("select organization_id from api_token_organization where token_id = ?", String.class, h.id())),
                h.expiresAt(), h.revokedAt(), h.lastUsedAt(), h.createdAt())).toList();
    }

    private static ApiTokenRow map(ResultSet rs, int n) throws SQLException {
        return new ApiTokenRow(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getString(7), Set.of(), instant(rs.getTimestamp(8)), instant(rs.getTimestamp(9)), instant(rs.getTimestamp(10)), rs.getTimestamp(11).toInstant());
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
