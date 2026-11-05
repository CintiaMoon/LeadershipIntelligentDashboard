package com.company.leadershipintelligence.service.identity;

/** A token just created, with its secret. This is the only time the secret exists outside the caller: it is not stored. */
public record IssuedApiToken(ApiTokenView token, String secret) {

    @Override
    public String toString() {
        return "IssuedApiToken[" + token.id() + ", secret redacted]";
    }
}
