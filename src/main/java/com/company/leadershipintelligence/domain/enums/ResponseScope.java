package com.company.leadershipintelligence.domain.enums;

/**
 * Who an answer represents (specv3.md Section 4.10, decision D45). A member answer is one respondent whose
 * identity is not part of the model; team and organization answers are figures already combined by the caller.
 */
public enum ResponseScope {
    MEMBER,
    TEAM,
    ORGANIZATION
}
