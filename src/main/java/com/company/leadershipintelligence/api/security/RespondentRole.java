package com.company.leadershipintelligence.api.security;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Locale;

/**
 * A person who answers a questionnaire (decision D48). The role is deliberately not a value of {@link Role}: the rules that let
 * "every role" read tenant data are built from that enum, and a respondent must never be part of them. It reaches only
 * {@code /api/v1/respond/**}. The person is the roster entry whose e-mail equals the verified {@code email} claim of the token
 * within the token's tenant; nothing from a request identifies the respondent.
 */
public final class RespondentRole {

    public static final String RESPONDENT = "RESPONDENT";
    public static final String EMAIL_CLAIM = "email";

    private RespondentRole() {
    }

    /** Defence in depth behind the request rule. */
    public static void require(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(CurrentUser.ROLES_CLAIM);
        if (roles == null || !roles.contains(RESPONDENT)) {
            throw new AccessDeniedException("only a respondent may do this");
        }
    }

    /** The verified e-mail, lower-cased the way the roster stores it. */
    public static String email(Jwt jwt) {
        String email = jwt.getClaimAsString(EMAIL_CLAIM);
        if (email == null || email.isBlank()) {
            throw new ScoringException(ScoringErrorCode.TENANT_ACCESS_DENIED, "the token carries no e-mail address");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
