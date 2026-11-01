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
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
