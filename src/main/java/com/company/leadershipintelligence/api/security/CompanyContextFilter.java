package com.company.leadershipintelligence.api.security;

import com.company.leadershipintelligence.api.exception.ApiErrorWriter;
import com.company.leadershipintelligence.service.identity.ClientInfo;
import com.company.leadershipintelligence.service.identity.CompanyMembership;
import com.company.leadershipintelligence.service.identity.SessionAccount;
import com.company.leadershipintelligence.service.identity.MembershipService;
import com.company.leadershipintelligence.service.identity.OrganizationReach;
import com.company.leadershipintelligence.service.identity.SecurityAudit;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns a signed-in session into the caller the rest of the system already understands (decisions D67, D69). The session names a
 * user and nothing else; this filter reads the user and the membership on every request and then builds the same {@link Jwt}-shaped
 * caller that a verified token used to provide, so {@link CurrentUser}, the role rules, the tenant-scoped repositories and the
 * audit work unchanged. That object is internal: it is never signed, sent or accepted from a client.
 *
 * <ul>
 *   <li>{@code /api/v1/platform/**}: only a platform administrator (the flag is read now, not from the session); no company;</li>
 *   <li>{@code /api/v1/companies}: the user alone;</li>
 *   <li>every other {@code /api/v1} endpoint: the company comes only from the {@code X-Company-Id} header and only when the user
 *       has an ACTIVE membership there ({@code COMPANY_ACCESS_DENIED} otherwise, the same for a company that does not exist).
 *       A platform administrator has no company and is refused.</li>
 * </ul>
 *
 * A request with a bearer token is left alone: it is already a caller. A session whose account is no longer ACTIVE gets 401.
 */
public class CompanyContextFilter extends OncePerRequestFilter {

    public static final String COMPANY_HEADER = "X-Company-Id";
    private static final Duration LIFETIME = Duration.ofMinutes(5);

    private final MembershipService memberships;
    private final ApiErrorWriter errors;
    private final SecurityAudit audit;
    private final Clock clock;

    public CompanyContextFilter(MembershipService memberships, ApiErrorWriter errors, SecurityAudit audit, Clock clock) {
        this.memberships = memberships;
        this.errors = errors;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        String path = request.getRequestURI();
        if (current == null || !(current.getPrincipal() instanceof UserPrincipal principal) || !path.startsWith("/api/v1/")) {
            chain.doFilter(request, response);
            return;
        }
        SessionAccount user = memberships.activeUser(principal.userId()).orElse(null);
        if (user == null) {
            SecurityContextHolder.clearContext();
            errors.unauthenticated(request, response, new InsufficientAuthenticationException("the account is no longer active"));
            return;
        }
        if (path.equals("/api/v1/companies")) {
            chain.doFilter(request, response);
            return;
        }
        if (path.startsWith("/api/v1/platform")) {
            if (!user.platformAdmin()) {
                errors.accessDenied(request, response, new AccessDeniedException("only a platform administrator may do this"));
                return;
            }
            replace(token(user, null, List.of(PlatformRole.PLATFORM_ADMIN), List.of(), false), List.of(new SimpleGrantedAuthority("ROLE_" + PlatformRole.PLATFORM_ADMIN)));
            chain.doFilter(request, response);
            return;
        }
        if (user.platformAdmin()) {
            // a platform administrator has no company; tenant endpoints stay closed to it
            errors.accessDenied(request, response, new AccessDeniedException("a platform administrator has no company"));
            return;
        }
        String company = request.getHeader(COMPANY_HEADER);
        if (company == null || company.isBlank()) {
            errors.companyRequired(response);
            return;
        }
        CompanyMembership membership = memberships.access(user.id(), company.trim()).orElse(null);
        if (membership == null) {
            audit.record("COMPANY_ACCESS_DENIED", SecurityAudit.FAILURE, user.id(), null, null, new ClientInfo(request.getRemoteAddr(), request.getHeader("User-Agent")), Map.of());
            errors.companyDenied(response);
            return;
        }
        List<String> roles = List.copyOf(membership.roles());
        List<GrantedAuthority> authorities = new ArrayList<>();
        roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
        OrganizationReach reach = memberships.reach(membership.tenantId(), membership.organizationIds());
        replace(token(user, membership.tenantId(), roles, List.copyOf(reach.organizationIds()), reach.all()), authorities);
        chain.doFilter(request, response);
    }

    private Jwt token(SessionAccount user, String tenantId, List<String> roles, List<String> organizations, boolean allOrganizations) {
        Instant now = clock.instant();
        Jwt.Builder builder = Jwt.withTokenValue(CurrentUser.INTERNAL_TOKEN_VALUE).header("alg", "none").subject(user.id().toString())
                .claim("email", user.email()).claim(CurrentUser.ROLES_CLAIM, roles)
                .claim(CurrentUser.ORGANIZATIONS_CLAIM, organizations).claim(CurrentUser.ALL_ORGANIZATIONS_CLAIM, allOrganizations)
                .issuedAt(now).expiresAt(now.plus(LIFETIME));
        if (tenantId != null) {
            builder.claim(CurrentUser.TENANT_CLAIM, tenantId);
        }
        return builder.build();
    }

    private void replace(Jwt jwt, List<? extends GrantedAuthority> authorities) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, authorities, jwt.getSubject()));
        SecurityContextHolder.setContext(context);
    }
}
