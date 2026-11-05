package com.company.leadershipintelligence.api.response;

import java.util.List;

/** What an invitation link offers (decision D72): shown to whoever holds the link. */
public record InvitationPreviewResponse(String companyName, String email, List<String> roles, boolean accountExists) {
}
