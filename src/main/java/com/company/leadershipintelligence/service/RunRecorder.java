package com.company.leadershipintelligence.service;

import com.company.leadershipintelligence.repository.persistence.entity.AnswerRow;
import com.company.leadershipintelligence.repository.persistence.entity.AssessmentRow;
import com.company.leadershipintelligence.repository.persistence.entity.NewFailedRun;
import com.company.leadershipintelligence.repository.persistence.entity.NewRun;
import com.company.leadershipintelligence.repository.persistence.entity.SnapshotRow;
import com.company.leadershipintelligence.repository.persistence.repository.AssessmentStore;
import com.company.leadershipintelligence.repository.persistence.repository.ScoringRunStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The transaction boundaries of persistence (specv3.md Section 26.9). Each method is one transaction, so a
 * failure rolls back everything it wrote: no assessment without its run, no run without its findings. Callers
 * decide how to react to a duplicate; this bean is deliberately not called from inside another transaction.
 */
@Service
public class RunRecorder {

    private final AssessmentStore assessments;
    private final ScoringRunStore runs;

    public RunRecorder(AssessmentStore assessments, ScoringRunStore runs) {
        this.assessments = assessments;
        this.runs = runs;
    }

    @Transactional
    public void recordCalculation(String tenantId, String organizationId, String templateId, int templateVersion,
                                  AssessmentRow assessment, SnapshotRow snapshot, List<AnswerRow> answers, NewRun run) {
        recordCalculation(tenantId, organizationId, templateId, templateVersion, assessment, snapshot, answers, run, List.of());
    }

    /** The organization run and its team runs are written in one transaction: all of them or none (decision D45). */
    @Transactional
    public void recordCalculation(String tenantId, String organizationId, String templateId, int templateVersion,
                                  AssessmentRow assessment, SnapshotRow snapshot, List<AnswerRow> answers, NewRun run,
                                  List<NewRun> teamRuns) {
        assessments.register(tenantId, organizationId, templateId, templateVersion);
        assessments.insertAssessment(assessment);
        assessments.insertSnapshot(snapshot, answers);
        runs.insert(run);
        teamRuns.forEach(runs::insert);
    }

    @Transactional
    public void recordRun(NewRun run) {
        runs.insert(run);
    }

    /** An organization run first, then the team runs that point to it, in one transaction. */
    @Transactional
    public void recordRuns(NewRun run, List<NewRun> teamRuns) {
        runs.insert(run);
        teamRuns.forEach(runs::insert);
    }

    /** Binds an idempotency key to an existing run that answered the request. */
    @Transactional
    public void bindKey(String tenantId, String key, String requestHash, java.util.UUID runId) {
        runs.bindIdempotencyKey(tenantId, key, requestHash, runId);
    }

    @Transactional
    public void recordFailure(NewFailedRun failed) {
        runs.insertFailed(failed);
    }
}
