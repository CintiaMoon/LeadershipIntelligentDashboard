import type { ScoringResult } from '@/types/api';

type TeamRun = NonNullable<ScoringResult['teamRuns']>[number];

interface Props {
  teams: TeamRun[];
  /** The selected team id, or null for the whole organization. */
  value: string | null;
  onChange: (teamId: string | null) => void;
  /** The name to show for a team id (the id itself when the name is not known). */
  teamName?: (teamId: string | undefined) => string;
}

/**
 * Switches the dashboard between the organization and one of its teams. A team that could not be scored says so in its
 * label; the engine never ranks teams against each other, and neither does this control.
 */
export function ScopeSwitcher({ teams, value, onChange, teamName = (id) => id ?? '' }: Props) {
  if (teams.length === 0) return null;
  return (
    <label className="flex items-center gap-2 text-sm text-ink-soft">
      <span>Scope</span>
      <select
        className="min-h-9 rounded-md border border-line bg-surface px-2 text-sm text-ink"
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value === '' ? null : e.target.value)}
      >
        <option value="">Whole organization</option>
        {teams.map((t) => (
          <option key={t.teamId} value={t.teamId}>
            Team {teamName(t.teamId)}
            {t.scoreStatus === 'INSUFFICIENT_DATA' ? ' (not enough responses)' : ''}
          </option>
        ))}
      </select>
    </label>
  );
}
