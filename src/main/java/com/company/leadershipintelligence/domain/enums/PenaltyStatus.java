package com.company.leadershipintelligence.domain.enums;

/** Exactly one terminal status per penalty candidate (specv3.md Section 16.4, decision D7). */
public enum PenaltyStatus {
    APPLIED,
    SUPPRESSED,
    INELIGIBLE,
    NOT_TRIGGERED
}
