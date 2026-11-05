import { useEffect, type ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/app/providers/auth-context';
import { LoadingState } from '@/components/ui/StateViews';
import { EmptyState } from '@/components/ui/StateViews';
import { authConfig } from '@/services/auth/config';
import { canManageAssessments, canReadConfiguration, isPlatformAdmin, isRespondent, type Principal } from '@/services/auth/principal';
import { companyFromPath } from './company-path';

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
    // the screens of a company have their own router, whose addresses start with /c/<company>: leaving them is a full page load
    if (authConfig.mode === 'session' && companyFromPath(window.location.pathname)) return <LeaveToLogin />;
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

/** Reading and managing the scoring configuration: the platform administrator, owners, administrators and analysts (decision D52). */
export function RequireConfigurationReader({ children }: { children: ReactNode }) {
  const { principal } = useAuth();
  if (!canReadConfiguration(principal)) {
    return <EmptyState title="Not available for your role" description="The scoring configuration is visible to the platform administrator, owners, administrators and analysts. Ask one of them if you need it." />;
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

/** Ends up at the sign-in page after a full page load, remembering where the person was. */
function LeaveToLogin() {
  useEffect(() => {
    const from = window.location.pathname + window.location.search;
    window.location.replace(`/login?from=${encodeURIComponent(from)}`);
  }, []);
  return (
    <div className="mx-auto max-w-3xl p-8">
      <LoadingState label="Your session has ended" />
    </div>
  );
}

/**
 * In the application's own accounts a screen belongs to a company (decision D69). Outside one, a person is sent to choose; in a
 * company they may not open, they are told so. The platform administrator belongs to no company. The server checks the membership
 * on every call; this only avoids screens that cannot work.
 */
export function RequireCompany({ children }: { children: ReactNode }) {
  const { mode, account, company } = useAuth();
  if (mode !== 'session') return <>{children}</>;
  // a platform administrator belongs to no company: outside one they carry on, inside one they are told so
  if (account?.platformAdmin ? !companyFromPath(window.location.pathname) : company) return <>{children}</>;
  if (companyFromPath(window.location.pathname)) {
    return (
      <div className="mx-auto max-w-3xl p-8">
        <EmptyState
          title="You cannot open this company"
          description="Either it does not exist, or you are not an active member of it. Choose one of your companies."
          action={<a href="/companies" className="inline-flex min-h-9 items-center rounded-md bg-ink px-3.5 text-sm font-medium text-canvas hover:bg-ink/90">My companies</a>}
        />
      </div>
    );
  }
  return <Navigate to="/companies" replace />;
}
