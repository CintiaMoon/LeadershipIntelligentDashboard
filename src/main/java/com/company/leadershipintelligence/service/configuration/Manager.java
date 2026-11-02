package com.company.leadershipintelligence.service.configuration;

import com.company.leadershipintelligence.service.command.Caller;
import com.company.leadershipintelligence.service.command.OrganizationScope;

/**
 * Who manages configuration and what they may reach (decision D52): the platform administrator manages the platform default and
 * has no organizations; an owner or administrator manages the tenant override and the organization overrides of the organizations
 * in their token. Built by the API layer from the verified token, never from a request.
 */
public record Manager(Caller caller, OrganizationScope scope) {

    public static Manager platform(String actor) {
        return new Manager(Caller.platform(actor), OrganizationScope.unrestricted());
    }

    public boolean platform() {
        return caller.isPlatform();
    }

    public String actor() {
        return caller.actor();
    }
}
