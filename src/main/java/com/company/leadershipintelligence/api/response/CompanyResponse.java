package com.company.leadershipintelligence.api.response;

import java.util.List;

/** A company the signed-in user may open, with the roles they hold there (decision D69). */
public record CompanyResponse(String id, String name, List<String> roles) {
}
