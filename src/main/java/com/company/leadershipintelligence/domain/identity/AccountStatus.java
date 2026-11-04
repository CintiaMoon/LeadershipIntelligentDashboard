package com.company.leadershipintelligence.domain.identity;

/** Where an account is in its life (decision D65). Only an ACTIVE account can sign in; ACTIVE implies a verified email. */
public enum AccountStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    DISABLED
}
