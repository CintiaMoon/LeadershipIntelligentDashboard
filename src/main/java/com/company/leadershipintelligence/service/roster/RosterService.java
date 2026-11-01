package com.company.leadershipintelligence.service.roster;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.repository.persistence.entity.RosterRows.PersonRow;
import com.company.leadershipintelligence.repository.persistence.entity.RosterRows.TeamRow;
import com.company.leadershipintelligence.repository.persistence.repository.AccessAuditStore;
import com.company.leadershipintelligence.repository.persistence.repository.RosterStore;
import com.company.leadershipintelligence.service.command.OrganizationScope;
import com.company.leadershipintelligence.service.query.RunViews.PersonView;
import com.company.leadershipintelligence.service.query.RunViews.TeamView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Teams and the roster of people (decision D48). A team belongs to an organization the caller may reach; a person belongs to one
 * team and is identified for the respondent flow by e-mail. Everything is tenant-scoped, and a team or person outside the caller's
 * tenant or organizations looks like a missing one. Reading the roster is audited (names and e-mail addresses are personal data);
 * neither names nor addresses are written to the logs or the audit.
 */
@Service
public class RosterService {

    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final RosterStore roster;
    private final AccessAuditStore audit;
    private final Clock clock;

    public RosterService(RosterStore roster, AccessAuditStore audit, Clock clock) {
        this.roster = roster;
        this.audit = audit;
        this.clock = clock;
    }

    // ---------------- teams ----------------

    @Transactional(readOnly = true)
    public List<TeamView> teams(TenantId tenant, OrganizationScope scope, String organizationId) {
        if (organizationId != null) {
            scope.require(organizationId);
        }
        return roster.teams(tenant.value(), organizationId == null ? scope.organizationIds() : List.of(organizationId)).stream()
                .map(RosterService::view).toList();
    }

    @Transactional
    public TeamView createTeam(TenantId tenant, OrganizationScope scope, String organizationId, String name) {
        scope.require(organizationId);
        String clean = name(name, "a team");
        if (!roster.ensureOrganization(tenant.value(), organizationId)) {
            throw new ScoringException(ScoringErrorCode.ORGANIZATION_ACCESS_DENIED, "the caller cannot access this organization");
        }
        String id = "t-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try {
            roster.insertTeam(tenant.value(), id, organizationId, clean, clock.instant());
        } catch (DuplicateKeyException e) {
            throw conflict("the organization already has a team with this name");
        }
        return view(roster.team(tenant.value(), id).orElseThrow());
    }

    @Transactional
    public TeamView renameTeam(TenantId tenant, OrganizationScope scope, String teamId, String name) {
        TeamRow team = team(tenant, scope, teamId);
        try {
            roster.renameTeam(tenant.value(), team.id(), name(name, "a team"));
        } catch (DuplicateKeyException e) {
            throw conflict("the organization already has a team with this name");
        }
        return view(roster.team(tenant.value(), team.id()).orElseThrow());
    }

    // ---------------- people ----------------

    @Transactional
    public List<PersonView> people(TenantId tenant, OrganizationScope scope, String actor, String teamId) {
        if (teamId != null) {
            team(tenant, scope, teamId);
        }
        List<PersonView> people = roster.people(tenant.value(), scope.organizationIds(), teamId).stream().map(RosterService::view).toList();
        audit.record(tenant.value(), actor, "ROSTER_READ", null, "{\"count\":" + people.size() + "}", clock.instant());
        return people;
    }

    @Transactional
    public PersonView createPerson(TenantId tenant, OrganizationScope scope, String actor, String teamId, String name, String email) {
        TeamRow team = team(tenant, scope, teamId);
        UUID id = UUID.randomUUID();
        try {
            roster.insertPerson(tenant.value(), id, team.id(), name(name, "a person"), email(email), true, clock.instant());
        } catch (DuplicateKeyException e) {
            throw conflict("a person with this e-mail address is already on the roster");
        }
        audit.record(tenant.value(), actor, "PERSON_ADDED", null, "{}", clock.instant());
        return view(roster.person(tenant.value(), id).orElseThrow());
    }

    /** Replaces the person's team, name, e-mail and active flag. People are deactivated, never deleted. */
    @Transactional
    public PersonView updatePerson(TenantId tenant, OrganizationScope scope, String actor, UUID personId, String teamId, String name, String email,
                                   boolean active) {
        PersonRow existing = roster.person(tenant.value(), personId).orElseThrow(RosterService::personNotFound);
        scope.require(existing.organizationId());
        TeamRow team = team(tenant, scope, teamId);
        try {
            roster.updatePerson(tenant.value(), personId, team.id(), name(name, "a person"), email(email), active, clock.instant());
        } catch (DuplicateKeyException e) {
            throw conflict("a person with this e-mail address is already on the roster");
        }
        audit.record(tenant.value(), actor, "PERSON_CHANGED", null, "{}", clock.instant());
        return view(roster.person(tenant.value(), personId).orElseThrow());
    }

    // ---------------- internals ----------------

    private TeamRow team(TenantId tenant, OrganizationScope scope, String teamId) {
        TeamRow team = roster.team(tenant.value(), teamId == null ? "" : teamId)
                .orElseThrow(() -> new ScoringException(ScoringErrorCode.ROSTER_NOT_FOUND, "team not found", Map.of()));
        scope.require(team.organizationId());
        return team;
    }

    private static ScoringException personNotFound() {
        return new ScoringException(ScoringErrorCode.ROSTER_NOT_FOUND, "person not found", Map.of());
    }

    private static ScoringException conflict(String message) {
        return new ScoringException(ScoringErrorCode.ROSTER_CONFLICT, message, Map.of());
    }

    private static String name(String name, String what) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty() || clean.length() > 200) {
            throw new ScoringException(ScoringErrorCode.ROSTER_INVALID, what + " needs a name of 1 to 200 characters", Map.of());
        }
        return clean;
    }

    private static String email(String email) {
        String clean = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (clean.length() > 320 || !EMAIL.matcher(clean).matches()) {
            throw new ScoringException(ScoringErrorCode.ROSTER_INVALID, "a person needs a valid e-mail address", Map.of());
        }
        return clean;
    }

    private static TeamView view(TeamRow t) {
        return new TeamView(t.id(), t.organizationId(), t.name(), t.activePeople(), t.createdAt());
    }

    private static PersonView view(PersonRow p) {
        return new PersonView(p.id(), p.teamId(), p.organizationId(), p.name(), p.email(), p.active());
    }
}
