package com.company.leadershipintelligence.domain.service.interpretation;

import com.company.leadershipintelligence.domain.model.InterpretationResult;
import com.company.leadershipintelligence.domain.model.OverallScore;
import com.company.leadershipintelligence.domain.rule.InterpretationConfig;
import com.company.leadershipintelligence.domain.rule.InterpretationRange;
import com.company.leadershipintelligence.domain.valueobject.ScoreValue;

import java.util.Optional;

/**
 * Resolves configured labels (specv3.md Section 18, decision D6). It reads the already rounded score and never
 * alters it. A result without a numeric score (INSUFFICIENT_DATA) gets no interpretation, so it is never read
 * as a risk category.
 */
public class InterpretationService {

    public InterpretationResult interpret(ScoreValue score, InterpretationConfig config) {
        InterpretationRange range = config.find(score.value())
                .orElseThrow(() -> new IllegalStateException("no interpretation range covers " + score));
        return new InterpretationResult(range.label(), range.min(), range.max());
    }

    public Optional<InterpretationResult> interpret(OverallScore overall, InterpretationConfig config) {
        return overall.score().map(score -> interpret(score, config));
    }
}
