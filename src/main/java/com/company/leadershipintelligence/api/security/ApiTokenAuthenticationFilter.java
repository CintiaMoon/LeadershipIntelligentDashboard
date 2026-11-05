package com.company.leadershipintelligence.api.security;

import com.company.leadershipintelligence.api.exception.ApiErrorWriter;
import com.company.leadershipintelligence.domain.exception.RateLimitedException;
import com.company.leadershipintelligence.service.identity.ApiTokenCaller;
import com.company.leadershipintelligence.service.identity.ApiTokenService;
import com.company.leadershipintelligence.service.identity.ClientInfo;
import com.company.leadershipintelligence.service.identity.MembershipService;
import com.company.leadershipintelligence.service.identity.OrganizationReach;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates a request that carries an application API token (decision D70): {@code Authorization: Bearer lis_...}. The token
 * is looked up by hash on every request and gives the caller its one company, role and organizations, in the same shape a session
 * and a verified external token give, so nothing downstream changes. A token needs no company header and no CSRF token (it is not an
 * ambient credential). A token that is not valid, whatever the reason, is a plain 401; one over its call limit is a 429.
 *
 * <p>A bearer value that does not start with the token prefix is left to the external-token path.
 */
public class ApiTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final ApiTokenService tokens;
    private final MembershipService memberships;
    private final ApiErrorWriter errors;
    private final Clock clock;

    public ApiTokenAuthenticationFilter(ApiTokenService tokens, MembershipService memberships, ApiErrorWriter errors, Clock clock) {
        this.memberships = memberships;
        this.tokens = tokens;
        this.errors = errors;
        this.clock = clock;
    }

    /** The API token in the request, or null when the request carries none (including a bearer value of the other kind). */
    public static String tokenOf(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || header.length() <= BEARER.length() || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }
        String value = header.substring(BEARER.length()).trim();
        return value.startsWith(ApiTokenService.PREFIX) ? value : null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String secret = tokenOf(request);
        if (secret == null) {
            chain.doFilter(request, response);
            return;
        }
        Optional<ApiTokenCaller> caller;
        try {
            caller = tokens.authenticate(secret, new ClientInfo(request.getRemoteAddr(), request.getHeader("User-Agent")));
        } catch (RateLimitedException e) {
            SecurityContextHolder.clearContext();
            errors.rateLimited(response, e);
            return;
        }
        if (caller.isEmpty()) {
            SecurityContextHolder.clearContext();
            errors.unauthenticated(request, response, new BadCredentialsException("the API token is not valid"));
            return;
        }
        ApiTokenCaller c = caller.get();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        // a token without an organization list reaches every organization of its company, like the membership of its creator
        OrganizationReach reach = memberships.reach(c.tenantId(), c.organizationIds());
        context.setAuthentication(CallerTokens.caller(c.subject(), null, c.tenantId(), List.of(c.role()), List.copyOf(reach.organizationIds()), reach.all(), clock));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }
}
