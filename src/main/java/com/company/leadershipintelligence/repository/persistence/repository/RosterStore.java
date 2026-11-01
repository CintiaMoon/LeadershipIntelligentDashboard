package com.company.leadershipintelligence.repository.persistence.repository;

import com.company.leadershipintelligence.repository.persistence.entity.RosterRows.PersonRow;
import com.company.leadershipintelligence.repository.persistence.entity.RosterRows.TeamRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped storage of teams and the people of the roster (decision D48). Every statement names the tenant, so a team or a
 * person of another tenant looks like a missing one. E-mail addresses are stored lower-cased.
 */
@Repository
public class RosterStore {

    private static final RowMapper<TeamRow> TEAM = (rs, n) -> new TeamRow(rs.getString("id"), rs.getString("organization_id"),
            rs.getString("name"), rs.getTimestamp("created_at").toInstant(), rs.getInt("active_people"));

    private static final String PERSON_SELECT = "select p.id, p.team_id, t.organization_id, p.name, p.email, p.active, p.created_at, p.updated_at "
            + "from person p join team t on t.id = p.team_id ";

    private static final RowMapper<PersonRow> PERSON = (rs, n) -> new PersonRow(rs.getObject("id", UUID.class), rs.getString("team_id"),
            rs.getString("organization_id"), rs.getString("name"), rs.getString("email"), rs.getBoolean("active"),
            rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbc;

    public RosterStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------- teams ----------------

    /** Makes sure the organization exists for this tenant; false when the id belongs to another tenant. */
    @Transactional
    public boolean ensureOrganization(String tenantId, String organizationId) {
        jdbc.update("insert into organization (id, tenant_id, name) values (?, ?, ?) on conflict (id) do nothing", organizationId, tenantId, organizationId);
        Integer own = jdbc.queryForObject("select count(*) from organization where id = ? and tenant_id = ?", Integer.class, organizationId, tenantId);
        return own != null && own > 0;
    }

    @Transactional
    public void insertTeam(String tenantId, String id, String organizationId, String name, Instant now) {
        jdbc.update("insert into team (id, tenant_id, organization_id, name, created_at) values (?, ?, ?, ?, ?)", id, tenantId, organizationId, name,
                Timestamp.from(now));
    }

    @Transactional
    public void renameTeam(String tenantId, String id, String name) {
        jdbc.update("update team set name = ? where tenant_id = ? and id = ?", name, tenantId, id);
    }

    public Optional<TeamRow> team(String tenantId, String id) {
        return jdbc.query(teamSelect("where t.tenant_id = ? and t.id = ?"), TEAM, tenantId, id).stream().findFirst();
    }

    /** The teams of the organizations given, by name. An empty collection means no organization, never "all". */
    public List<TeamRow> teams(String tenantId, Collection<String> organizationIds) {
        if (organizationIds.isEmpty()) {
            return List.of();
        }
        String marks = String.join(",", organizationIds.stream().map(o -> "?").toList());
        Object[] arguments = new Object[organizationIds.size() + 1];
        arguments[0] = tenantId;
        int i = 1;
        for (String organization : organizationIds) {
            arguments[i++] = organization;
        }
        return jdbc.query(teamSelect("where t.tenant_id = ? and t.organization_id in (" + marks + ")") + " order by lower(t.name), t.id", TEAM, arguments);
    }

    private static String teamSelect(String where) {
        return "select t.id, t.organization_id, t.name, t.created_at, "
                + "(select count(*) from person p where p.team_id = t.id and p.active) as active_people from team t " + where;
    }

    // ---------------- people ----------------

    @Transactional
    public void insertPerson(String tenantId, UUID id, String teamId, String name, String email, boolean active, Instant now) {
        jdbc.update("insert into person (id, tenant_id, team_id, name, email, active, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
                id, tenantId, teamId, name, email, active, Timestamp.from(now), Timestamp.from(now));
    }

    @Transactional
    public void updatePerson(String tenantId, UUID id, String teamId, String name, String email, boolean active, Instant now) {
        jdbc.update("update person set team_id = ?, name = ?, email = ?, active = ?, updated_at = ? where tenant_id = ? and id = ?",
                teamId, name, email, active, Timestamp.from(now), tenantId, id);
    }

    public Optional<PersonRow> person(String tenantId, UUID id) {
        return jdbc.query(PERSON_SELECT + "where p.tenant_id = ? and p.id = ?", PERSON, tenantId, id).stream().findFirst();
    }

    public Optional<PersonRow> personByEmail(String tenantId, String email) {
        return jdbc.query(PERSON_SELECT + "where p.tenant_id = ? and p.email = ?", PERSON, tenantId, email).stream().findFirst();
    }

    /** People of the given organizations (and, if given, of one team), by name. */
    public List<PersonRow> people(String tenantId, Collection<String> organizationIds, String teamId) {
        if (organizationIds.isEmpty()) {
            return List.of();
        }
        String marks = String.join(",", organizationIds.stream().map(o -> "?").toList());
        java.util.ArrayList<Object> arguments = new java.util.ArrayList<>();
        arguments.add(tenantId);
        arguments.addAll(organizationIds);
        String sql = PERSON_SELECT + "where p.tenant_id = ? and t.organization_id in (" + marks + ")";
        if (teamId != null) {
            sql += " and p.team_id = ?";
            arguments.add(teamId);
        }
        return jdbc.query(sql + " order by lower(p.name), p.id", PERSON, arguments.toArray());
    }

    /** The active people of the given teams, in a fixed order. */
    public List<PersonRow> activePeopleOf(String tenantId, Collection<String> teamIds) {
        if (teamIds.isEmpty()) {
            return List.of();
        }
        String marks = String.join(",", teamIds.stream().map(o -> "?").toList());
        java.util.ArrayList<Object> arguments = new java.util.ArrayList<>();
        arguments.add(tenantId);
        arguments.addAll(teamIds);
        return jdbc.query(PERSON_SELECT + "where p.tenant_id = ? and p.active and p.team_id in (" + marks + ") order by p.team_id, lower(p.name), p.id",
                PERSON, arguments.toArray());
    }
}
