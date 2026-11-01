package com.company.leadershipintelligence.service.query;

import java.util.List;

/** Outcome of validating a configuration version. Problems are kept so a failed activation can be explained. */
public record ValidationResult(boolean valid, List<String> problems) {

    public ValidationResult {
        problems = List.copyOf(problems);
    }
}
