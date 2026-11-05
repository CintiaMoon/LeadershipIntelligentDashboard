package com.company.leadershipintelligence.api.security;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import org.springframework.security.access.AccessDeniedException;

import java.util.Locale;

/**
 * A person who answers a questionnaire (decision D48). The role is deliberately not a value of {@link Role}: the rules that let
 * "every role" read tenant data are built from that enum, and a respondent must never be part of them. It reaches only
 * {@code /api/v1/respond/**}. The person is the roster entry whose e-mail equals the verified email of the account within the
 * caller's company; nothing from a request identifies the respondent.
 */
public final class RespondentRole {

    public static final String RESPONDENT = "RESPONDENT";

    private RespondentRole() {
    }

    /** Defence in depth behind the request rule. */
    public static void require(AuthenticatedCaller caller) {
        if (!caller.hasRole(RESPONDENT)) {
            throw new AccessDeniedException("only a respondent may do this");
        }
    }

    /** The verified e-mail, lower-cased the way the roster stores it. */
    public static String email(AuthenticatedCaller caller) {
        if (caller.email() == null || caller.email().isBlank()) {
            throw new ScoringException(ScoringErrorCode.TENANT_ACCESS_DENIED, "the caller has no e-mail address");
        }
        return caller.email().trim().toLowerCase(Locale.ROOT);
    }
}
