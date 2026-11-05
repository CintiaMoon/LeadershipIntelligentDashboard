package com.company.leadershipintelligence.service.identity;

import java.util.Set;

/**
 * What the holder of an invitation link is told before accepting: the company, the address it was sent to, the roles on offer, and
 * whether that address already has an account (so the page can ask them to sign in or to register).
 */
public record InvitationPreview(String companyName, String email, Set<String> roles, boolean accountExists) {
}
