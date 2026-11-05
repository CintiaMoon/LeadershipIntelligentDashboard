import { useState, type FormEvent } from 'react';
import { useAuth } from '@/app/providers/auth-context';
import { Badge, type Tone } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Field, FormError, inputClass } from '@/components/ui/Form';
import { PageHeader } from '@/components/ui/PageHeader';
import { QuerySection } from '@/components/ui/QuerySection';
import { EmptyState } from '@/components/ui/StateViews';
import { useInviteMember, useMembers, useRemoveMember, useResendInvitation, useUpdateMember } from '@/services/queries/identity';
import { MEMBER_ROLES, type Member } from '@/types/identity';
import { humanizeCode } from '@/utils/presentation';
import { mayManageRoles, mayGrantRole, parseList, toggleRole } from './rules';

const statusTone: Record<string, Tone> = { ACTIVE: 'ok', INVITED: 'accent', SUSPENDED: 'medium' };

/** Who may do what (decision D72). The server decides; the interface only avoids offering what would be refused. */
export function MembersPage() {
  const { principal, account, company } = useAuth();
  const members = useMembers();
  const actorRoles = principal?.roles ?? [];
  const known = company?.organizationIds ?? [];

  return (
    <div className="space-y-6">
      <PageHeader title="Members" subtitle="The people who can open this company, and the invitations that are waiting. Changes apply the next time they use the application." />
      <InviteForm actorRoles={actorRoles} organizations={known} />
      <QuerySection query={members} label="the members">
        {() => {
          const list = members.data ?? [];
          if (list.length === 0) return <EmptyState title="No members yet" description="Invite the first person above." />;
          return (
            <Card>
              <CardHeader title={`${list.length} ${list.length === 1 ? 'member' : 'members'}`} />
              <ul className="divide-y divide-line">
                {list.map((member) => (
                  <MemberItem key={member.id} member={member} actorRoles={actorRoles} self={member.email.toLowerCase() === account?.email.toLowerCase()} organizations={known} />
                ))}
              </ul>
            </Card>
          );
        }}
      </QuerySection>
    </div>
  );
}

function RoleChoices({ idPrefix, chosen, onChange, actorRoles }: { idPrefix: string; chosen: string[]; onChange: (roles: string[]) => void; actorRoles: string[] }) {
  return (
    <fieldset className="mt-1 flex flex-wrap gap-x-5 gap-y-2">
      <legend className="sr-only">Roles</legend>
      {MEMBER_ROLES.map((role) => {
        const allowed = mayGrantRole(actorRoles, role);
        return (
          <label key={role} className={`inline-flex items-center gap-2 text-sm ${allowed ? '' : 'opacity-50'}`} title={allowed ? undefined : 'Your role does not allow you to give this role'}>
            <input
              id={`${idPrefix}-${role}`}
              type="checkbox"
              checked={chosen.includes(role)}
              disabled={!allowed}
              onChange={() => onChange(toggleRole(chosen, role))}
            />
            {humanizeCode(role)}
          </label>
        );
      })}
    </fieldset>
  );
}

function InviteForm({ actorRoles, organizations }: { actorRoles: string[]; organizations: string[] }) {
  const invite = useInviteMember();
  const [email, setEmail] = useState('');
  const [roles, setRoles] = useState<string[]>(['VIEWER']);
  const [orgs, setOrgs] = useState('');
  const [sentTo, setSentTo] = useState<string | null>(null);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setSentTo(null);
    invite.mutate(
      { email: email.trim(), roles, organizationIds: parseList(orgs) },
      {
        onSuccess: (member) => {
          setSentTo(member.email);
          setEmail('');
          setOrgs('');
        },
      },
    );
  };

  return (
    <Card>
      <CardHeader title="Invite someone" description="They receive an email with a link that works once, for seven days." />
      <form onSubmit={submit} noValidate className="space-y-4 px-5 py-4">
        <Field id="invite-email" label="Email address">
          <input id="invite-email" type="email" autoComplete="off" value={email} onChange={(e) => setEmail(e.target.value)} className={inputClass} maxLength={254} />
        </Field>
        <div>
          <span className="text-sm font-medium" id="invite-roles-label">Roles</span>
          <RoleChoices idPrefix="invite-role" chosen={roles} onChange={setRoles} actorRoles={actorRoles} />
          <p className="mt-1 text-xs text-ink-soft">A respondent only answers the questionnaires they are asked to; it cannot be combined with another role.</p>
        </div>
        <Field
          id="invite-orgs"
          label="Limited to organizations"
          help={organizations.length > 0 ? `Separate with commas. Leave empty for every organization. Known: ${organizations.join(', ')}.` : 'Separate with commas. Leave empty for every organization.'}
        >
          <input id="invite-orgs" value={orgs} onChange={(e) => setOrgs(e.target.value)} className={inputClass} />
        </Field>
        <FormError error={invite.error} />
        {sentTo && (
          <p role="status" className="rounded-lg border border-ok/30 bg-ok-soft px-4 py-3 text-sm text-ok">
            Invitation sent to {sentTo}.
          </p>
        )}
        <Button type="submit" variant="primary" disabled={invite.isPending || !email.trim() || roles.length === 0}>
          {invite.isPending ? 'Sending' : 'Send the invitation'}
        </Button>
      </form>
    </Card>
  );
}

