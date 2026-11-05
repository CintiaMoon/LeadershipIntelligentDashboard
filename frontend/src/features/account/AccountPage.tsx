import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/app/providers/auth-context';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { identityApi, describeAuthError } from '@/services/api/identity';
import { Notice, PASSWORD_HELP, PasswordField } from './AuthShell';

/** The signed-in person's own account: who they are, and changing the password (decision D73). Outside any company. */
export function AccountPage() {
  const { account, signOut } = useAuth();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [changed, setChanged] = useState(false);
  const mismatch = confirm.length > 0 && confirm !== next ? 'The two passwords are not the same.' : undefined;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (next !== confirm) return;
    setError(null);
    setChanged(false);
    setPending(true);
    try {
      await identityApi.changePassword(current, next);
      setChanged(true);
      setCurrent('');
      setNext('');
      setConfirm('');
    } catch (e) {
      setError(describeAuthError(e));
    } finally {
      setPending(false);
    }
  };

  return (
    <main className="mx-auto max-w-2xl space-y-6 px-4 py-10">
      <header className="space-y-1">
        <Link to="/companies" className="text-sm text-ink-soft hover:text-ink">
          &larr; My companies
        </Link>
        <h1 className="font-display text-3xl font-semibold tracking-tight">Your account</h1>
      </header>
      <Card>
        <CardHeader title="Who you are" />
        <dl className="grid grid-cols-[8rem_1fr] gap-y-2 px-5 py-4 text-sm">
          <dt className="text-ink-soft">Name</dt>
          <dd>{account?.displayName}</dd>
          <dt className="text-ink-soft">Email address</dt>
          <dd>{account?.email}</dd>
        </dl>
      </Card>
      <Card>
        <CardHeader title="Change your password" description="Your other sign-ins are ended when you do." />
        <form onSubmit={(e) => void submit(e)} noValidate className="space-y-4 px-5 py-4">
          <PasswordField id="current-password" label="Current password" autoComplete="current-password" value={current} onChange={setCurrent} />
          <PasswordField id="new-password" label="New password" autoComplete="new-password" value={next} onChange={setNext} help={PASSWORD_HELP} />
          <PasswordField id="confirm-password" label="Repeat the new password" autoComplete="new-password" value={confirm} onChange={setConfirm} error={mismatch} />
          {error && <Notice tone="error">{error}</Notice>}
          {changed && <Notice tone="ok">Your password was changed. Your other sign-ins were ended and we sent you an email about it.</Notice>}
          <Button type="submit" variant="primary" disabled={pending || !current || next.length < 12 || next !== confirm}>
            {pending ? 'Saving' : 'Change password'}
          </Button>
        </form>
      </Card>
      <Button onClick={() => void signOut()}>Sign out</Button>
    </main>
  );
}
