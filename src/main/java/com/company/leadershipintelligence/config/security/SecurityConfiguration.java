package com.company.leadershipintelligence.config.security;

import com.company.leadershipintelligence.api.exception.ApiErrorWriter;
import com.company.leadershipintelligence.api.security.CompanyContextFilter;
import com.company.leadershipintelligence.service.identity.MembershipService;
import com.company.leadershipintelligence.service.identity.SecurityAudit;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import com.company.leadershipintelligence.config.properties.IdentityProperties;
import com.company.leadershipintelligence.api.security.PlatformRole;
import com.company.leadershipintelligence.api.security.RespondentRole;
import com.company.leadershipintelligence.api.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Authentication and authorization (specv3.md Sections 25.5, 33; decision D19). The service is an OAuth2 resource
 * server: it validates tokens issued by the platform and implements no login of its own. Every endpoint needs a
 * token. Writes need OWNER, ADMIN or ENGINEERING_LEADER; reads need any of the five roles. Sessions are not used.
 * Without a configured key set the decoder rejects every token (fail closed).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfiguration.class);

    /**
     * Accounts and sessions (decisions D65 to D68): {@code /api/v1/auth/**} uses a server-side session, the session cookie and CSRF
     * protection. The session is created only by the sign-in endpoint. Everything else keeps using the bearer-token chain below
     * until the company memberships of a later phase move it to sessions too.
     */
    @Bean
    @Order(1)
    SecurityFilterChain authFilterChain(HttpSecurity http, ApiErrorWriter errors, IdentityProperties identity, Clock clock,
                                        SecurityContextRepository contexts, CookieCsrfTokenRepository csrfTokens) throws Exception {
        // the plain handler (not the masked one) is what a single-page app can read from the cookie and send back
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        http.securityMatcher("/api/v1/auth/**")
                .csrf(c -> c.csrfTokenRepository(csrfTokens).csrfTokenRequestHandler(csrfHandler))
                .securityContext(c -> c.securityContextRepository(contexts))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.NEVER))
                // an API never redirects, so a refused request is not remembered (that would create a session for an anonymous caller)
                .requestCache(c -> c.requestCache(new org.springframework.security.web.savedrequest.NullRequestCache()))
                .addFilterBefore(new AbsoluteSessionTimeoutFilter(identity.sessionAbsolute(), clock), SecurityContextHolderFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/verify-email", "/api/v1/auth/resend-verification",
                                "/api/v1/auth/login", "/api/v1/auth/logout", "/api/v1/auth/password/forgot", "/api/v1/auth/password/reset").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/invitations/accept").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/password/change").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errors::unauthenticated)
                        .accessDeniedHandler((request, response, denied) -> {
                            if (denied instanceof CsrfException) {
                                errors.csrfRejected(request, response, denied);
                            } else {
                                errors.accessDenied(request, response, denied);
                            }
                        }));
        return http.build();
    }

    /** Where the signed-in user is kept between requests: the server-side session (Spring Session JDBC). */
    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** The CSRF token lives in a cookie the web application can read and sends back in a header (decision D68). */
    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    @Order(2)
    SecurityFilterChain filterChain(HttpSecurity http, ApiErrorWriter errors, MembershipService memberships, SecurityAudit audit, IdentityProperties identity,
                                    Clock clock, CookieCsrfTokenRepository csrfTokens, @Value("${server.servlet.session.cookie.name:JSESSIONID}") String sessionCookie) throws Exception {
        // sessions are read, never created, here; a request with a bearer token is not a session request
        HttpSessionSecurityContextRepository sessions = new HttpSessionSecurityContextRepository();
        sessions.setAllowSessionCreation(false);
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        String[] writers = {Role.OWNER.name(), Role.ADMIN.name(), Role.ENGINEERING_LEADER.name()};
        String[] everyone = java.util.Arrays.stream(Role.values()).map(Enum::name).toArray(String[]::new);
        http.csrf(c -> c.csrfTokenRepository(csrfTokens).csrfTokenRequestHandler(csrfHandler)
                        // CSRF protects only what carries an ambient credential: a state-changing call that rides on the session cookie
                        // (decision D68). A bearer-token call has none, and a call with neither is simply unauthenticated
                        .requireCsrfProtectionMatcher(r -> !java.util.Set.of("GET", "HEAD", "OPTIONS", "TRACE").contains(r.getMethod())
                                && r.getHeader("Authorization") == null && hasCookie(r, sessionCookie)))
                .securityContext(c -> c.securityContextRepository(sessions))
                .requestCache(c -> c.requestCache(new org.springframework.security.web.savedrequest.NullRequestCache()))
                .addFilterBefore(new AbsoluteSessionTimeoutFilter(identity.sessionAbsolute(), clock), SecurityContextHolderFilter.class)
                .addFilterAfter(new CompanyContextFilter(memberships, errors, audit, clock), BearerTokenAuthenticationFilter.class)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.NEVER))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // platform administration (decision D46): only the platform role, and nothing tenant-scoped is reachable with it
                        .requestMatchers("/api/v1/platform/**").hasRole(PlatformRole.PLATFORM_ADMIN)
                        // questionnaire library and builder (decisions D47, D50): administrators only, for reads as well as writes
                        .requestMatchers("/api/v1/questionnaires", "/api/v1/questionnaires/**")
                        .hasAnyRole(Role.OWNER.name(), Role.ADMIN.name())
                        // scoring configuration of a tenant (decisions D52 to D58): analysts may read, owners and administrators change
                        .requestMatchers(HttpMethod.GET, "/api/v1/configuration", "/api/v1/configuration/**")
                        .hasAnyRole(Role.OWNER.name(), Role.ADMIN.name(), Role.ANALYST.name())
                        .requestMatchers("/api/v1/configuration", "/api/v1/configuration/**").hasAnyRole(Role.OWNER.name(), Role.ADMIN.name())
                        // roster and assessment runs (decision D48): administrators only
                        .requestMatchers("/api/v1/teams", "/api/v1/teams/**", "/api/v1/people", "/api/v1/people/**", "/api/v1/runs", "/api/v1/runs/**")
                        .hasAnyRole(Role.OWNER.name(), Role.ADMIN.name())
                        // the respondent flow: the respondent role reaches this and nothing else
                        .requestMatchers("/api/v1/respond/**").hasRole(RespondentRole.RESPONDENT)
                        .requestMatchers(HttpMethod.POST, "/api/v1/calculate", "/api/v1/assessments/*/score",
                                "/api/v1/assessments/*/score/recalculate").hasAnyRole(writers)
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**")
                        .hasAnyRole(Role.OWNER.name(), Role.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/v1/scoring-runs/*/answers")
                        .hasAnyRole(Role.OWNER.name(), Role.ADMIN.name(), Role.ANALYST.name())
                        // the companies a signed-in user may open (decision D69): the user alone, no company; the controller refuses a bearer token
                        .requestMatchers(HttpMethod.GET, "/api/v1/companies").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/**", "/v3/api-docs", "/v3/api-docs/**").hasAnyRole(everyone)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(rolesConverter()))
                        .authenticationEntryPoint(errors::unauthenticated)
                        .accessDeniedHandler(errors::accessDenied))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errors::unauthenticated)
                        .accessDeniedHandler((request, response, denied) -> {
                            if (denied instanceof CsrfException) {
                                errors.csrfRejected(request, response, denied);
                            } else {
                                errors.accessDenied(request, response, denied);
                            }
                        }));
        return http.build();
    }

    private static boolean hasCookie(jakarta.servlet.http.HttpServletRequest request, String name) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        return cookies != null && java.util.Arrays.stream(cookies).anyMatch(c -> name.equals(c.getName()));
    }

    /** Maps the {@code roles} claim to Spring authorities named ROLE_&lt;name&gt;. */
    @Bean
    public JwtAuthenticationConverter rolesConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            List<GrantedAuthority> authorities = new ArrayList<>();
            if (roles != null) {
                roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${lis.security.jwk-set-uri:}") String jwkSetUri, @Value("${lis.security.issuer:}") String issuer,
                          @Value("${lis.security.audience:}") String audience) {
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            LOG.warn("lis.security.jwk-set-uri is not set: every bearer token will be rejected");
            return token -> {
                throw new BadJwtException("no token verification key is configured");
            };
        }
        if (issuer == null || issuer.isBlank()) {
            LOG.warn("lis.security.issuer is not set: the issuer of a token is not checked, only its signature and validity period");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(validator(issuer, audience));
        return decoder;
    }

    /** Validity period always; issuer and audience when configured (a token for another service must not be accepted here). */
    static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        if (issuer != null && !issuer.isBlank()) {
            validators.add(new JwtIssuerValidator(issuer));
        }
        if (audience != null && !audience.isBlank()) {
            validators.add(jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "the token is not meant for this service", null)));
        }
        return new DelegatingOAuth2TokenValidator<>(validators);
    }
}
