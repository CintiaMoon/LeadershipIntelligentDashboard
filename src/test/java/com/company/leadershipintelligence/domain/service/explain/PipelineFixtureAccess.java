package com.company.leadershipintelligence.domain.service.explain;

import com.company.leadershipintelligence.domain.rule.ScaleConfig;

/** Exposes the shared 0-100 scale configuration of the pipeline fixture to other test packages. */
public final class PipelineFixtureAccess {

    public static final ScaleConfig PERCENT = PipelineFixture.PERCENT;

    private PipelineFixtureAccess() {
    }
}
