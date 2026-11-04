package com.company.leadershipintelligence.api.response;

/** Where the web application finds the CSRF token (decision D68): send {@code token} in the header named {@code headerName}. */
public record CsrfResponse(String headerName, String token) {
}
