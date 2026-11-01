package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.ResponseScope;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * One answer together with who it represents (specv3.md Section 4.10, decision D45). There is no respondent
 * identity: a member answer is only a position in a set of answers. A team is an opaque identifier inside the
 * organization.
 *
 * @param teamId              required for TEAM, optional for MEMBER (an answer without a team counts only for the
 *                            organization), forbidden for ORGANIZATION
 * @param declaredRespondents how many people a TEAM or ORGANIZATION figure represents, as claimed by the caller
 */
public record ScopedResponse(String questionId, Object answer, ResponseScope scope, String teamId, Integer declaredRespondents) {

    private static final Pattern TEAM_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    public ScopedResponse {
        if (questionId == null || questionId.isBlank() || scope == null) {
            throw new IllegalArgumentException("questionId and scope are required");
        }
        if (scope == ResponseScope.TEAM && teamId == null) {
            throw invalid("a TEAM answer needs a teamId", questionId);
        }
        if (scope == ResponseScope.ORGANIZATION && teamId != null) {
            throw invalid("an ORGANIZATION answer cannot name a team", questionId);
        }
        if (teamId != null && !TEAM_ID.matcher(teamId).matches()) {
            throw invalid("teamId must use 1 to 64 letters, digits, dot, underscore or hyphen", questionId);
        }
        if (declaredRespondents != null) {
            if (scope == ResponseScope.MEMBER) {
                throw invalid("a MEMBER answer is one respondent and cannot declare a respondent count", questionId);
            }
            if (declaredRespondents < 1) {
                throw invalid("declaredRespondents must be at least 1", questionId);
            }
        }
    }

    public static ScopedResponse organization(String questionId, Object answer) {
        return new ScopedResponse(questionId, answer, ResponseScope.ORGANIZATION, null, null);
    }

    public static ScopedResponse member(String questionId, Object answer, String teamId) {
        return new ScopedResponse(questionId, answer, ResponseScope.MEMBER, teamId, null);
    }

    public static ScopedResponse team(String questionId, Object answer, String teamId, Integer declaredRespondents) {
        return new ScopedResponse(questionId, answer, ResponseScope.TEAM, teamId, declaredRespondents);
    }

    /**
     * Whether this answer takes part in a scoring unit: the organization (null team) takes every answer; a team takes
     * that team's member and team answers and never an organization-level figure.
     */
    public boolean belongsToUnit(String unitTeamId) {
        if (unitTeamId == null) {
            return true;
        }
        return scope != ResponseScope.ORGANIZATION && unitTeamId.equals(teamId);
    }

    public ScoringInput asInput() {
        return new ScoringInput(questionId, answer);
    }

    private static ScoringException invalid(String message, String questionId) {
        return new ScoringException(ScoringErrorCode.INVALID_RESPONSE_SCOPE, message, Map.of("questionId", questionId));
    }
}
