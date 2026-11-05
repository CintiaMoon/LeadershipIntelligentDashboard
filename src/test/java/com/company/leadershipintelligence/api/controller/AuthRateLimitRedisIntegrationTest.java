package com.company.leadershipintelligence.api.controller;

import com.company.leadershipintelligence.repository.security.RateCounters;
import com.company.leadershipintelligence.repository.security.ResilientRateCounters;
import com.company.leadershipintelligence.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** The application wired to a real Redis (lis.rate-limit.store=redis): the counters are shared and a throttle answers 429. */
@org.springframework.test.context.TestPropertySource(properties = {"lis.rate-limit.token-ip-limit=2"})
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
class AuthRateLimitRedisIntegrationTest extends PostgresIntegrationTest {

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    static {
        REDIS.start();
    }

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("lis.rate-limit.store", () -> "redis");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    RateCounters counters;

    @Autowired
    MockMvc mvc;

    @Test
    void theApplicationUsesRedisCountersAndThrottlesWithThem() throws Exception {
        assertThat(counters).isInstanceOf(ResilientRateCounters.class);
        String body = "{\"token\":\"not-a-real-token\",\"password\":\"correct horse battery\"}";
        int[] statuses = new int[3];
        for (int i = 0; i < 3; i++) {
            statuses[i] = mvc.perform(post("/api/v1/auth/verify-email").with(csrf()).with(r -> {
                r.setRemoteAddr("10.9.9.9");
                return r;
            }).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
        }
        assertThat(statuses).containsExactly(400, 400, 429);
    }
}
