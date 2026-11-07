import { describe, expect, it } from 'vitest';
import { trailFor } from './trail';

describe('trailFor', () => {
  it('has a single page for a top-level screen', () => {
    expect(trailFor('/reports')).toEqual([{ label: 'Reports' }]);
    expect(trailFor('/platform/security-audit')).toEqual([{ label: 'Security audit' }]);
  });

  it('leads from the explanation of a run back to the dashboard', () => {
    expect(trailFor('/runs/r-1/explanation')).toEqual([{ label: 'Dashboard', to: '/dashboard' }, { label: 'How the score was calculated' }]);
    expect(trailFor('/runs/r-1/dimensions/TEAM_HEALTH')).toEqual([
      { label: 'Dashboard', to: '/dashboard' },
      { label: 'How the score was calculated', to: '/runs/r-1/explanation' },
      { label: 'Dimension: Team health' },
    ]);
  });

  it('links the list above a detail or form screen', () => {
    expect(trailFor('/questionnaires/new')).toEqual([{ label: 'Questionnaires', to: '/questionnaires' }, { label: 'New questionnaire' }]);
    expect(trailFor('/assessment-runs/abc')).toEqual([{ label: 'Assessment runs', to: '/assessment-runs' }, { label: 'Run' }]);
    expect(trailFor('/platform/tenants/new')).toEqual([{ label: 'Tenants', to: '/platform/tenants' }, { label: 'New tenant' }]);
    expect(trailFor('/configuration/layers/organization/org-1')).toEqual([{ label: 'Configuration', to: '/configuration' }, { label: 'Organization org-1' }]);
  });

  it('gives an unknown address no trail', () => {
    expect(trailFor('/')).toEqual([]);
    expect(trailFor('/nowhere')).toEqual([]);
  });
});
