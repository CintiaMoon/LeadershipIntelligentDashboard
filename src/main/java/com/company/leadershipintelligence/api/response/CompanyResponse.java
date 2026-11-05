package com.company.leadershipintelligence.api.response;

import java.util.List;

/**
 * A company the signed-in user may open (decision D69): the roles they hold there and the organizations they reach. With
 * {@code allOrganizations} the user is not limited to a list: {@code organizationIds} then holds the organizations that exist now,
 * and a new one may still be named (organizations are created by first use). Only a convenience for the interface; the server decides.
 */
public record CompanyResponse(String id, String name, List<String> roles, List<String> organizationIds, boolean allOrganizations) {
}
