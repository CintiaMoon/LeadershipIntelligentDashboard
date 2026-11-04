package com.company.leadershipintelligence.repository.security;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Ends the server-side sessions of one user (decisions D67, D73): after a password reset or change nobody who knew the old
 * password stays signed in. The user's id is the principal name of every session.
 */
@Component
public class SessionRevoker {

    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionRevoker(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    /** Deletes every session of the user except the one with {@code keepSessionId} (null keeps none); returns how many were ended. */
    public int revokeAll(String principalName, String keepSessionId) {
        Map<String, ? extends Session> found = sessions.findByPrincipalName(principalName);
        int ended = 0;
        for (String id : found.keySet()) {
            if (!id.equals(keepSessionId)) {
                sessions.deleteById(id);
                ended++;
            }
        }
        return ended;
    }
}
