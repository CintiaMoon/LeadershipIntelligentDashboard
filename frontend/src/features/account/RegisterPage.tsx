import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { identityApi, describeAuthError } from '@/services/api/identity';
import { Notice, PASSWORD_HELP, PasswordField, TextField } from './AuthShell';
import { SplitAuthLayout } from './SplitAuthLayout';

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
      <SplitAuthLayout eyebrow="Almost there" title="Check your email" switchText="Already confirmed?" switchLabel="Sign in" switchTo="/login">
        <Notice tone="ok">
          If <strong>{sentTo}</strong> can be used for a new account, we sent a message there. Open the link in it and enter your password to confirm the address. If you
          already have an account, the message says so.
        </Notice>
        <p className="text-sm text-ink-soft">The link works for 24 hours and only once.</p>
        <Link to="/login" className="inline-block text-sm font-medium text-accent-ink underline-offset-2 hover:underline">Back to sign in</Link>
      </SplitAuthLayout>
    );
  }

  return (
    <SplitAuthLayout
      eyebrow="Your leadership journey starts here"
      title="Create your account"
      subtitle="After you confirm your email address, you can be invited to a company."
      switchText="Already have an account?"
      switchLabel="Sign in"
      switchTo="/login"
      note="Your password is never stored in plain text."
    >
      <form onSubmit={(e) => void submit(e)} noValidate className="space-y-3">
        <TextField id="register-name" label="Your name" autoComplete="name" value={displayName} onChange={setDisplayName} maxLength={200} required />
        <TextField id="register-email" label="Email address" type="email" autoComplete="email" value={email} onChange={setEmail} maxLength={254} required />
        <div className="space-y-2">
          <PasswordField id="register-password" label="Password" autoComplete="new-password" value={password} onChange={setPassword} help={PASSWORD_HELP} />
          <LengthMeter length={password.length} />
        </div>
        <PasswordField id="register-confirm" label="Repeat the password" autoComplete="new-password" value={confirm} onChange={setConfirm} error={mismatch} />
        {error && <Notice tone="error">{error}</Notice>}
        <button
          type="submit"
          className="inline-flex min-h-11 w-full items-center justify-center gap-2 rounded-md bg-accent px-3.5 text-sm font-semibold text-on-accent transition-colors hover:bg-accent/90 disabled:opacity-50"
          disabled={pending || !displayName.trim() || !email.trim() || password.length < 12 || password !== confirm}
        >
          <svg aria-hidden="true" viewBox="0 0 24 24" className="size-4" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M5 12h14m-6-6 6 6-6 6" />
          </svg>
          {pending ? 'Creating' : 'Create account'}
        </button>
      </form>
    </SplitAuthLayout>
  );
}

/** How long the password is against the 12 characters the server asks for. It says nothing about strength: the server decides that. */
function LengthMeter({ length }: { length: number }) {
  const filled = length === 0 ? 0 : length < 8 ? 1 : length < 12 ? 2 : length < 16 ? 3 : 4;
  const label = length === 0 ? 'Length' : length < 12 ? 'Too short' : 'Long enough';
  return (
    <div aria-hidden="true" className="flex items-center gap-3 text-[11px]">
      <div className="grid flex-1 grid-cols-4 gap-1.5">
        {[1, 2, 3, 4].map((i) => (
          <span key={i} className={`h-0.5 rounded-full ${i <= filled ? (filled >= 3 ? 'bg-accent' : 'bg-risk-medium') : 'bg-line'}`} />
        ))}
      </div>
      <span className={filled >= 3 ? 'text-accent-ink' : 'text-ink-soft'}>{label}</span>
      <span className="text-ink-faint">12+ characters</span>
    </div>
  );
}
