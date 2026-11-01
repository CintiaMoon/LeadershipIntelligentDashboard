import { useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/app/providers/auth-context';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { PERSONAS } from '@/services/auth/oidc';
import { authConfig } from '@/services/auth/config';

export function LoginPage() {
  const { status, signIn, principal } = useAuth();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from;
  const [error, setError] = useState<string | null>(null);

  if (status === 'authenticated') return <Navigate to={from ?? (principal?.platformAdmin ? '/platform/tenants' : '/dashboard')} replace />;

  const start = async (personaId?: string) => {
    setError(null);
    try {
      await signIn(personaId, from);
    } catch {
      setError('The sign-in service could not be reached. Check that it is running and try again.');
    }
  };

  return (
    <main className="mx-auto grid min-h-screen max-w-xl place-items-center px-4 py-12">
      <div className="w-full space-y-6">
        <div className="text-center">
          <span aria-hidden="true" className="mx-auto grid size-12 place-items-center rounded-xl bg-accent text-lg font-bold text-white">
            LI
          </span>
          <h1 className="mt-4 text-2xl font-semibold tracking-tight">Leadership Intelligence</h1>
          <p className="mt-1 text-sm text-ink-soft">An organizational diagnostic, not an individual performance rating.</p>
        </div>
        <Card className="p-6">
          {authConfig.devPersonas ? (
            <>
              <h2 className="text-base font-semibold">Choose a development persona</h2>
              <p className="mt-1 text-sm text-ink-soft">Local sign-in with the mock identity provider. This list is not available in production builds.</p>
              <ul className="mt-4 space-y-2">
                {PERSONAS.map((persona) => (
                  <li key={persona.id}>
                    <button
                      type="button"
                      onClick={() => void start(persona.id)}
                      className="flex w-full items-center justify-between rounded-lg border border-line px-4 py-3 text-left transition-colors hover:border-accent hover:bg-accent-soft"
                    >
                      <span>
                        <span className="block text-sm font-medium">{persona.label}</span>
                        <span className="block text-xs text-ink-soft">{persona.description}</span>
                      </span>
                      <span aria-hidden="true" className="text-ink-faint">
                        &rarr;
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            </>
          ) : (
            <div className="text-center">
              <p className="text-sm text-ink-soft">Sign in with your organization account to continue.</p>
              <Button variant="primary" className="mt-4" onClick={() => void start()}>
                Sign in
              </Button>
            </div>
          )}
          {error && (
            <p role="alert" className="mt-4 text-sm text-risk-critical">
              {error}
            </p>
          )}
        </Card>
      </div>
    </main>
  );
}
