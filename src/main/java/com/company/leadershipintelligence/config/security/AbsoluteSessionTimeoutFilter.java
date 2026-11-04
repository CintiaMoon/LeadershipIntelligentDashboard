package com.company.leadershipintelligence.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

/**
 * Ends a session that has lived longer than the absolute limit (decision D67), however active it was. The idle limit is the
 * session store's own timeout; this one stops a stolen cookie from being kept alive for ever by regular use.
 */
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    private final Duration limit;
    private final Clock clock;

    public AbsoluteSessionTimeoutFilter(Duration limit, Clock clock) {
        this.limit = limit;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && clock.millis() - session.getCreationTime() > limit.toMillis()) {
            session.invalidate();
        }
        chain.doFilter(request, response);
    }
}
