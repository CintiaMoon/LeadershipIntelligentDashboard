package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.service.identity.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import com.company.leadershipintelligence.api.security.AuthenticatedCaller;

import java.util.UUID;

/** Who is calling, for the endpoints that only a signed-in person may use. */
final class Callers {

    private Callers() {
    }

    /**
     * The signed-in person behind the request (the subject is their account id). An API token, or a caller that is not an account of
     * this application, is refused: tokens never manage tokens or members.
     */
    static UUID person(AuthenticatedCaller caller) {
        try {
            return UUID.fromString(caller.subject());
        } catch (RuntimeException e) {
            throw new AccessDeniedException("this needs a signed-in session");
        }
    }

    static ClientInfo client(HttpServletRequest request) {
        return new ClientInfo(request.getRemoteAddr(), request.getHeader("User-Agent"));
    }
}
