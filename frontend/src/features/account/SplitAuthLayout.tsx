import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { brand } from '@/brand';
import { BrandMark } from '@/components/ui/BrandMark';

const POINTS = [
  { title: 'Understand your people', text: 'Bring every leadership perspective into focus.' },
  { title: 'Build aligned teams', text: 'Make shared strengths your next advantage.' },
  { title: 'Lead with intention', text: 'Move from assessment to measurable progress.' },
];

function Check() {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" className="size-3.5" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="m5 12.5 4.5 4.5L19 7.5" />
    </svg>
  );
}

/** A compass with a bolt at its centre: decorative, the headline and the list beside it carry the meaning. */
function Compass() {
  const spokes = Array.from({ length: 8 }, (_, i) => (i * Math.PI) / 4);
  return (
    <div aria-hidden="true" className="relative mx-auto my-4 aspect-square w-full max-w-[min(15rem,26vh)]">
      <svg viewBox="-100 -112 200 212" className="size-full">
        <g fill="none" stroke="currentColor" className="text-accent">
          <circle r="96" strokeOpacity="0.14" />
          <circle r="70" strokeOpacity="0.22" />
          <circle r="44" strokeOpacity="0.3" />
          {spokes.map((a) => (
            <line key={a} x1={Math.cos(a) * 44} y1={Math.sin(a) * 44} x2={Math.cos(a) * 96} y2={Math.sin(a) * 96} strokeOpacity="0.14" />
          ))}
        </g>
        <circle r="44" className="fill-canvas" />
        <path d="M6 -34 -22 6h16l-8 30 30-44H0Z" className="fill-accent" />
        <circle r="2.6" className="fill-canvas" />
        <text y="-101" textAnchor="middle" className="fill-accent" fontSize="8">
          N
        </text>
      </svg>
      <span className="absolute left-[-1.5rem] top-[22%] rounded-md border border-line bg-surface px-3 py-1.5 text-[11px] text-ink-soft shadow-card">People, connected</span>
      <span className="absolute bottom-[18%] right-[-1.5rem] rounded-md border border-line bg-surface px-3 py-1.5 text-[11px] text-ink-soft shadow-card">Insight into action</span>
    </div>
  );
}

/**
 * The two-column frame of the account pages that introduce the product: the brand and what it is for on the left (large screens
 * only), the form on the right with the way to the other page above it.
 */
export function SplitAuthLayout({
  eyebrow,
  title,
  subtitle,
  switchText,
  switchLabel,
  switchTo,
  children,
  note,
}: {
  eyebrow: string;
  title: string;
  subtitle?: ReactNode;
  switchText: string;
  switchLabel: string;
  switchTo: string;
  children: ReactNode;
  note?: ReactNode;
}) {
  return (
    <div className="grid min-h-screen bg-canvas text-ink lg:grid-cols-2">
      <aside className="hidden flex-col justify-between border-r border-line bg-sidebar px-12 py-6 lg:flex">
        <Link to="/" className="flex items-center gap-3" aria-label={`${brand.name}, home`}>
          <BrandMark className="size-9" />
          <span className="leading-tight">
            <span className="block font-display text-xl font-semibold tracking-tight">{brand.name}</span>
            <span className="block text-[10px] uppercase tracking-[0.18em] text-ink-faint">{brand.tagline}</span>
          </span>
        </Link>

        <div className="mx-auto w-full max-w-md py-4">
          <p className="flex items-center gap-3 text-[11px] font-medium uppercase tracking-[0.2em] text-accent-ink">
            <span aria-hidden="true" className="h-px w-8 bg-accent" />
            Clarity to lead.
          </p>
          <p className="mt-4 font-display text-4xl xl:text-5xl font-medium leading-[1.1] tracking-tight">Better leadership. Stronger organizations.</p>
          <p className="mt-3 text-sm text-ink-soft">Turn leadership insight into meaningful action. Connect your people, understand your teams, and lead with confidence.</p>
          <Compass />
          <ul className="space-y-3">
            {POINTS.map((p) => (
              <li key={p.title} className="flex items-start gap-3">
                <span aria-hidden="true" className="mt-0.5 grid size-6 shrink-0 place-items-center rounded-md bg-accent-soft text-accent-ink">
                  <Check />
                </span>
                <span>
                  <span className="block text-sm font-medium">{p.title}</span>
                  <span className="block text-xs text-ink-soft">{p.text}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>

        <p className="flex items-center justify-between text-[11px] text-ink-faint">
          <span>
            &copy; {new Date().getFullYear()} {brand.name}. All rights reserved.
          </span>
          <span>Built on trust</span>
        </p>
      </aside>

      <div className="flex min-h-screen flex-col px-6 py-6 sm:px-12">
        <div className="flex items-center justify-between gap-4">
          <Link to="/" className="flex items-center gap-2 lg:invisible" aria-label={`${brand.name}, home`}>
            <BrandMark className="size-8" />
            <span className="font-display text-lg font-semibold">{brand.name}</span>
          </Link>
          <p className="text-xs text-ink-soft">
            {switchText}{' '}
            <Link to={switchTo} className="font-medium text-accent-ink underline-offset-2 hover:underline">
              {switchLabel} &rarr;
            </Link>
          </p>
        </div>

        <main className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center py-4">
          <p className="text-[11px] font-medium uppercase tracking-[0.2em] text-accent-ink">{eyebrow}</p>
          <h1 className="mt-2 font-display text-3xl font-medium tracking-tight">{title}</h1>
          {subtitle && <p className="mt-2 text-sm text-ink-soft">{subtitle}</p>}
          <div className="mt-5 space-y-4">{children}</div>
          {note && <p className="mt-4 text-center text-xs text-ink-faint">{note}</p>}
        </main>

        <p className="text-center text-[11px] text-ink-faint lg:text-left">{brand.productName}</p>
      </div>
    </div>
  );
}
