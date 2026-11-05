import { useState, type FormEvent } from 'react';
import { useAuth } from '@/app/providers/auth-context';
import { Badge, type Tone } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Field, FormError, inputClass, selectClass } from '@/components/ui/Form';
import { PageHeader } from '@/components/ui/PageHeader';
import { QuerySection } from '@/components/ui/QuerySection';
import { EmptyState } from '@/components/ui/StateViews';
import { parseList } from '@/features/members/rules';
import { useApiTokens, useCreateApiToken, useRevokeApiToken } from '@/services/queries/identity';
import { TOKEN_ROLES, type ApiToken, type CreatedApiToken } from '@/types/identity';
import { humanizeCode } from '@/utils/presentation';

const statusTone: Record<string, Tone> = { ACTIVE: 'ok', EXPIRED: 'medium', REVOKED: 'neutral' };

function when(iso?: string | null): string {
  return iso ? new Date(iso).toLocaleString() : 'never';
}

/** The end of the chosen day, as an instant (a token chosen to expire on a day works through that day, UTC). */
function endOfDay(date: string): string | undefined {
  return date ? `${date}T23:59:59Z` : undefined;
}

/**
 * API tokens for programs that call the application (decision D70). The secret is shown once, when the token is created, and exists
 * nowhere else; this page keeps it only until it is closed. A token is bound to the person who created it and stops when they lose
 * access.
 */
export function ApiTokensPage() {
  const tokens = useApiTokens();
  const [created, setCreated] = useState<CreatedApiToken | null>(null);

  return (
    <div className="space-y-6">
      <PageHeader
        title="API tokens"
        subtitle="Let a program use the API for this company without a person signing in. A token has one role, never owner, and stops working if the person who made it loses that access."
      />
      {created && <Secret created={created} onClose={() => setCreated(null)} />}
      <CreateForm onCreated={setCreated} />
      <QuerySection query={tokens} label="the API tokens">
        {() => {
          const list = tokens.data ?? [];
          if (list.length === 0) return <EmptyState title="No API tokens" description="Create one above when a program needs access." />;
          return (
            <Card>
              <CardHeader title={`${list.length} ${list.length === 1 ? 'token' : 'tokens'}`} />
              <ul className="divide-y divide-line">
                {list.map((token) => (
                  <TokenItem key={token.id} token={token} />
                ))}
              </ul>
            </Card>
          );
        }}
      </QuerySection>
    </div>
  );
}

function Secret({ created, onClose }: { created: CreatedApiToken; onClose: () => void }) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(created.secret);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };
  return (
    <Card className="border-accent" role="region" aria-label="New API token">
      <CardHeader title={`Token “${created.token.name}” created`} description="Copy it now. It is shown only this once and cannot be read again; if it is lost, make a new one and revoke this one." />
      <div className="space-y-3 px-5 py-4">
        <label htmlFor="token-secret" className="text-sm font-medium">Secret</label>
        <input id="token-secret" readOnly value={created.secret} className={`${inputClass} font-mono`} onFocus={(e) => e.currentTarget.select()} />
        <p className="text-xs text-ink-soft">
          Send it as <span className="font-mono">Authorization: Bearer &lt;secret&gt;</span>. It needs no company header and no CSRF token.
        </p>
        <div className="flex gap-2">
          <Button variant="primary" onClick={() => void copy()}>
            {copied ? 'Copied' : 'Copy'}
          </Button>
          <Button onClick={onClose}>I have saved it</Button>
        </div>
      </div>
    </Card>
  );
}

function CreateForm({ onCreated }: { onCreated: (created: CreatedApiToken) => void }) {
  const { company } = useAuth();
  const create = useCreateApiToken();
  const [name, setName] = useState('');
  const [role, setRole] = useState<string>('VIEWER');
  const [expires, setExpires] = useState('');
  const [orgs, setOrgs] = useState('');
  const known = company?.organizationIds ?? [];

  const submit = (event: FormEvent) => {
    event.preventDefault();
    create.mutate(
      { name: name.trim(), role, expiresAt: endOfDay(expires), organizationIds: parseList(orgs) },
      {
        onSuccess: (created) => {
          onCreated(created);
          // the secret now lives only in the panel that shows it; do not keep a second copy in the mutation result
          create.reset();
          setName('');
          setExpires('');
          setOrgs('');
        },
      },
    );
  };

  return (
    <Card>
      <CardHeader title="Create a token" />
      <form onSubmit={submit} noValidate className="space-y-4 px-5 py-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field id="token-name" label="Name" help="What it is used for, for example “Nightly export”.">
            <input id="token-name" value={name} onChange={(e) => setName(e.target.value)} className={inputClass} maxLength={100} />
          </Field>
          <Field id="token-role" label="Role" help="What the token may do. It cannot be stronger than your own role.">
            <select id="token-role" value={role} onChange={(e) => setRole(e.target.value)} className={selectClass}>
              {TOKEN_ROLES.map((r) => (
                <option key={r} value={r}>
                  {humanizeCode(r)}
                </option>
              ))}
            </select>
          </Field>
          <Field id="token-expires" label="Expires (optional)" help="The token works through the end of this day.">
            <input id="token-expires" type="date" value={expires} onChange={(e) => setExpires(e.target.value)} className={inputClass} />
          </Field>
          <Field
            id="token-orgs"
            label="Limited to organizations"
            help={known.length > 0 ? `Separate with commas. Empty means every organization you can reach. Known: ${known.join(', ')}.` : 'Separate with commas. Empty means every organization you can reach.'}
          >
            <input id="token-orgs" value={orgs} onChange={(e) => setOrgs(e.target.value)} className={inputClass} />
          </Field>
        </div>
        <FormError error={create.error} />
        <Button type="submit" variant="primary" disabled={create.isPending || !name.trim()}>
          {create.isPending ? 'Creating' : 'Create the token'}
        </Button>
      </form>
    </Card>
  );
}

function TokenItem({ token }: { token: ApiToken }) {
  const revoke = useRevokeApiToken();
  const [confirm, setConfirm] = useState(false);
  return (
    <li className="px-5 py-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-medium">
            {token.name} <span className="ml-1 font-mono text-xs font-normal text-ink-soft">{token.prefix}…</span>
          </p>
          <p className="mt-1 flex flex-wrap items-center gap-1.5">
            <Badge tone={statusTone[token.status] ?? 'neutral'}>{humanizeCode(token.status)}</Badge>
            <Badge tone="accent">{humanizeCode(token.role)}</Badge>
            <span className="text-xs text-ink-soft">{token.organizationIds.length > 0 ? `Organizations: ${token.organizationIds.join(', ')}` : 'Every organization'}</span>
          </p>
          <p className="mt-1 text-xs text-ink-soft">
            Created by {token.createdBy} on {when(token.createdAt)} · Expires {token.expiresAt ? when(token.expiresAt) : 'never'} · Last used {when(token.lastUsedAt)}
            {token.revokedAt ? ` · Revoked ${when(token.revokedAt)}` : ''}
          </p>
        </div>
        {token.status === 'ACTIVE' &&
          (confirm ? (
            <span className="flex items-center gap-2 text-sm">
              Revoke this token?
              <Button variant="primary" disabled={revoke.isPending} onClick={() => revoke.mutate(token.id)}>
                Yes
              </Button>
              <Button onClick={() => setConfirm(false)}>No</Button>
            </span>
          ) : (
            <Button variant="ghost" onClick={() => setConfirm(true)}>
              Revoke
            </Button>
          ))}
      </div>
      <FormError error={revoke.error} />
    </li>
  );
}
