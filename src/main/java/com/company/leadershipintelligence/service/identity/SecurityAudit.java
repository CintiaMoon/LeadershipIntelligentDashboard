package com.company.leadershipintelligence.service.identity;

import com.company.leadershipintelligence.config.properties.IdentityProperties;
import com.company.leadershipintelligence.repository.persistence.repository.SecurityAuditStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Writes security events (decision D74). A row is written in its own transaction, so the record of a refused sign-in or a
 * rejected token survives the rollback of the request that caused it. Details are short labels only: never a password, a
 * token or a cookie. An email that belongs to no account is stored as a keyed hash, not in clear.
 */
@Service
public class SecurityAudit {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityAudit.class);

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILURE = "FAILURE";

    private final SecurityAuditStore store;
    private final Clock clock;
    private final ObjectMapper json;
    private final byte[] hashKey;

    public SecurityAudit(SecurityAuditStore store, Clock clock, ObjectMapper json, IdentityProperties properties) {
        this.store = store;
        this.clock = clock;
        this.json = json;
        if (properties.auditHashKey() == null || properties.auditHashKey().isBlank()) {
            this.hashKey = new byte[32];
            new SecureRandom().nextBytes(hashKey);
        } else {
            this.hashKey = properties.auditHashKey().getBytes(StandardCharsets.UTF_8);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String eventType, String outcome, UUID userId, String tenantId, String emailForHash, ClientInfo client, Map<String, String> detail) {
        ClientInfo from = client == null ? ClientInfo.unknown() : client;
        store.record(clock.instant(), eventType, outcome, userId, tenantId, emailForHash == null ? null : keyedHash(emailForHash),
                from.ip(), from.userAgent(), detailJson(detail));
    }

    public void success(String eventType, UUID userId, ClientInfo client) {
        record(eventType, SUCCESS, userId, null, null, client, Map.of());
    }

    private String keyedHash(String email) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(email.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    private String detailJson(Map<String, String> detail) {
        if (detail == null || detail.isEmpty()) {
            return "{}";
        }
        try {
            return json.writeValueAsString(new TreeMap<>(detail));
        } catch (JsonProcessingException e) {
            LOG.warn("security audit detail could not be written type={}", e.getClass().getSimpleName());
            return "{}";
        }
    }
}
