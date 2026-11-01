package com.company.leadershipintelligence.api.exception;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorStatusTest {

    @Test
    void everyDomainErrorCodeHasAStatus() {
        for (ScoringErrorCode code : ScoringErrorCode.values()) {
            assertThat(ErrorStatus.of(code)).as(code.name()).isNotNull();
        }
    }

    @Test
    void theDocumentedMapping() {
        assertThat(ErrorStatus.of(ScoringErrorCode.INVALID_SCALE_VALUE)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorStatus.of(ScoringErrorCode.QUESTION_NOT_CONFIGURED)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorStatus.of(ScoringErrorCode.ASSESSMENT_NOT_COMPLETED)).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorStatus.of(ScoringErrorCode.ASSESSMENT_NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorStatus.of(ScoringErrorCode.SCORING_RUN_NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorStatus.of(ScoringErrorCode.CONFIGURATION_VERSION_NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorStatus.of(ScoringErrorCode.IDEMPOTENCY_CONFLICT)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorStatus.of(ScoringErrorCode.CONFIGURATION_NOT_ACTIVE)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorStatus.of(ScoringErrorCode.TENANT_CONTEXT_MISSING)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorStatus.of(ScoringErrorCode.TENANT_ACCESS_DENIED)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorStatus.of(ScoringErrorCode.ORGANIZATION_ACCESS_DENIED)).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void configurationAndPersistenceFaultsAreServerFaults() {
        for (ScoringErrorCode code : new ScoringErrorCode[]{ScoringErrorCode.INVALID_PENALTY_CONFIGURATION, ScoringErrorCode.INVALID_CONDITION_EXPRESSION,
                ScoringErrorCode.CONFIGURATION_SNAPSHOT_INVALID, ScoringErrorCode.SCORING_EXECUTION_FAILED, ScoringErrorCode.PERSISTENCE_FAILURE}) {
            assertThat(ErrorStatus.isServerFault(code)).as(code.name()).isTrue();
        }
        assertThat(ErrorStatus.isServerFault(ScoringErrorCode.INVALID_SCALE_VALUE)).isFalse();
    }
}
