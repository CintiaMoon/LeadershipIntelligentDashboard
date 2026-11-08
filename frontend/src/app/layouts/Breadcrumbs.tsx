import { Link, useLocation } from 'react-router-dom';
import { trailFor } from './trail';

/** "Company / Section / Page": where the screen sits, with a way back up. The last crumb is the page itself. */
export function Breadcrumbs({ rootLabel, rootTo }: { rootLabel: string; rootTo: string }) {
  const { pathname } = useLocation();
  const trail = trailFor(pathname);
  if (trail.length === 0) return null;
  const crumbs = [{ label: rootLabel, to: rootTo }, ...trail];
  return (
    <nav aria-label="Breadcrumb" className="border-t border-line/70">
      <ol className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-1.5 gap-y-1 px-4 py-2 text-[13px] text-ink-soft sm:px-6">
        {crumbs.map((crumb, i) => {
          const last = i === crumbs.length - 1;
          return (
            <li key={`${i}-${crumb.label}`} className="flex items-center gap-1.5">
              {i > 0 && (
                <svg aria-hidden="true" viewBox="0 0 24 24" className="size-3.5 text-ink-faint" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="m9 6 6 6-6 6" />
                </svg>
              )}
              {last || !crumb.to ? (
                <span aria-current={last ? 'page' : undefined} className={last ? 'font-medium text-ink' : undefined}>
                  {crumb.label}
                </span>
              ) : (
                <Link to={crumb.to} className="underline-offset-2 hover:text-ink hover:underline">
                  {crumb.label}
                </Link>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}
