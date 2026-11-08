import { humanizeCode } from '@/utils/presentation';

export interface Crumb {
  label: string;
  /** Absent for the page the person is on and for steps that have no page of their own. */
  to?: string;
}

const TOP: Record<string, string> = {
  dashboard: 'Dashboard',
  compare: 'Compare',
  reports: 'Reports',
  questionnaires: 'Questionnaires',
  people: 'People',
  'assessment-runs': 'Assessment runs',
  configuration: 'Configuration',
  respond: 'My assessments',
  members: 'Members',
  'api-tokens': 'API tokens',
  'security-audit': 'Security audit',
};

/**
 * Where a screen sits, from its address (relative to the router prefix, so `/c/<company>` is already gone). Only steps that are
 * screens of their own are links; the last crumb is the page itself. An address nobody knows gets no trail.
 */
export function trailFor(pathname: string): Crumb[] {
  const parts = pathname.split('/').filter(Boolean).map(decodeURIComponent);
  const [first, second, third, fourth] = parts;
  if (!first) return [];

  if (first === 'runs' && second) {
    const explanation = `/runs/${encodeURIComponent(second)}/explanation`;
    const base: Crumb[] = [{ label: 'Dashboard', to: '/dashboard' }];
    if (third === 'explanation') return [...base, { label: 'How the score was calculated' }];
    const kind = third === 'dimensions' ? 'Dimension' : third === 'patterns' ? 'Pattern' : third === 'penalties' ? 'Penalty' : null;
    if (kind && fourth) return [...base, { label: 'How the score was calculated', to: explanation }, { label: `${kind}: ${humanizeCode(fourth)}` }];
    return [];
  }
  if (first === 'platform') {
    if (second === 'tenants') return third === 'new' ? [{ label: 'Tenants', to: '/platform/tenants' }, { label: 'New tenant' }] : [{ label: 'Tenants' }];
    if (second === 'security-audit') return [{ label: 'Security audit' }];
    return [];
  }
  if (first === 'configuration') {
    if (!second) return [{ label: 'Configuration' }];
    const home: Crumb = { label: 'Configuration', to: '/configuration' };
    if (second === 'versions') return [home, { label: 'Version' }];
    if (second === 'layers') return [home, { label: third === 'organization' && fourth ? `Organization ${fourth}` : `${humanizeCode(third ?? 'layer')} layer` }];
    return [];
  }
  if (first === 'questionnaires') {
    if (!second) return [{ label: 'Questionnaires' }];
    return [{ label: 'Questionnaires', to: '/questionnaires' }, { label: second === 'new' ? 'New questionnaire' : 'Questionnaire' }];
  }
  if (first === 'assessment-runs') {
    if (!second) return [{ label: 'Assessment runs' }];
    return [{ label: 'Assessment runs', to: '/assessment-runs' }, { label: second === 'new' ? 'Start a run' : 'Run' }];
  }
  if (first === 'respond') {
    return second ? [{ label: 'My assessments', to: '/respond' }, { label: 'Answer' }] : [{ label: 'My assessments' }];
  }
  const label = TOP[first];
  return label && !second ? [{ label }] : [];
}
