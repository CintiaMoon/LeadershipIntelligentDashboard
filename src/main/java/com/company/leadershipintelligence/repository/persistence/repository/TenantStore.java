package com.company.leadershipintelligence.repository.persistence.repository;

import com.company.leadershipintelligence.repository.persistence.entity.TenantRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The tenants themselves (decision D46). Unlike every other store this one is not tenant-scoped, because a tenant is
 * the scope: it is used only by platform administration, never by a request that carries a tenant.
 */
@Repository
public class TenantStore {

    private final JdbcTemplate jdbc;

    public TenantStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Creates the tenant; false when one with that id already exists (nothing is changed). */
    @Transactional
    public boolean insert(String id, String name) {
        return jdbc.update("insert into tenant (id, name) values (?, ?) on conflict (id) do nothing", id, name) == 1;
    }

    public TenantRow find(String id) {
        return jdbc.query("select id, name, created_at from tenant where id = ?",
                (rs, n) -> new TenantRow(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant()), id).stream().findFirst().orElse(null);
    }

    /** Oldest first, then by id, so the order is the same on every read. */
    public List<TenantRow> findAll() {
        return jdbc.query("select id, name, created_at from tenant order by created_at, id",
                (rs, n) -> new TenantRow(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant()));
    }
}
