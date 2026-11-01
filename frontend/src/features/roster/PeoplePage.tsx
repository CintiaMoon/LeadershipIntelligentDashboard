import { useState } from 'react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { FormError, Switch, inputClass, selectClass } from '@/components/ui/Form';
import { PageHeader } from '@/components/ui/PageHeader';
import { QuerySection } from '@/components/ui/QuerySection';
import { EmptyState } from '@/components/ui/StateViews';
import { useCreatePerson, useCreateTeam, usePeople, useRenameTeam, useTeams, useUpdatePerson } from '@/services/queries/hooks';
import { useSession } from '@/stores/session';
import type { PersonView, TeamView } from '@/types/api';

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

function AddTeam({ organizationId }: { organizationId: string }) {
  const create = useCreateTeam();
  const [name, setName] = useState('');
  const [problem, setProblem] = useState('');
  return (
    <Card>
      <CardHeader title="Add a team" description="A team belongs to the organization chosen at the top. Answers are only ever combined per team." />
      <form
        className="flex flex-wrap items-end gap-3 p-5"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          if (!name.trim()) {
            setProblem('Enter a team name.');
            return;
          }
          setProblem('');
          create.mutate({ organizationId, name: name.trim() }, { onSuccess: () => setName('') });
        }}
      >
        <div className="min-w-56 flex-1">
          <label htmlFor="team-name" className="text-sm font-medium">
            Team name
          </label>
          <input id="team-name" type="text" className={inputClass} value={name} onChange={(e) => setName(e.target.value)} aria-invalid={!!problem} aria-describedby="team-name-error" />
          <p id="team-name-error" role="alert" className="mt-1 text-xs text-risk-critical">
            {problem}
          </p>
        </div>
        <Button type="submit" variant="primary" disabled={create.isPending}>
          {create.isPending ? 'Adding' : 'Add team'}
        </Button>
      </form>
      {create.error && (
        <div className="px-5 pb-5">
          <FormError error={create.error} />
        </div>
      )}
    </Card>
  );
}

