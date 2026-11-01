package com.company.leadershipintelligence.domain.model;

import java.math.BigDecimal;

/** The configured label for a score and the range that produced it (specv3.md Section 18). */
public record InterpretationResult(String label, BigDecimal rangeMin, BigDecimal rangeMax) {
}
