package com.company.leadershipintelligence.service.command;

import java.time.Instant;

/** Approval metadata required to activate a version (specv3.md Section 23.6). A null {@code effectiveFrom} means now. */
public record Approval(String approvedBy, Instant effectiveFrom) {
}
