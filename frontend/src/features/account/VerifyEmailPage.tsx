import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { identityApi, describeAuthError } from '@/services/api/identity';
import { AuthShell, Notice, PasswordField, TextField } from './AuthShell';

/**
 * Confirming an email address (decision D66). The link carries the token; the person also enters the password chosen at
 * registration, so a stranger who registered someone else's address cannot get it confirmed by that person opening the link.
 * Without a token the page offers to send the link again.
 */
export function VerifyEmailPage() {
  const [params] = useSearchParams();
  const token = params.get('token');
  return token ? <Confirm token={token} /> : <Resend />;
}

function Confirm({ token }: { token: string }) {
  const [password, setPassword] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setPending(true);
    try {
      await identityApi.verifyEmail(token, password);
      setDone(true);
    } catch (e) {
      setError(describeAuthError(e));
      setPassword('');
    } finally {
      setPending(false);
    }
  };

  if (done) {
    return (
      <AuthShell title="Email address confirmed">
        <Notice tone="ok">Your account is ready.</Notice>
        <Link to="/login" className="inline-flex min-h-9 w-full items-center justify-center rounded-md bg-ink px-3.5 text-sm font-medium text-canvas hover:bg-ink/90">
          Sign in
        </Link>
      </AuthShell>
    );
  }
  return (
    <AuthShell
      title="Confirm your email address"
      subtitle="Enter the password you chose when you created the account."
      footer={<Link to="/verify-email" className="text-ink-soft underline-offset-2 hover:underline">Send me a new link</Link>}
    >
      <form onSubmit={(e) => void submit(e)} noValidate className="space-y-4">
        <PasswordField id="verify-password" label="Password" autoComplete="current-password" value={password} onChange={setPassword} />
        {error && <Notice tone="error">{error}</Notice>}
        <Button type="submit" variant="primary" className="w-full" disabled={pending || !password}>
          {pending ? 'Confirming' : 'Confirm'}
        </Button>
      </form>
    </AuthShell>
  );
}

function Resend() {
  const [email, setEmail] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setPending(true);
    try {
      await identityApi.resendVerification(email.trim());
      setSent(true);
    } catch (e) {
      setError(describeAuthError(e));
    } finally {
      setPending(false);
    }
  };

  return (
    <AuthShell
      title="Send the confirmation link again"
      subtitle="For an account that was created but not yet confirmed."
      footer={<Link to="/login" className="font-medium text-accent-ink underline-offset-2 hover:underline">Back to sign in</Link>}
    >
      {sent ? (
        <Notice tone="ok">If an account for that address is waiting to be confirmed, we sent a new link. The earlier one no longer works.</Notice>
      ) : (
        <form onSubmit={(e) => void submit(e)} noValidate className="space-y-4">
          <TextField id="resend-email" label="Email address" type="email" autoComplete="email" value={email} onChange={setEmail} required />
          {error && <Notice tone="error">{error}</Notice>}
          <Button type="submit" variant="primary" className="w-full" disabled={pending || !email.trim()}>
            {pending ? 'Sending' : 'Send the link'}
          </Button>
        </form>
      )}
    </AuthShell>
  );
}
