import { useEffect, useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/app/providers/auth-context';
import { Button } from '@/components/ui/Button';
import { identityApi, describeAuthError } from '@/services/api/identity';
import { AuthShell, Notice, PasswordField, TextField } from './AuthShell';
import { goTo, safeReturn } from './navigation';

/** Signing in with an email address and a password (decisions D65, D67). Every failure reads the same on purpose. */
export function SessionLoginPage() {
  const { status, refresh } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [params] = useSearchParams();
  const from = safeReturn(params.get('from') ?? (location.state as { from?: string } | null)?.from);
  const [email, setEmail] = useState(params.get('email') ?? '');
  const [password, setPassword] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // signed in, whether just now or already: continue to where the person was going, or to their companies
  useEffect(() => {
    if (status === 'authenticated') goTo(navigate, from ?? '/');
  }, [status, from, navigate]);

  if (status === 'authenticated') return null;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setPending(true);
    try {
      await identityApi.login(email.trim(), password);
      await refresh?.();
    } catch (e) {
      setError(describeAuthError(e));
      setPassword('');
    } finally {
      setPending(false);
    }
  };

  return (
    <AuthShell
      title="Sign in"
      subtitle="An organizational diagnostic, not an individual performance rating."
      footer={
        <>
          New here? <Link to="/register" className="font-medium text-accent-ink underline-offset-2 hover:underline">Create an account</Link>
        </>
      }
    >
      <form onSubmit={(e) => void submit(e)} noValidate className="space-y-4">
        <TextField id="login-email" label="Email address" type="email" autoComplete="username" value={email} onChange={setEmail} required />
        <PasswordField id="login-password" label="Password" autoComplete="current-password" value={password} onChange={setPassword} />
        {error && <Notice tone="error">{error}</Notice>}
        <Button type="submit" variant="primary" className="w-full" disabled={pending || !email.trim() || !password}>
          {pending ? 'Signing in' : 'Sign in'}
        </Button>
      </form>
      <p className="flex flex-wrap justify-between gap-2 text-sm">
        {import.meta.env.VITE_DEMO_LOGIN === 'true' && (
          <Link to="/demo-login" className="text-ink-soft underline-offset-2 hover:underline">Test accounts</Link>
        )}
        <Link to="/forgot-password" className="text-ink-soft underline-offset-2 hover:underline">Forgot your password?</Link>
        <Link to="/verify-email" className="text-ink-soft underline-offset-2 hover:underline">Did not get the confirmation email?</Link>
      </p>
    </AuthShell>
  );
}