function PersonForm({
  initial,
  teams,
  submitLabel,
  pending,
  error,
  idPrefix,
  allowTeamChange,
  onSubmit,
  onCancel,
}: {
  initial: { name: string; email: string; teamId: string; active: boolean };
  teams: TeamView[];
  submitLabel: string;
  pending: boolean;
  error: unknown;
  idPrefix: string;
  allowTeamChange: boolean;
  onSubmit: (values: { name: string; email: string; teamId: string; active: boolean }) => void;
  onCancel?: () => void;
}) {
  const [values, setValues] = useState(initial);
  const [problems, setProblems] = useState<{ name?: string; email?: string }>({});
  const set = (patch: Partial<typeof values>) => setValues({ ...values, ...patch });
  return (
    <form
      noValidate
      className="space-y-3"
      onSubmit={(e) => {
        e.preventDefault();
        const next: { name?: string; email?: string } = {};
        if (!values.name.trim()) next.name = 'Enter a name.';
        if (!EMAIL.test(values.email.trim())) next.email = 'Enter a valid e-mail address.';
        setProblems(next);
        if (Object.keys(next).length === 0) onSubmit({ ...values, name: values.name.trim(), email: values.email.trim() });
      }}
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <div>
          <label htmlFor={`${idPrefix}-name`} className="text-sm font-medium">
            Name
          </label>
          <input id={`${idPrefix}-name`} type="text" className={inputClass} value={values.name} onChange={(e) => set({ name: e.target.value })} aria-invalid={!!problems.name} aria-describedby={`${idPrefix}-name-error`} />
          <p id={`${idPrefix}-name-error`} role="alert" className="mt-1 text-xs text-risk-critical">
            {problems.name}
          </p>
        </div>
        <div>
          <label htmlFor={`${idPrefix}-email`} className="text-sm font-medium">
            E-mail address
          </label>
          <input id={`${idPrefix}-email`} type="email" autoComplete="off" className={inputClass} value={values.email} onChange={(e) => set({ email: e.target.value })} aria-invalid={!!problems.email} aria-describedby={`${idPrefix}-email-error`} />
          <p id={`${idPrefix}-email-error`} role="alert" className="mt-1 text-xs text-risk-critical">
            {problems.email}
          </p>
        </div>
      </div>
      {allowTeamChange && (
        <div className="flex flex-wrap items-center gap-4">
          <div>
            <label htmlFor={`${idPrefix}-team`} className="text-sm font-medium">
              Team
            </label>
            <select id={`${idPrefix}-team`} className={`${selectClass} !w-auto`} value={values.teamId} onChange={(e) => set({ teamId: e.target.value })}>
              {teams.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </div>
          <Switch label="Active" checked={values.active} onChange={(active) => set({ active })} />
        </div>
      )}
      <FormError error={error} />
      <div className="flex items-center gap-2">
        <Button type="submit" variant="primary" disabled={pending}>
          {pending ? 'Saving' : submitLabel}
        </Button>
        {onCancel && (
          <Button onClick={onCancel} disabled={pending}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  );
}

function PersonRow({ person, teams }: { person: PersonView; teams: TeamView[] }) {
  const update = useUpdatePerson();
  const [editing, setEditing] = useState(false);
  return (
    <tr className="border-b border-line/60 align-top">
      <th scope="row" className="px-5 py-2 text-left font-medium">
        {person.name}
      </th>
      <td className="px-5 py-2 text-ink-soft">{person.email}</td>
      <td className="px-5 py-2">
        <Badge tone={person.active ? 'ok' : 'neutral'}>{person.active ? 'Active' : 'Inactive'}</Badge>
      </td>
      <td className="px-5 py-2">
        {editing ? (
          <div className="min-w-80 py-1">
            <PersonForm
              idPrefix={`edit-${person.id}`}
              initial={{ name: person.name ?? '', email: person.email ?? '', teamId: person.teamId ?? '', active: person.active !== false }}
              teams={teams}
              submitLabel="Save"
              pending={update.isPending}
              error={update.error}
              allowTeamChange
              onCancel={() => setEditing(false)}
              onSubmit={(v) =>
                update.mutate({ personId: person.id as string, request: { teamId: v.teamId, name: v.name, email: v.email, active: v.active } }, { onSuccess: () => setEditing(false) })
              }
            />
          </div>
        ) : (
          <Button variant="ghost" aria-label={`Edit ${person.name}`} onClick={() => setEditing(true)}>
            Edit
          </Button>
        )}
      </td>
    </tr>
  );
}

function TeamCard({ team, teams, people }: { team: TeamView; teams: TeamView[]; people: PersonView[] }) {
  const rename = useRenameTeam();
  const create = useCreatePerson();
  const [renaming, setRenaming] = useState(false);
  const [name, setName] = useState(team.name ?? '');
  const [adding, setAdding] = useState(false);
  return (
    <Card aria-label={`Team ${team.name}`}>
      <div className="flex flex-wrap items-start justify-between gap-3 border-b border-line px-5 py-4">
        <div>
          {renaming ? (
            <form
              className="flex items-center gap-2"
              onSubmit={(e) => {
                e.preventDefault();
                if (name.trim()) rename.mutate({ teamId: team.id as string, name: name.trim() }, { onSuccess: () => setRenaming(false) });
              }}
            >
              <label className="sr-only" htmlFor={`rename-${team.id}`}>
                Team name
              </label>
              <input id={`rename-${team.id}`} type="text" className={`${inputClass} !mt-0`} value={name} onChange={(e) => setName(e.target.value)} />
              <Button type="submit" variant="primary" disabled={rename.isPending}>
                Save
              </Button>
              <Button onClick={() => setRenaming(false)}>Cancel</Button>
            </form>
          ) : (
            <h2 className="text-base font-semibold">{team.name}</h2>
          )}
          <p className="mt-0.5 text-sm text-ink-soft">
            {team.activePeople} active {team.activePeople === 1 ? 'person' : 'people'}
          </p>
          {rename.error && <FormError error={rename.error} />}
        </div>
        <div className="flex gap-2">
          {!renaming && (
            <Button variant="ghost" aria-label={`Rename ${team.name}`} onClick={() => setRenaming(true)}>
              Rename
            </Button>
          )}
          <Button onClick={() => setAdding(!adding)} aria-expanded={adding}>
            Add person
          </Button>
        </div>
      </div>
      {adding && (
        <div className="border-b border-line p-5">
          <PersonForm
            idPrefix={`add-${team.id}`}
            initial={{ name: '', email: '', teamId: team.id as string, active: true }}
            teams={teams}
            submitLabel="Add person"
            pending={create.isPending}
            error={create.error}
            allowTeamChange={false}
            onCancel={() => setAdding(false)}
            onSubmit={(v) => create.mutate({ teamId: team.id as string, name: v.name, email: v.email }, { onSuccess: () => setAdding(false) })}
          />
        </div>
      )}
      {people.length === 0 ? (
        <p className="px-5 py-6 text-sm text-ink-soft">Nobody is on this team yet.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-max text-sm">
            <caption className="sr-only">People of {team.name}</caption>
            <thead>
              <tr className="border-b border-line text-left text-xs uppercase tracking-wide text-ink-faint">
                <th scope="col" className="px-5 py-2 font-medium">Name</th>
                <th scope="col" className="px-5 py-2 font-medium">E-mail address</th>
                <th scope="col" className="px-5 py-2 font-medium">Status</th>
                <th scope="col" className="px-5 py-2 font-medium"><span className="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {people.map((p) => (
                <PersonRow key={p.id} person={p} teams={teams} />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}

/**
 * Teams and the roster (decision D48). A respondent is matched to their roster entry by e-mail address, and a run asks every active
 * person of the teams it is started for. The names and addresses are personal data: only owners and administrators see them, and
 * reading the list is recorded.
 */
export function PeoplePage() {
  const organizationId = useSession((s) => s.organizationId);
  const teams = useTeams(organizationId ?? undefined, !!organizationId);
  const people = usePeople(undefined, !!organizationId);

  return (
    <div className="space-y-6">
      <PageHeader
        title="People"
        subtitle={organizationId ? `Teams and the people asked to answer, in ${organizationId}. People sign in with the e-mail address listed here.` : 'Teams and the people asked to answer.'}
      />
      {!organizationId ? (
        <EmptyState title="Choose an organization" description="Teams belong to an organization. Choose one at the top of the page." />
      ) : (
        <>
          <p role="note" className="rounded-lg border border-line bg-surface px-4 py-3 text-sm text-ink-soft">
            Reading this list is recorded. People are deactivated, never deleted, so past runs stay explainable.
          </p>
          <AddTeam organizationId={organizationId} />
          <QuerySection query={teams} label="the teams">
            {() => (
              <QuerySection query={people} label="the people">
                {() => {
                  const teamList = teams.data ?? [];
                  if (teamList.length === 0) {
                    return <EmptyState title="No teams yet" description="Add the first team above, then add the people on it." />;
                  }
                  const everyone = (people.data ?? []).filter((p) => p.organizationId === organizationId);
                  return (
                    <div className="space-y-6">
                      {teamList.map((t) => (
                        <TeamCard key={t.id} team={t} teams={teamList} people={everyone.filter((p) => p.teamId === t.id)} />
                      ))}
                    </div>
                  );
                }}
              </QuerySection>
            )}
          </QuerySection>
        </>
      )}
    </div>
  );
}
