package com.company.leadershipintelligence.api.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/** The security context's view of an {@link AuthenticatedCaller}: authenticated, with one authority {@code ROLE_<name>} per role. */
public final class CallerAuthentication extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    private final transient AuthenticatedCaller caller;

    public CallerAuthentication(AuthenticatedCaller caller) {
        super(authorities(caller));
        this.caller = caller;
        setAuthenticated(true);
    }

    private static List<GrantedAuthority> authorities(AuthenticatedCaller caller) {
        return caller.roles().stream().sorted().<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public AuthenticatedCaller getPrincipal() {
        return caller;
    }

    @Override
    public String getName() {
        return caller.subject() == null ? "unknown" : caller.subject();
    }
}
