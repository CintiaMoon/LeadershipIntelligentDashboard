package com.company.leadershipintelligence.api.response;

import java.util.Map;

/** The error body (specv3.md Section 28.10). It never carries a stack trace, configuration internals or raw answers. */
public record ApiError(String code, String message, Map<String, String> details, String correlationId) {
}
