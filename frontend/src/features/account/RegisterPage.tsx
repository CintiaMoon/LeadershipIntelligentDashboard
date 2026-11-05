import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { Button } from '@/components/ui/Button';
import { identityApi, describeAuthError } from '@/services/api/identity';
import { AuthShell, Notice, PASSWORD_HELP, PasswordField, TextField } from './AuthShell';

/**
 * Creating an account (decision D65). The answer is the same whether or not the address already has one, so the page says only that
 * an email was sent. An account has no company: that comes with an invitation.
 */
export function RegisterPage() {
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [sentTo, setSentTo] = useState<string | null>(null);

  const mismatch = confirm.length > 0 && confirm !== password ? 'The two passwords are not the same.' : undefined;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (password !== confirm) return;
    setError(null);
    setPending(true);
    try {
      await identityApi.register(email.trim(), password, displayName.trim());
      setSentTo(email.trim());
    } catch (e) {
      setError(describeAuthError(e));
    } finally {
      setPending(false);
    }
  };

  if (sentTo) {
    return (
      <AuthShell title="Check your email" footer={<Link to="/login" className="font-medium text-accent-ink underline-offset-2 hover:underline">Back to sign in</Link>}>
        <Notice tone="ok">
          If <strong>{sentTo}</strong> can be used for a new account, we sent a message there. Open the link in it and enter your password to confirm the address. If you
          already have an account, the message says so.
        </Notice>
        <p className="text-sm text-ink-soft">The link works for 24 hours and only once.</p>
      </AuthShell>
    );
  }

  return (
    <AuthShell
      title="Create an account"
      subtitle="After you confirm your email address, you can be invited to a company."
      footer={
        <>
          Already have an account? <Link to="/login" className="font-medium text-accent-ink underline-offset-2 hover:underline">Sign in</Link>
        </>
      }
    >
      <form onSubmit={(e) => void submit(e)} noValidate className="space-y-4">
        <TextField id="register-name" label="Your name" autoComplete="name" value={displayName} onChange={setDisplayName} maxLength={200} required />
        <TextField id="register-email" label="Email address" type="email" autoComplete="email" value={email} onChange={setEmail} maxLength={254} required />
        <PasswordField id="register-password" label="Password" autoComplete="new-password" value={password} onChange={setPassword} help={PASSWORD_HELP} />
        <PasswordField id="register-confirm" label="Repeat the password" autoComplete="new-password" value={confirm} onChange={setConfirm} error={mismatch} />
        {error && <Notice tone="error">{error}</Notice>}
        <Button
          type="submit"
          variant="primary"
          className="w-full"
          disabled={pending || !displayName.trim() || !email.trim() || password.length < 12 || password !== confirm}
        >
          {pending ? 'Creating' : 'Create account'}
        </Button>
      </form>
    </AuthShell>
  );
}
