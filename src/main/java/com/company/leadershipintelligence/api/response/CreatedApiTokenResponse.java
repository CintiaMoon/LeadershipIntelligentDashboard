package com.company.leadershipintelligence.api.response;

/** A token just created, with its secret, shown this once. The secret is not stored and cannot be read again. */
public record CreatedApiTokenResponse(ApiTokenResponse token, String secret) {

    @Override
    public String toString() {
        return "CreatedApiTokenResponse[" + token.id() + ", secret redacted]";
    }
}
