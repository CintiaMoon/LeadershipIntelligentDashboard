package com.company.leadershipintelligence.service;

import com.company.leadershipintelligence.service.query.ScoringRunView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The structured log events of a scoring run (specv3.md Section 34.2): run id, assessment id, engine and configuration
 * version, status, failure category and duration. The correlation id comes from the log context. The tenant id is
 * written only when the logging policy allows it. Raw answers and free text are never logged.
 */
public class ScoringLog {

    private static final Logger LOG = LoggerFactory.getLogger(ScoringLog.class);

    private final boolean includeTenantId;

    public ScoringLog(boolean includeTenantId) {
        this.includeTenantId = includeTenantId;
    }

    public void completed(String tenantId, String source, ScoringRunView run, long durationMs) {
        var event = LOG.atInfo().setMessage("scoring run finished")
                .addKeyValue("scoringRunId", run.id())
                .addKeyValue("assessmentId", run.assessmentId())
                .addKeyValue("source", source)
                .addKeyValue("engineVersion", run.engineVersion())
                .addKeyValue("configurationVersion", run.configurationVersion())
                .addKeyValue("status", run.status())
                .addKeyValue("scoreStatus", run.scoreStatus())
                .addKeyValue("durationMs", durationMs);
        if (includeTenantId) {
            event.addKeyValue("tenantId", tenantId);
        }
        event.log();
    }

    public void failed(String tenantId, String source, String failureCategory, long durationMs) {
        var event = LOG.atWarn().setMessage("scoring run failed")
                .addKeyValue("source", source)
                .addKeyValue("status", "FAILED")
                .addKeyValue("failureCategory", failureCategory)
                .addKeyValue("durationMs", durationMs);
        if (includeTenantId) {
            event.addKeyValue("tenantId", tenantId);
        }
        event.log();
    }
}
