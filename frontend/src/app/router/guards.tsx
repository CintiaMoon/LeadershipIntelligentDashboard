import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/app/providers/auth-context';
import { LoadingState } from '@/components/ui/StateViews';
import { EmptyState } from '@/components/ui/StateViews';
import { canManageAssessments, isPlatformAdmin, isRespondent, type Principal } from '@/services/auth/principal';

/** Anonymous visitors are sent to the login page and brought back afterwards. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'loading') {
    return (
      <div className="mx-auto max-w-3xl p-8">
        <LoadingState label="Checking your session" />
      </div>
    );
  }
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  return <>{children}</>;
}

/** Hides what a role may not use. The backend still enforces every permission. */
export function Permitted({ check, children, fallback = null }: { check: (p: Principal | null) => boolean; children: ReactNode; fallback?: ReactNode }) {
  const { principal } = useAuth();
  return <>{check(principal) ? children : fallback}</>;
}

/**
 * Platform administration is for the platform administrator only. Anyone else sees that it is not for them, and the page
 * never asks the backend for the data. The backend refuses them as well; this only avoids a confusing screen.
 */
export function RequirePlatformAdmin({ children }: { children: ReactNode }) {
  const { principal } = useAuth();
  if (!isPlatformAdmin(principal)) {
    return <EmptyState title="Only for platform administrators" description="Creating and listing tenants is limited to the administrator of the platform. Ask them if you need a tenant." />;
  }
  return <>{children}</>;
}

/** Questionnaires, the roster and assessment runs are for owners and administrators; the backend refuses everyone else as well. */
export function RequireManager({ children }: { children: ReactNode }) {
  const { principal } = useAuth();
  if (!canManageAssessments(principal)) {
    return <EmptyState title="Only for owners and administrators" description="Building questionnaires, managing people and running assessments is limited to owners and administrators. Ask one of them if you need this." />;
  }
  return <>{children}</>;
}

/** Answering questionnaires is for respondents, who reach nothing else. */
export function RequireRespondent({ children }: { children: ReactNode }) {
  const { principal } = useAuth();
  if (!isRespondent(principal)) {
    return <EmptyState title="Only for respondents" description="This is where people answer the questionnaires they have been asked to complete." />;
  }
  return <>{children}</>;
}

/** Where a signed-in user lands: a platform administrator has no organizations and a respondent no dashboard. */
export function HomeRedirect() {
  const { principal } = useAuth();
  if (isPlatformAdmin(principal)) return <Navigate to="/platform/tenants" replace />;
  return <Navigate to={isRespondent(principal) ? '/respond' : '/dashboard'} replace />;
}
