package com.company.leadershipintelligence.api.security;

import org.springframework.security.core.AuthenticatedPrincipal;

import java.io.Serializable;
import java.util.UUID;

/**
 * The principal of a signed-in session (decisions D65, D67): the user's id and nothing else. It names the user, never a
 * company, and it is what the session store keeps, so it stays small and serializable. {@link #getName()} is the user id,
 * which is also how a user's sessions are found when they must all be ended.
 */
public record UserPrincipal(UUID userId) implements AuthenticatedPrincipal, Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return userId.toString();
    }
}
