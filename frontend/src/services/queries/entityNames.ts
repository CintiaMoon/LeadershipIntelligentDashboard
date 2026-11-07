import { useQuery } from '@tanstack/react-query';
import { api } from '@/services/api/endpoints';
import { queryKeys } from './keys';

export interface EntityNames {
  /** The name of a team, or its id when the roster cannot be read or does not know it. */
  team: (id: string | undefined) => string;
  /** The name of the questionnaire an assessment was made with (its id is the template id), or the id for any other template. */
  template: (id: string | undefined) => string;
}

/**
 * Team and questionnaire ids are generated values (t-4dfb3eb05aea, q-9f3a...), so screens show the name that people gave them.
 * The two lists are the ones the roster and library screens already load and share their cache. A role that cannot read a list,
 * or a list that has not arrived yet, leaves the id in place: a name is a convenience and never blocks a screen.
 */
export function useEntityNames(organizationId: string | null | undefined): EntityNames {
  const teams = useQuery({
    queryKey: queryKeys.teams(organizationId ?? undefined),
    queryFn: ({ signal }) => api.teams(organizationId ?? undefined, signal),
    enabled: !!organizationId,
    retry: false,
  });
  const questionnaires = useQuery({ queryKey: queryKeys.questionnaires, queryFn: ({ signal }) => api.questionnaires(signal), retry: false });
  const teamNames = new Map((teams.data ?? []).map((t) => [t.id as string, t.name as string]));
  const templateNames = new Map((questionnaires.data ?? []).map((q) => [q.id as string, q.name as string]));
  return {
    team: (id) => (id ? teamNames.get(id) || id : ''),
    template: (id) => (id ? templateNames.get(id) || id : ''),
  };
}
