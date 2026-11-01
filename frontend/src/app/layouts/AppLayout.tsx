import { useEffect } from 'react';
import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '@/app/providers/auth-context';
import { brand } from '@/brand';
import { Badge } from '@/components/ui/Badge';
import { BrandMark } from '@/components/ui/BrandMark';
import { Button } from '@/components/ui/Button';
import { isPlatformAdmin, isRespondent } from '@/services/auth/principal';
import { useSession } from '@/stores/session';
import { useTheme } from '@/stores/theme';
import { humanizeCode } from '@/utils/presentation';
import { navItems, type NavIcon } from './nav';

const navClass = ({ isActive }: { isActive: boolean }) =>
  `flex items-center gap-2.5 whitespace-nowrap rounded-md px-3 py-2 text-sm font-medium transition-colors ${
    isActive ? 'bg-accent-soft text-accent-ink' : 'text-ink-soft hover:bg-canvas hover:text-ink'
  }`;

const iconPaths: Record<NavIcon, string> = {
  dashboard: 'M4 4h6v7H4V4Zm10 0h6v4h-6V4ZM4 15h6v5H4v-5Zm10-3h6v8h-6v-8Z',
  compare: 'M7 4v16M17 4v16M3 8l4-4 4 4M13 16l4 4 4-4',
  tenants: 'M4 21V6l8-3 8 3v15M9 21v-5h6v5M8 9h2m4 0h2M8 12.5h2m4 0h2',
  questionnaires: 'M7 3h8l4 4v14H7V3Zm7 0v5h5M10 12h6M10 16h6',
  runs: 'M8 5v14l11-7L8 5Z',
  people: 'M9 11a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7Zm-6 9v-1a5 5 0 0 1 5-5h2a5 5 0 0 1 5 5v1M17 4.5a3 3 0 0 1 0 6m2.5 9.5v-1a4.5 4.5 0 0 0-3-4.2',
  answer: 'M5 4h14v16H5V4Zm3 5h8M8 13h8M8 17h5',
};

function NavGlyph({ icon }: { icon: NavIcon }) {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="size-4 shrink-0" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
      <path d={iconPaths[icon]} />
    </svg>
  );
}

function ThemeToggle() {
  const theme = useTheme((s) => s.theme);
  const toggle = useTheme((s) => s.toggle);
  const next = theme === 'dark' ? 'light' : 'dark';
  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={`Switch to ${next} theme`}
      title={`Switch to ${next} theme`}
      className="grid size-9 place-items-center rounded-md text-ink-soft transition-colors hover:bg-accent-soft hover:text-accent-ink"
    >
      <svg aria-hidden="true" viewBox="0 0 24 24" className="size-[18px]" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        {theme === 'dark' ? (
          <path d="M12 4V2m0 20v-2m8-8h2M2 12h2m13.7-5.7 1.4-1.4M4.9 19.1l1.4-1.4m0-11.4L4.9 4.9m14.2 14.2-1.4-1.4M12 7.5a4.5 4.5 0 1 0 0 9 4.5 4.5 0 0 0 0-9Z" />
        ) : (
          <path d="M20.5 14.5A8.5 8.5 0 0 1 9.5 3.5a8.5 8.5 0 1 0 11 11Z" />
        )}
      </svg>
    </button>
  );
}

export function AppLayout() {
  const { principal, signOut } = useAuth();
  const organizationId = useSession((s) => s.organizationId);
  const setOrganization = useSession((s) => s.setOrganization);
  const organizations = principal?.organizations ?? [];
  const platform = isPlatformAdmin(principal);
  const respondent = isRespondent(principal);

  // Keep the selection valid for this user: default to the first organization, drop one the token does not list.
  useEffect(() => {
    if (!principal) return;
    if (!organizationId || !principal.organizations.includes(organizationId)) {
      setOrganization(principal.organizations[0] ?? null);
    }
  }, [principal, organizationId, setOrganization]);

  return (
    <div className="min-h-screen lg:flex">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-surface focus:px-3 focus:py-2">
        Skip to content
      </a>
      <aside className="shrink-0 border-b border-line bg-sidebar lg:sticky lg:top-0 lg:h-screen lg:w-60 lg:border-b-0 lg:border-r">
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3 lg:flex-col lg:flex-nowrap lg:items-stretch lg:gap-6 lg:px-3 lg:py-4">
          <div className="flex items-center gap-2.5 lg:px-2">
            <BrandMark className="size-9" />
            <div className="leading-tight">
              <p className="font-display text-lg font-semibold tracking-tight">{brand.name}</p>
              <p className="text-[10px] font-medium uppercase tracking-[0.14em] text-ink-faint">{brand.tagline}</p>
            </div>
          </div>
          <nav aria-label="Main" className="flex w-full min-w-0 items-center gap-1 overflow-x-auto lg:flex-col lg:items-stretch lg:overflow-visible">
            {navItems(principal).map((item) => (
              <NavLink key={item.to} to={item.to} className={navClass}>
                <NavGlyph icon={item.icon} />
                {item.label}
              </NavLink>
            ))}
          </nav>
        </div>
      </aside>
      <div className="min-w-0 flex-1">
        <header className="border-b border-line bg-sidebar">
          <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3 sm:px-6">
            {!platform && !respondent && (
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
            <div className="ml-auto flex flex-wrap items-center gap-3">
              <div className="flex flex-wrap items-center gap-1.5" aria-label="Your roles">
                {platform && <Badge tone="accent">Platform administrator</Badge>}
                {respondent && <Badge tone="accent">Respondent</Badge>}
                {principal?.roles.map((role) => (
                  <Badge key={role} tone="accent">
                    {humanizeCode(role)}
                  </Badge>
                ))}
              </div>
              <ThemeToggle />
              <Button variant="ghost" onClick={() => void signOut()}>
                Sign out
              </Button>
            </div>
          </div>
        </header>
        <main id="main" className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
