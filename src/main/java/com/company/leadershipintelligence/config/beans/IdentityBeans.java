package com.company.leadershipintelligence.config.beans;

import com.company.leadershipintelligence.config.properties.IdentityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.concurrent.Executor;

/** Beans of the built-in accounts (decisions D65, D66). */
@Configuration
@EnableConfigurationProperties(IdentityProperties.class)
public class IdentityBeans {

    /** bcrypt behind a prefix ({bcrypt}), so a stronger algorithm can be added later and old hashes keep working. */
    @Bean
    PasswordEncoder passwordEncoder(IdentityProperties properties) {
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(properties.passwordStrength())));
    }

    /** Runs email sending off the request thread; with lis.identity.mail-async=false (tests) it runs on the caller's thread. */
    @Bean(name = "mailExecutor")
    Executor mailExecutor(IdentityProperties properties) {
        if (!properties.mailAsync()) {
            return Runnable::run;
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("mail-");
        executor.setDaemon(true);
        executor.initialize();
        return executor;
    }
}
