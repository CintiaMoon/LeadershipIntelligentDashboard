package com.company.leadershipintelligence.api.exception;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import org.springframework.http.HttpStatus;

/**
 * The mapping from domain error codes to HTTP statuses (specv3.md Section 29.3, API contract section 5). Every code
 * is listed; configuration problems found while scoring are server-side faults (500) and are not described to the caller.
 */
public final class ErrorStatus {

    private ErrorStatus() {
    }

    public static HttpStatus of(ScoringErrorCode code) {
        return switch (code) {
            case QUESTION_NOT_CONFIGURED, UNSUPPORTED_QUESTION_TYPE, INVALID_ANSWER_TYPE, INVALID_SCALE_VALUE, INVALID_NUMBER_VALUE,
                 INVALID_MULTIPLE_CHOICE_VALUE, MISSING_REQUIRED_INPUT, DUPLICATE_QUESTION_INPUT, ASSESSMENT_NOT_COMPLETED,
                 MIXED_RESPONSE_SCOPES, DUPLICATE_TEAM_SCOPE, INVALID_RESPONSE_SCOPE, INVALID_TENANT, QUESTIONNAIRE_INVALID, ROSTER_INVALID, RUN_INVALID
                    -> HttpStatus.UNPROCESSABLE_ENTITY;
            case ASSESSMENT_NOT_FOUND, ASSESSMENT_SNAPSHOT_NOT_FOUND, SCORING_RUN_NOT_FOUND, CONFIGURATION_VERSION_NOT_FOUND,
                 QUESTIONNAIRE_NOT_FOUND, ROSTER_NOT_FOUND, RUN_NOT_FOUND
                    -> HttpStatus.NOT_FOUND;
            case CONFIGURATION_NOT_ACTIVE, IDEMPOTENCY_CONFLICT, INVALID_CONFIGURATION_VERSION, TENANT_ALREADY_EXISTS,
                 QUESTIONNAIRE_STATE, ROSTER_CONFLICT, RUN_STATE -> HttpStatus.CONFLICT;
            case TENANT_CONTEXT_MISSING -> HttpStatus.UNAUTHORIZED;
            case TENANT_ACCESS_DENIED, ORGANIZATION_ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case INVALID_NORMALIZATION_CONFIGURATION, INVALID_SIGNAL_CONFIGURATION, INVALID_WEIGHT_CONFIGURATION,
                 INVALID_COVERAGE_CONFIGURATION, INVALID_RESPONSE_CONFIGURATION, INVALID_CONDITION_EXPRESSION, INVALID_PATTERN_CONFIGURATION,
                 INVALID_PENALTY_CONFIGURATION, INVALID_IMBALANCE_CONFIGURATION, INVALID_INTERPRETATION_CONFIGURATION,
                 CONFIGURATION_SNAPSHOT_INVALID, CONFIGURATION_ACTIVATION_FAILED, SCORING_EXECUTION_FAILED, PERSISTENCE_FAILURE
                    -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /** Configuration faults are not explained to API callers; the code is kept so support can find the cause. */
    public static boolean isServerFault(ScoringErrorCode code) {
        return of(code) == HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
