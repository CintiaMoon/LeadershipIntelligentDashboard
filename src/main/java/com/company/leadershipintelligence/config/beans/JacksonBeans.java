package com.company.leadershipintelligence.config.beans;

import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JacksonBeans {

    /** Keeps the scale of decimals (67.00 stays 67.00, 100.00 never becomes 1E+2) when stored JSON is read and written back. */
    @Bean
    Jackson2ObjectMapperBuilderCustomizer keepDecimalScale() {
        return builder -> builder.postConfigurer(mapper -> mapper.configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false));
    }
}
