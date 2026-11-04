package com.company.leadershipintelligence.domain.identity;

/** What a one-time token may be used for. A token never works for another purpose. */
public enum TokenPurpose {
    VERIFY_EMAIL,
    RESET_PASSWORD
}