function MemberItem({ member, actorRoles, self, organizations }: { member: Member; actorRoles: string[]; self: boolean; organizations: string[] }) {
  const [editing, setEditing] = useState(false);
  const [confirmRemove, setConfirmRemove] = useState(false);
  const update = useUpdateMember();
  const remove = useRemoveMember();
  const resend = useResendInvitation();
  const manageable = !self && mayManageRoles(actorRoles, member.roles);
  const failure = update.error ?? remove.error ?? resend.error;

  return (
    <li className="px-5 py-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="truncate text-sm font-medium">
            {member.displayName ? `${member.displayName} ` : ''}
            <span className={member.displayName ? 'font-normal text-ink-soft' : ''}>{member.displayName ? `(${member.email})` : member.email}</span>
            {self && <span className="ml-2 text-xs font-normal text-ink-soft">you</span>}
          </p>
          <p className="mt-1 flex flex-wrap items-center gap-1.5">
            <Badge tone={statusTone[member.status] ?? 'neutral'}>{humanizeCode(member.status)}</Badge>
            {member.roles.map((role) => (
              <Badge key={role} tone="accent">
                {humanizeCode(role)}
              </Badge>
            ))}
            <span className="text-xs text-ink-soft">{member.organizationIds.length > 0 ? `Organizations: ${member.organizationIds.join(', ')}` : 'Every organization'}</span>
          </p>
        </div>
        {manageable ? (
          <div className="flex flex-wrap items-center gap-2">
            {member.status === 'INVITED' && (
              <Button disabled={resend.isPending} onClick={() => resend.mutate(member.id)}>
                {resend.isSuccess ? 'Sent again' : 'Send again'}
              </Button>
            )}
            {member.status === 'ACTIVE' && (
              <Button disabled={update.isPending} onClick={() => update.mutate({ id: member.id, request: { status: 'SUSPENDED' } })}>
                Suspend
              </Button>
            )}
            {member.status === 'SUSPENDED' && (
              <Button disabled={update.isPending} onClick={() => update.mutate({ id: member.id, request: { status: 'ACTIVE' } })}>
                Reactivate
              </Button>
            )}
            <Button onClick={() => setEditing((e) => !e)} aria-expanded={editing}>
              {editing ? 'Close' : 'Edit'}
            </Button>
            {confirmRemove ? (
              <span className="flex items-center gap-2 text-sm">
                {member.status === 'INVITED' ? 'Withdraw the invitation?' : 'Remove this member?'}
                <Button variant="primary" disabled={remove.isPending} onClick={() => remove.mutate(member.id)}>
                  Yes
                </Button>
                <Button onClick={() => setConfirmRemove(false)}>No</Button>
              </span>
            ) : (
              <Button variant="ghost" onClick={() => setConfirmRemove(true)}>
                {member.status === 'INVITED' ? 'Withdraw' : 'Remove'}
              </Button>
            )}
          </div>
        ) : (
          <p className="text-xs text-ink-soft">{self ? 'You cannot change your own membership.' : 'Your role does not allow you to manage this member.'}</p>
        )}
      </div>
      <FormError error={failure} />
      {editing && manageable && <EditMember member={member} actorRoles={actorRoles} organizations={organizations} onDone={() => setEditing(false)} />}
    </li>
  );
}

function EditMember({ member, actorRoles, organizations, onDone }: { member: Member; actorRoles: string[]; organizations: string[]; onDone: () => void }) {
  const update = useUpdateMember();
  const [roles, setRoles] = useState<string[]>(member.roles);
  const [orgs, setOrgs] = useState(member.organizationIds.join(', '));

  const save = (event: FormEvent) => {
    event.preventDefault();
    update.mutate({ id: member.id, request: { roles, organizationIds: parseList(orgs) } }, { onSuccess: onDone });
  };

  return (
    <form onSubmit={save} noValidate className="mt-4 space-y-4 rounded-lg border border-line bg-canvas p-4">
      <div>
        <span className="text-sm font-medium">Roles</span>
        <RoleChoices idPrefix={`edit-${member.id}`} chosen={roles} onChange={setRoles} actorRoles={actorRoles} />
      </div>
      <Field
        id={`edit-orgs-${member.id}`}
        label="Limited to organizations"
        help={organizations.length > 0 ? `Separate with commas. Empty means every organization. Known: ${organizations.join(', ')}.` : 'Separate with commas. Empty means every organization.'}
      >
        <input id={`edit-orgs-${member.id}`} value={orgs} onChange={(e) => setOrgs(e.target.value)} className={inputClass} />
      </Field>
      <FormError error={update.error} />
      <div className="flex gap-2">
        <Button type="submit" variant="primary" disabled={update.isPending || roles.length === 0}>
          {update.isPending ? 'Saving' : 'Save'}
        </Button>
        <Button onClick={onDone}>Cancel</Button>
      </div>
    </form>
  );
}
