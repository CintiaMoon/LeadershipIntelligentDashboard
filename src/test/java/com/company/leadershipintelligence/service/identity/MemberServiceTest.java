package com.company.leadershipintelligence.service.identity;

import com.company.leadershipintelligence.config.properties.IdentityProperties;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.identity.MembershipStatus;
import com.company.leadershipintelligence.repository.persistence.entity.MemberRow;
import com.company.leadershipintelligence.repository.persistence.repository.MembershipStore;
import com.company.leadershipintelligence.repository.persistence.repository.UserStore;
import com.company.leadershipintelligence.support.TestClock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rules of {@link MemberService} that depend on the stored state. Through the API the last-owner rule cannot be reached (whoever may
 * touch an owner is itself an active owner), so it is checked here against the service directly: it is the guard that keeps a company
 * from losing its last owner whatever the caller does.
 */
class MemberServiceTest {

    private static final String TENANT = "acme";

    private final MembershipStore store = mock(MembershipStore.class);
    private final MembershipService access = mock(MembershipService.class);
    private final MemberService service = new MemberService(store, mock(UserStore.class), access, mock(TokenCodec.class), mock(AccountMessages.class),
            mock(AuthRateLimiter.class), mock(SecurityAudit.class),
            new IdentityProperties(4, Duration.ofHours(24), Duration.ofHours(1), Duration.ofHours(8), "http://localhost", "no-reply@localhost", false, "",
                    Duration.ofDays(7), ""), new TestClock());

    private final UUID actorId = UUID.randomUUID();
    private final UUID targetId = UUID.randomUUID();

    private void actorHolds(String... roles) {
        when(access.access(actorId, TENANT)).thenReturn(Optional.of(new CompanyMembership(TENANT, "Acme", Set.of(roles), Set.of())));
    }

    private void targetIs(MembershipStatus status, String... roles) {
        when(store.member(TENANT, targetId)).thenReturn(Optional.of(new MemberRow(targetId, TENANT, UUID.randomUUID(), "t@example.com", "T", status,
                Set.of(roles), Set.of(), null, Instant.EPOCH, Instant.EPOCH)));
    }

    private static void conflict(ThrowingCallable call) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ScoringException.class, e -> {
            assertThat(e.code()).isEqualTo(ScoringErrorCode.MEMBER_CONFLICT);
            assertThat(e.getMessage()).contains("at least one active owner");
        });
    }

    private interface ThrowingCallable {
        void run();
    }

    @Test
    void theLastActiveOwnerCannotBeRemovedSuspendedOrDemoted() {
        actorHolds("OWNER");
        targetIs(MembershipStatus.ACTIVE, "OWNER");
        when(store.activeOwners(TENANT)).thenReturn(1L);
        conflict(() -> service.remove(actorId, TENANT, targetId, null));
        conflict(() -> service.update(actorId, TENANT, targetId, new MemberChange(null, null, "SUSPENDED"), null));
        conflict(() -> service.update(actorId, TENANT, targetId, new MemberChange(List.of("ADMIN"), null, null), null));
        verify(store, never()).changeStatus(any(), any(), any(), any());
        verify(store, never()).replaceRoles(any(), any(), any(), any());
    }

    @Test
    void anOwnerCanBeRemovedSuspendedOrDemotedWhileAnotherRemains() {
        actorHolds("OWNER");
        targetIs(MembershipStatus.ACTIVE, "OWNER");
        when(store.activeOwners(TENANT)).thenReturn(2L);
        assertThatCode(() -> service.remove(actorId, TENANT, targetId, null)).doesNotThrowAnyException();
        verify(store).changeStatus(eq(TENANT), eq(targetId), eq(MembershipStatus.REMOVED), any());
    }

    @Test
    void changingAnOwnersOrganizationsOrKeepingTheOwnerRoleIsNotAffectedByTheRule() {
        actorHolds("OWNER");
        targetIs(MembershipStatus.ACTIVE, "OWNER");
        when(store.activeOwners(TENANT)).thenReturn(1L);
        assertThatCode(() -> service.update(actorId, TENANT, targetId, new MemberChange(List.of("OWNER", "ADMIN"), null, null), null)).doesNotThrowAnyException();
    }

    @Test
    void anOwnerWhoIsNotActiveIsNotTheLastOneToProtect() {
        actorHolds("OWNER");
        targetIs(MembershipStatus.SUSPENDED, "OWNER");
        when(store.activeOwners(TENANT)).thenReturn(1L);
        assertThatCode(() -> service.remove(actorId, TENANT, targetId, null)).doesNotThrowAnyException();
    }
}
