package com.company.leadershipintelligence.api.response;

/** Convenience counts for screens; they are read from the stored findings, never calculated separately. */
public record CountsResponse(int matchedPatterns, int appliedPenalties, int suppressedPenalties) {
}
