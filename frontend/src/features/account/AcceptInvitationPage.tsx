import { useState, type FormEvent } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/app/providers/auth-context';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { identityApi, describeAuthError } from '@/services/api/identity';
import { humanizeCode } from '@/utils/presentation';
import { AuthShell, Notice, PASSWORD_HELP, PasswordField, TextField } from './AuthShell';

const primaryLink = 'inline-flex min-h-9 w-full items-center justify-center rounded-md bg-ink px-3.5 text-sm font-medium text-canvas hover:bg-ink/90';

/**
 * Opening the link of an invitation to a company (decision D72). Someone whose address already has an account signs in and accepts;
 * someone without one creates the account here, and the link proves the mailbox, so no separate confirmation is needed.
 */
export function AcceptInvitationPage() {
  const [params] = useSearchParams();
  const token = params.get('token');
  const preview = useQuery({
    queryKey: ['lis', 'invitation', token],
    queryFn: () => identityApi.previewInvitation(token as string),
    enabled: !!token,
    retry: false,
    staleTime: Infinity,
  });

  if (!token) {
    return (
      <AuthShell title="This link is incomplete">
        <Notice tone="error">Open the link from the invitation email exactly as it was sent.</Notice>
      </AuthShell>
    );
  }
  if (preview.isPending) {
    return (
      <AuthShell title="Opening the invitation">
        <p role="status" className="text-sm text-ink-soft">Checking the link&hellip;</p>
      </AuthShell>
    );
  }
  if (preview.isError) {
    return (
      <AuthShell title="This invitation cannot be used" footer={<Link to="/login" className="font-medium text-accent-ink underline-offset-2 hover:underline">Sign in</Link>}>
        <Notice tone="error">{describeAuthError(preview.error)}</Notice>
        <p className="text-sm text-ink-soft">Ask the person who invited you to send it again.</p>
      </AuthShell>
    );
  }
  const offer = preview.data;
  return (
    <AuthShell
      title={`Join ${offer.companyName}`}
      subtitle={
        <>
          The invitation was sent to <strong>{offer.email}</strong>.
        </>
      }
    >
      <div className="flex flex-wrap items-center gap-2 text-sm" aria-label="Roles offered">
        <span className="text-ink-soft">You would be</span>
        {offer.roles.map((role) => (
          <Badge key={role} tone="accent">
            {humanizeCode(role)}
          </Badge>
        ))}
      </div>
      {offer.accountExists ? <HasAccount token={token} email={offer.email} /> : <CreateAccount token={token} email={offer.email} />}
    </AuthShell>
  );
}

function HasAccount({ token, email }: { token: string; email: string }) {
  const { status, account, refresh, signOut } = useAuth();
  const accept = useMutation({
    mutationFn: () => identityApi.acceptInvitation(token),
    onSuccess: () => refresh?.(),
  });
  const here = `/invitations/accept?token=${encodeURIComponent(token)}`;

  if (accept.isSuccess) {
    return (
      <>
        <Notice tone="ok">You joined the company.</Notice>
        <a href="/companies" className={primaryLink}>
          Open my companies
        </a>
      </>
    );
  }
  if (status !== 'authenticated') {
    return (
      <>
        <p className="text-sm text-ink-soft">This address already has an account. Sign in to accept the invitation.</p>
        <Link to={`/login?from=${encodeURIComponent(here)}&email=${encodeURIComponent(email)}`} className={primaryLink}>
          Sign in to accept
        </Link>
      </>
    );
  }
  if (account && account.email.toLowerCase() !== email.toLowerCase()) {
    return (
      <>
        <Notice tone="error">
          You are signed in as <strong>{account.email}</strong>, but the invitation is for <strong>{email}</strong>.
        </Notice>
        <Button className="w-full" onClick={() => void signOut()}>
          Sign out, then sign in with {email}
        </Button>
      </>
    );
  }
  return (
    <>
      {accept.isError && <Notice tone="error">{describeAuthError(accept.error)}</Notice>}
      <Button variant="primary" className="w-full" disabled={accept.isPending} onClick={() => accept.mutate()}>
        {accept.isPending ? 'Accepting' : 'Accept the invitation'}
      </Button>
    </>
  );
}

function CreateAccount({ token, email }: { token: string; email: string }) {
  const [displayName, setDisplayName] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const register = useMutation({ mutationFn: () => identityApi.registerWithInvitation(token, password, displayName.trim()) });
  const mismatch = confirm.length > 0 && confirm !== password ? 'The two passwords are not the same.' : undefined;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (password === confirm) register.mutate();
  };

  if (register.isSuccess) {
    return (
      <>
        <Notice tone="ok">Your account is ready and you are a member. Sign in to continue.</Notice>
        <Link to={`/login?email=${encodeURIComponent(email)}`} className={primaryLink}>
          Sign in
        </Link>
      </>
    );
  }
  return (
    <form onSubmit={submit} noValidate className="space-y-4">
      <p className="text-sm text-ink-soft">Create your account to accept. There is no separate confirmation email: this link already proves the address.</p>
      <TextField id="invite-name" label="Your name" autoComplete="name" value={displayName} onChange={setDisplayName} maxLength={200} required />
      <PasswordField id="invite-password" label="Password" autoComplete="new-password" value={password} onChange={setPassword} help={PASSWORD_HELP} />
      <PasswordField id="invite-confirm" label="Repeat the password" autoComplete="new-password" value={confirm} onChange={setConfirm} error={mismatch} />
      {register.isError && <Notice tone="error">{describeAuthError(register.error)}</Notice>}
      <Button type="submit" variant="primary" className="w-full" disabled={register.isPending || !displayName.trim() || password.length < 12 || password !== confirm}>
        {register.isPending ? 'Creating' : 'Create account and join'}
      </Button>
    </form>
  );
}
