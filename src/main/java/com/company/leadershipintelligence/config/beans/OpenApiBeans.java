package com.company.leadershipintelligence.config.beans;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiBeans {

    @Bean
    OpenAPI leadershipIntelligenceApi() {
        return new OpenAPI()
                .info(new Info().title("Leadership Intelligence Scoring Engine").version("3.0.0")
                        .description("Deterministic, explainable organizational scoring. The score is an organizational diagnostic "
                                + "signal, not an individual performance rating and not a basis for employment decisions."))
                .components(new Components()
                        // a browser: the session cookie (set by POST /api/v1/auth/login) plus X-XSRF-TOKEN on state-changing calls and X-Company-Id
                        .addSecuritySchemes("session",
                                new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("LIS_SESSION"))
                        // any other caller: an API token, Authorization: Bearer lis_... (it carries its own company and needs no CSRF token)
                        .addSecuritySchemes("bearer",
                                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("API token (lis_...)")))
                .addSecurityItem(new SecurityRequirement().addList("session"))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
