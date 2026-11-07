import { Link } from 'react-router-dom';
import { brand } from '@/brand';
import { BrandMark } from '@/components/ui/BrandMark';
import { linkButtonClass, secondaryLinkClass } from '@/components/ui/Button';

const DIMENSIONS = [
  { name: 'Team health', text: 'Pace, workload and how people feel about the work they do together.' },
  { name: 'Delivery stability', text: 'How predictable delivery is and how well commitments hold.' },
  { name: 'Technical risk', text: 'How well technical risk and debt are understood and controlled.' },
  { name: 'Leadership load', text: 'Whether the load on leaders is one they can carry.' },
];

const PROMISES = [
  { title: 'Explained, never a black box', text: 'Every score can be followed from the answers, through the dimensions and the patterns found, to the final number.' },
  { title: 'Answers stay anonymous', text: 'Team members answer a questionnaire. Their answers are combined per team and never listed one by one or tied to a person.' },
  { title: 'Reports you can share', text: 'Read the result on screen, compare two assessments, or save a report as a PDF or a CSV file.' },
];

/**
 * The page at the root of the site for someone who is not signed in: what the product is, and the two ways in. A signed-in person
 * never sees it; the root takes them to their companies.
 */
export function LandingPage() {
  return (
    <div className="min-h-screen bg-canvas text-ink">
      <header className="mx-auto flex max-w-5xl items-center justify-between px-4 py-5 sm:px-6">
        <div className="flex items-center gap-3">
          <BrandMark className="size-9" />
          <span className="font-display text-lg font-semibold tracking-tight">{brand.name}</span>
        </div>
        <nav aria-label="Account" className="flex items-center gap-2">
          <Link to="/register" className="px-2 text-sm font-medium text-ink-soft underline-offset-2 hover:text-ink hover:underline">
            Create an account
          </Link>
          <Link to="/login" className={linkButtonClass}>
            Sign in
          </Link>
        </nav>
      </header>

      <main>
        <section className="mx-auto max-w-5xl px-4 pb-14 pt-10 sm:px-6 sm:pt-16">
          <p className="text-sm font-medium text-accent-ink">{brand.tagline}</p>
          <h1 className="mt-3 max-w-3xl font-display text-4xl font-semibold tracking-tight sm:text-5xl">See how your teams are really led, and what to do about it.</h1>
          <p className="mt-5 max-w-2xl text-lg text-ink-soft">
            {brand.productName} turns the answers of your teams into one Leadership Intelligence Score, four dimensions behind it, and the patterns that explain it.
          </p>
          <div className="mt-8 flex flex-wrap items-center gap-3">
            <Link to="/login" className={linkButtonClass}>
              Sign in
            </Link>
            <Link to="/register" className={secondaryLinkClass}>
              Create an account
            </Link>
            {import.meta.env.VITE_DEMO_LOGIN === 'true' && (
              <Link to="/demo-login" className="px-2 text-sm text-ink-soft underline-offset-2 hover:underline">
                Test accounts
              </Link>
            )}
          </div>
        </section>

        <section aria-labelledby="dimensions" className="border-y border-line bg-sidebar">
          <div className="mx-auto max-w-5xl px-4 py-12 sm:px-6">
            <h2 id="dimensions" className="text-xl font-semibold tracking-tight">
              Four dimensions, one score
            </h2>
            <ul className="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {DIMENSIONS.map((d) => (
                <li key={d.name} className="rounded-lg border border-line bg-surface p-4 shadow-card">
                  <p className="font-medium">{d.name}</p>
                  <p className="mt-1 text-sm text-ink-soft">{d.text}</p>
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section aria-labelledby="promises" className="mx-auto max-w-5xl px-4 py-12 sm:px-6">
          <h2 id="promises" className="text-xl font-semibold tracking-tight">
            Built to be trusted
          </h2>
          <ul className="mt-6 grid gap-6 md:grid-cols-3">
            {PROMISES.map((p) => (
              <li key={p.title}>
                <p className="font-medium">{p.title}</p>
                <p className="mt-1 text-sm text-ink-soft">{p.text}</p>
              </li>
            ))}
          </ul>
        </section>
      </main>

      <footer className="border-t border-line">
        <p className="mx-auto max-w-5xl px-4 py-6 text-sm text-ink-faint sm:px-6">
          The score is a diagnostic signal about an organization, never a judgment of a person.
        </p>
      </footer>
    </div>
  );
}
