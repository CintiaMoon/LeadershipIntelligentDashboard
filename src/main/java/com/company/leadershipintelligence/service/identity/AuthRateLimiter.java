package com.company.leadershipintelligence.service.identity;

import com.company.leadershipintelligence.config.properties.IdentityProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Throttles the sign-in and account endpoints (finding of the P1 review). In memory and per instance: it limits abuse on one
 * node and is replaced by the shared Redis counters of phase P2 (decision D71). Three rules, none of which depends on whether
 * an account exists, so a throttle never reveals one:
 * <ul>
 *   <li>a client address may make {@code ipRequestsPerMinute} calls a minute (the caller answers 429 above it);</li>
 *   <li>after {@code loginFailureLimit} failed sign-ins for one email from one address, sign-in is refused for
 *       {@code loginLockout} (keyed by both, so a stranger cannot lock the owner out from another address);</li>
 *   <li>an address receives at most {@code emailsPerAddressPerHour} account emails an hour; further requests are answered as
 *       usual and send nothing.</li>
 * </ul>
 */
@Component
public class AuthRateLimiter {

    private static final int PRUNE_ABOVE = 10_000;
    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration HOUR = Duration.ofHours(1);

    private record Window(Instant start, int count) {
    }

    private final IdentityProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> counters = new ConcurrentHashMap<>();

    public AuthRateLimiter(IdentityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Counts a call from this address; false when it is over the limit. */
    public boolean allowRequest(String ip) {
        return count("ip|" + ip, MINUTE) <= properties.ipRequestsPerMinute();
    }

    /** Counts an account email to this address; false when the hourly allowance is used up. */
    public boolean allowMail(String email) {
        return count("mail|" + email, HOUR) <= properties.emailsPerAddressPerHour();
    }

    /** True while sign-in is open for this email and address (does not count). */
    public boolean loginOpen(String email, String ip) {
        Window w = counters.get(loginKey(email, ip));
        return w == null || !clock.instant().isBefore(w.start().plus(properties.loginLockout())) || w.count() < properties.loginFailureLimit();
    }

    public void loginFailed(String email, String ip) {
        count(loginKey(email, ip), properties.loginLockout());
    }

    public void loginSucceeded(String email, String ip) {
        counters.remove(loginKey(email, ip));
    }

    private static String loginKey(String email, String ip) {
        return "login|" + email + "|" + ip;
    }

    private int count(String key, Duration length) {
        Instant now = clock.instant();
        if (counters.size() > PRUNE_ABOVE) {
            Duration longest = HOUR.compareTo(properties.loginLockout()) > 0 ? HOUR : properties.loginLockout();
            counters.values().removeIf(w -> now.isAfter(w.start().plus(longest)));
        }
        return counters.merge(key, new Window(now, 1),
                (old, fresh) -> now.isBefore(old.start().plus(length)) ? new Window(old.start(), old.count() + 1) : fresh).count();
    }
}
