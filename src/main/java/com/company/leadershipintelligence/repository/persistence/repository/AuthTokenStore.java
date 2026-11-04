package com.company.leadershipintelligence.repository.persistence.repository;

import com.company.leadershipintelligence.domain.identity.TokenPurpose;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * One-time tokens (decisions D66, D73). The store sees only the hash of a token, never the token. Using a token is one
 * atomic statement, so two requests cannot both succeed with the same token.
 */
@Repository
public class AuthTokenStore {

    private final JdbcTemplate jdbc;

    public AuthTokenStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void insert(UUID userId, TokenPurpose purpose, String tokenHash, Instant expiresAt, Instant at) {
        jdbc.update("insert into auth_token (id, user_id, purpose, token_hash, expires_at, created_at) values (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), userId, purpose.name(), tokenHash, Timestamp.from(expiresAt), Timestamp.from(at));
    }

    /** Retires every unused token of this purpose, so asking again leaves only the newest one valid. */
    @Transactional
    public void revokeOpen(UUID userId, TokenPurpose purpose, Instant at) {
        jdbc.update("update auth_token set used_at = ? where user_id = ? and purpose = ? and used_at is null", Timestamp.from(at), userId, purpose.name());
    }

    /** Uses the token if it is unused, unexpired and for this purpose; returns whose it was, or empty (and changes nothing). */
    @Transactional
    public Optional<UUID> consume(String tokenHash, TokenPurpose purpose, Instant at) {
        Timestamp now = Timestamp.from(at);
        return jdbc.query("update auth_token set used_at = ? where token_hash = ? and purpose = ? and used_at is null and expires_at > ? returning user_id",
                (rs, n) -> rs.getObject(1, UUID.class), now, tokenHash, purpose.name(), now).stream().findFirst();
    }
}
