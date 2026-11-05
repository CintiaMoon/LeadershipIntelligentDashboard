package com.company.leadershipintelligence.repository.persistence.repository;

import com.company.leadershipintelligence.domain.identity.AccountStatus;
import com.company.leadershipintelligence.repository.persistence.entity.UserRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Accounts (decision D65). Emails are looked up in their normalized form; a user belongs to no tenant. */
@Repository
public class UserStore {

    private static final String COLUMNS = "id, email, display_name, password_hash, status, email_verified_at, platform_admin, password_changed_at";

    private static final RowMapper<UserRow> MAPPER = UserStore::map;

    private final JdbcTemplate jdbc;

    public UserStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Creates a PENDING_VERIFICATION account; false when the email is already taken (nothing is changed). */
    @Transactional
    public boolean insertPending(UUID id, String email, String displayName, String passwordHash, Instant at) {
        Timestamp now = Timestamp.from(at);
        return jdbc.update("insert into app_user (id, email, display_name, password_hash, status, platform_admin, password_changed_at, created_at, updated_at) "
                + "values (?, ?, ?, ?, 'PENDING_VERIFICATION', false, ?, ?, ?) on conflict do nothing",
                id, email, displayName, passwordHash, now, now, now) == 1;
    }

    /** The latest registration of a still pending address wins: its name and password replace the earlier ones. */
    @Transactional
    public boolean replacePendingCredentials(UUID id, String displayName, String passwordHash, Instant at) {
        Timestamp now = Timestamp.from(at);
        return jdbc.update("update app_user set display_name = ?, password_hash = ?, password_changed_at = ?, updated_at = ? "
                + "where id = ? and status = 'PENDING_VERIFICATION'", displayName, passwordHash, now, now, id) == 1;
    }

    public Optional<UserRow> findByEmail(String normalizedEmail) {
        return jdbc.query("select " + COLUMNS + " from app_user where lower(email) = ?", MAPPER, normalizedEmail).stream().findFirst();
    }

    public Optional<UserRow> findById(UUID id) {
        return jdbc.query("select " + COLUMNS + " from app_user where id = ?", MAPPER, id).stream().findFirst();
    }

    /** Marks the email verified and the account ACTIVE; a DISABLED account stays disabled. False when nothing changed. */
    @Transactional
    public boolean activate(UUID id, Instant at) {
        Timestamp now = Timestamp.from(at);
        return jdbc.update("update app_user set status = 'ACTIVE', email_verified_at = coalesce(email_verified_at, ?), updated_at = ? "
                + "where id = ? and status = 'PENDING_VERIFICATION'", now, now, id) == 1;
    }

    @Transactional
    public void updatePassword(UUID id, String passwordHash, Instant at) {
        Timestamp now = Timestamp.from(at);
        jdbc.update("update app_user set password_hash = ?, password_changed_at = ?, updated_at = ? where id = ?", passwordHash, now, now, id);
    }

    private static UserRow map(ResultSet rs, int n) throws SQLException {
        Timestamp verified = rs.getTimestamp("email_verified_at");
        return new UserRow(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name"), rs.getString("password_hash"),
                AccountStatus.valueOf(rs.getString("status")), verified == null ? null : verified.toInstant(), rs.getBoolean("platform_admin"),
                rs.getTimestamp("password_changed_at").toInstant());
    }
}
