import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { getUserManager, takeReturnTo } from '@/services/auth/oidc';

/** Completes the authorization-code flow once, then returns to where the user was heading. */
export function CallbackPage() {
  const navigate = useNavigate();
  const started = useRef(false);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    getUserManager()
      .signinRedirectCallback()
      .then(() => navigate(takeReturnTo(), { replace: true }))
      .catch(() => setFailed(true));
  }, [navigate]);

  if (failed) {
    return (
      <main className="mx-auto max-w-md px-4 py-24 text-center">
        <h1 className="text-lg font-semibold">Sign-in did not complete</h1>
        <p className="mt-1 text-sm text-ink-soft">Try again from the login page.</p>
        <Button className="mt-4" variant="primary" onClick={() => navigate('/login', { replace: true })}>
          Back to sign in
        </Button>
      </main>
    );
  }
  return (
    <main role="status" className="mx-auto max-w-md px-4 py-24 text-center text-sm text-ink-soft">
      Signing you in&hellip;
    </main>
  );
}
