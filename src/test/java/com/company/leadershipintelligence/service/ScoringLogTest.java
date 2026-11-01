package com.company.leadershipintelligence.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.company.leadershipintelligence.domain.enums.RunStatus;
import com.company.leadershipintelligence.domain.enums.ScoreStatus;
import com.company.leadershipintelligence.service.query.ScoringRunView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringLogTest {

    private final Logger logger = (Logger) LoggerFactory.getLogger(ScoringLog.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attach() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(appender);
    }

    private static ScoringRunView run() {
        return new ScoringRunView(UUID.randomUUID(), "asm-1", "snp-1", "org-1", "CALCULATE", "DEFAULT", "3.0.0", "1.0.0", "platform_default:v1",
                "sha256:c", "sha256:i", "1", RunStatus.COMPLETED, ScoreStatus.VALID, new BigDecimal("67.00"), new BigDecimal("67.00"),
                BigDecimal.ZERO, "HEALTHY", "[]", "{}", null, Instant.now(), Instant.now());
    }

    private String keys(ILoggingEvent event) {
        return event.getKeyValuePairs().stream().map(p -> p.key + "=" + p.value).collect(Collectors.joining(","));
    }

    @Test
    void aFinishedRunLogsTheSpecifiedFieldsWithoutTheTenantByDefault() {
        new ScoringLog(false).completed("tenant-secret", "SCORE", run(), 12);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getFormattedMessage()).isEqualTo("scoring run finished");
        assertThat(keys(event)).contains("scoringRunId=", "assessmentId=asm-1", "source=SCORE", "engineVersion=3.0.0",
                "configurationVersion=platform_default:v1", "status=COMPLETED", "scoreStatus=VALID", "durationMs=12");
        assertThat(keys(event)).doesNotContain("tenantId").doesNotContain("tenant-secret");
    }

    @Test
    void theTenantIsLoggedOnlyWhenThePolicyAllowsIt() {
        new ScoringLog(true).completed("tenant-1", "SCORE", run(), 1);
        new ScoringLog(true).failed("tenant-1", "CALCULATE", "QUESTION_NOT_CONFIGURED", 2);
        assertThat(keys(appender.list.get(0))).contains("tenantId=tenant-1");
        assertThat(keys(appender.list.get(1))).contains("tenantId=tenant-1");
    }

    @Test
    void aFailureLogsTheCategoryAndDurationOnly() {
        new ScoringLog(false).failed("tenant-secret", "CALCULATE", "INVALID_SCALE_VALUE", 7);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getFormattedMessage()).isEqualTo("scoring run failed");
        assertThat(keys(event)).contains("failureCategory=INVALID_SCALE_VALUE", "durationMs=7", "status=FAILED", "source=CALCULATE");
        assertThat(keys(event)).doesNotContain("tenant-secret");
    }
}
