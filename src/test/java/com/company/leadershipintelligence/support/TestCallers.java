package com.company.leadershipintelligence.support;

import com.company.leadershipintelligence.api.security.AuthenticatedCaller;
import com.company.leadershipintelligence.api.security.CallerAuthentication;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.util.Collection;
import java.util.Set;

/**
 * A caller for a test request: someone the application has already authenticated, with the company, organizations and roles the test
 * is about. It puts the same {@link AuthenticatedCaller} into the security context that the session and API token filters build, so
 * everything behind the filters (the role rules, the tenant scope, the audit) is exercised as in production. The filters themselves
 * are exercised by the tests that sign in for real ({@code AccountFlowTest} and its subclasses).
 */
public final class TestCallers {

    private TestCallers() {
    }

    /** A person with a membership: their account id (any text), company, organizations, optional verified email and roles. */
    public static RequestPostProcessor caller(String subject, String tenant, Collection<String> organizations, String email, Collection<String> roles) {
        return SecurityMockMvcRequestPostProcessors.authentication(
                new CallerAuthentication(new AuthenticatedCaller(subject, email, tenant, Set.copyOf(roles), Set.copyOf(organizations), false)));
    }

    /** A caller not limited to a list of organizations: it may also name an organization that does not exist yet. */
    public static RequestPostProcessor callerInAllOrganizations(String subject, String tenant, Collection<String> existingOrganizations, Collection<String> roles) {
        return SecurityMockMvcRequestPostProcessors.authentication(
                new CallerAuthentication(new AuthenticatedCaller(subject, null, tenant, Set.copyOf(roles), Set.copyOf(existingOrganizations), true)));
    }

    /** The platform administrator: the role and a subject, and no company. */
    public static RequestPostProcessor platformAdmin(String subject) {
        return caller(subject, null, Set.of(), null, Set.of("PLATFORM_ADMIN"));
    }
}
