import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { identityApi, describeAuthError } from '@/services/api/identity';
import { AuthShell, Notice, PASSWORD_HELP, PasswordField, TextField } from './AuthShell';

/** Asking for a password reset (decision D73). The answer is the same for every address. */
export function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setPending(true);
    try {
      await identityApi.forgotPassword(email.trim());
      setSent(true);
    } catch (e) {
      setError(describeAuthError(e));
    } finally {
      setPending(false);
    }
  };

  return (
    <AuthShell
      title="Reset your password"
      subtitle="We will email you a link to choose a new one."
      footer={<Link to="/login" className="font-medium text-accent-ink underline-offset-2 hover:underline">Back to sign in</Link>}
    >
      {sent ? (
        <Notice tone="ok">If an account exists for that address, we sent a link to it. It works for one hour and only once.</Notice>
      ) : (
        <form onSubmit={(e) => void submit(e)} noValidate className="space-y-4">
          <TextField id="forgot-email" label="Email address" type="email" autoComplete="email" value={email} onChange={setEmail} required />
          {error && <Notice tone="error">{error}</Notice>}
          <Button type="submit" variant="primary" className="w-full" disabled={pending || !email.trim()}>
            {pending ? 'Sending' : 'Send the link'}
          </Button>
        </form>
      )}
    </AuthShell>
  );
}

/** Choosing a new password with the link from the email (decision D73). Every sign-in of the account ends. */
export function ResetPasswordPage() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  if (!token) {
    return (
      <AuthShell title="This link is incomplete" footer={<Link to="/forgot-password" className="font-medium text-accent-ink underline-offset-2 hover:underline">Ask for a new link</Link>}>
        <Notice tone="error">Open the link from the email exactly as it was sent.</Notice>
      </AuthShell>
    );
  }

  const mismatch = confirm.length > 0 && confirm !== password ? 'The two passwords are not the same.' : undefined;
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (password !== confirm) return;
    setError(null);
    setPending(true);
    try {
      await identityApi.resetPassword(token, password);
      setDone(true);
    } catch (e) {
      setError(describeAuthError(e));
    } finally {
      setPending(false);
    }
  };

  if (done) {
    return (
      <AuthShell title="Password changed">
        <Notice tone="ok">Your password was changed and you were signed out everywhere. Sign in with the new one.</Notice>
        <Link to="/login" className="inline-flex min-h-9 w-full items-center justify-center rounded-md bg-ink px-3.5 text-sm font-medium text-canvas hover:bg-ink/90">
          Sign in
        </Link>
      </AuthShell>
    );
  }
  return (
    <AuthShell title="Choose a new password" footer={<Link to="/forgot-password" className="text-ink-soft underline-offset-2 hover:underline">Ask for a new link</Link>}>
      <form onSubmit={(e) => void submit(e)} noValidate className="space-y-4">
        <PasswordField id="reset-password" label="New password" autoComplete="new-password" value={password} onChange={setPassword} help={PASSWORD_HELP} />
        <PasswordField id="reset-confirm" label="Repeat the new password" autoComplete="new-password" value={confirm} onChange={setConfirm} error={mismatch} />
        {error && <Notice tone="error">{error}</Notice>}
        <Button type="submit" variant="primary" className="w-full" disabled={pending || password.length < 12 || password !== confirm}>
          {pending ? 'Saving' : 'Change password'}
        </Button>
      </form>
    </AuthShell>
  );
}
