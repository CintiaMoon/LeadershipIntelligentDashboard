package com.company.leadershipintelligence.domain.identity;

/** Where a company membership stands (decision D69). Only ACTIVE gives access to the company. */
public enum MembershipStatus {
    INVITED,
    ACTIVE,
    SUSPENDED,
    REMOVED
}
