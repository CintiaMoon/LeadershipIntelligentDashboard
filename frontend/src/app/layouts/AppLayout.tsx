import { useEffect } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '@/app/providers/auth-context';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { isPlatformAdmin } from '@/services/auth/principal';
import { useSession } from '@/stores/session';
import { humanizeCode } from '@/utils/presentation';

const navClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-md px-3 py-2 text-sm font-medium transition-colors ${isActive ? 'bg-accent-soft text-accent-ink' : 'text-ink-soft hover:bg-canvas hover:text-ink'}`;

export function AppLayout() {
  const { principal, signOut } = useAuth();
  const organizationId = useSession((s) => s.organizationId);
  const setOrganization = useSession((s) => s.setOrganization);
  const organizations = principal?.organizations ?? [];
  const platform = isPlatformAdmin(principal);

  // Keep the selection valid for this user: default to the first organization, drop one the token does not list.
  useEffect(() => {
    if (!principal) return;
    if (!organizationId || !principal.organizations.includes(organizationId)) {
      setOrganization(principal.organizations[0] ?? null);
    }
  }, [principal, organizationId, setOrganization]);

  return (
    <div className="min-h-screen">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:rounded focus:bg-surface focus:px-3 focus:py-2">
        Skip to content
      </a>
      <header className="border-b border-line bg-surface">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3 sm:px-6">
          <div className="flex items-center gap-2.5">
            <span aria-hidden="true" className="grid size-8 place-items-center rounded-lg bg-accent text-sm font-bold text-white">
              LI
            </span>
            <span className="text-base font-semibold tracking-tight">Leadership Intelligence</span>
          </div>
          <nav aria-label="Main" className="flex items-center gap-1">
            {platform ? (
              <NavLink to="/platform/tenants" className={navClass}>
                Tenants
              </NavLink>
            ) : (
              <>
                <NavLink to="/dashboard" className={navClass}>
                  Dashboard
                </NavLink>
                <NavLink to="/compare" className={navClass}>
                  Compare
                </NavLink>
              </>
            )}
          </nav>
          <div className="ml-auto flex flex-wrap items-center gap-3">
            {!platform && (
            <label className="flex items-center gap-2 text-sm text-ink-soft">
              <span>Organization</span>
              <select
                className="min-h-9 rounded-md border border-line bg-surface px-2 text-sm text-ink"
                value={organizationId ?? ''}
                onChange={(e) => setOrganization(e.target.value || null)}
                disabled={organizations.length === 0}
              >
                {organizations.length === 0 && <option value="">None available</option>}
                {organizations.map((id) => (
                  <option key={id} value={id}>
                    {id}
                  </option>
                ))}
              </select>
            </label>
            )}
            <div className="flex items-center gap-1.5" aria-label="Your roles">
              {platform && <Badge tone="accent">Platform administrator</Badge>}
              {principal?.roles.map((role) => (
                <Badge key={role} tone="accent">
                  {humanizeCode(role)}
                </Badge>
              ))}
            </div>
            <Button variant="ghost" onClick={() => void signOut()}>
              Sign out
            </Button>
          </div>
        </div>
      </header>
      <main id="main" className="mx-auto max-w-7xl px-4 py-8 sm:px-6">
        <Outlet />
      </main>
    </div>
  );
}
