package com.company.leadershipintelligence.service.identity;

/** Where a request came from, for the security audit only (decision D74). The user agent is cut to 200 characters. */
public record ClientInfo(String ip, String userAgent) {

    public ClientInfo {
        if (userAgent != null && userAgent.length() > 200) {
            userAgent = userAgent.substring(0, 200);
        }
    }

    public static ClientInfo unknown() {
        return new ClientInfo(null, null);
    }
}
