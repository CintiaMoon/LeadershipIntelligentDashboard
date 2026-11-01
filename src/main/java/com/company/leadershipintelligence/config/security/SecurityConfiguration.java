package com.company.leadershipintelligence.config.security;

import com.company.leadershipintelligence.api.exception.ApiErrorWriter;
import com.company.leadershipintelligence.api.security.PlatformRole;
import com.company.leadershipintelligence.api.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, ApiErrorWriter errors) throws Exception {
        String[] writers = {Role.OWNER.name(), Role.ADMIN.name(), Role.ENGINEERING_LEADER.name()};
        String[] everyone = java.util.Arrays.stream(Role.values()).map(Enum::name).toArray(String[]::new);
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // platform administration (decision D46): only the platform role, and nothing tenant-scoped is reachable with it
                        .requestMatchers("/api/v1/platform/**").hasRole(PlatformRole.PLATFORM_ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/v1/calculate", "/api/v1/assessments/*/score",
                                "/api/v1/assessments/*/score/recalculate").hasAnyRole(writers)
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**")
                        .hasAnyRole(Role.OWNER.name(), Role.ADMIN.name())
                        .requestMatchers(HttpMethod.GET, "/api/v1/scoring-runs/*/answers")
                        .hasAnyRole(Role.OWNER.name(), Role.ADMIN.name(), Role.ANALYST.name())
                        .requestMatchers(HttpMethod.GET, "/api/v1/**", "/v3/api-docs", "/v3/api-docs/**").hasAnyRole(everyone)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(rolesConverter()))
                        .authenticationEntryPoint(errors::unauthenticated)
                        .accessDeniedHandler(errors::accessDenied))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errors::unauthenticated)
                        .accessDeniedHandler(errors::accessDenied));
        return http.build();
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
