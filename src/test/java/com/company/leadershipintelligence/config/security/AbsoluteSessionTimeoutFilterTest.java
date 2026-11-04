package com.company.leadershipintelligence.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AbsoluteSessionTimeoutFilterTest {

    private static final Instant START = Instant.parse("2026-11-01T10:00:00Z");

    private boolean stillValidAfter(Duration age) throws Exception {
        MockHttpSession session = new MockHttpSession() {
            @Override
            public long getCreationTime() {
                return START.toEpochMilli();
            }
        };
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        new AbsoluteSessionTimeoutFilter(Duration.ofHours(8), Clock.fixed(START.plus(age), ZoneOffset.UTC))
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return !session.isInvalid();
    }

    @Test
    void aSessionWithinTheLimitIsKept() throws Exception {
        assertThat(stillValidAfter(Duration.ofHours(7).plusMinutes(59))).isTrue();
        assertThat(stillValidAfter(Duration.ofHours(8))).isTrue();
    }

    @Test
    void aSessionPastTheLimitIsEndedHoweverActive() throws Exception {
        assertThat(stillValidAfter(Duration.ofHours(8).plusSeconds(1))).isFalse();
    }

    @Test
    void aRequestWithoutASessionIsLeftAlone() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        new AbsoluteSessionTimeoutFilter(Duration.ofHours(8), Clock.fixed(START, ZoneOffset.UTC))
                .doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(request.getSession(false)).isNull();
    }
}
