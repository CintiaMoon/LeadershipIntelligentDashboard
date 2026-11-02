import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { configureApi } from '@/services/api/client';
import { ORIGIN } from '@/tests/msw';
import { renderRoutes } from '@/tests/utils';
import { ConfigurationLayerPage } from './ConfigurationLayerPage';
import { ConfigurationPage } from './ConfigurationPage';
import { ConfigurationVersionPage } from './ConfigurationVersionPage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
beforeEach(() => configureApi({ getToken: () => 'tok', onUnauthorized: () => undefined }));

const routes = [
  { path: '/configuration', element: <ConfigurationPage /> },
  { path: '/configuration/layers/tenant', element: <ConfigurationLayerPage kind="tenant" /> },
  { path: '/configuration/versions/:versionId', element: <ConfigurationVersionPage /> },
];

const activeSummary = { id: 'v-1', version: 1, status: 'ACTIVE', unvalidated: true, effectiveFrom: '2026-10-01T00:00:00Z' };
const layer = { id: 'layer-1', scope: 'TENANT', name: 'Tenant', active: activeSummary };
const doc = (weight: number) => JSON.stringify({ questions: [{ id: 'Q01', weight }] });
const version = (over: Record<string, unknown> = {}) => ({
  id: 'v-2',
  layerId: 'layer-1',
  scope: 'TENANT',
  version: 2,
  status: 'DRAFT',
  createdBy: 'assess-admin',
  createdAt: '2026-11-02T08:00:00Z',
  unvalidated: true,
  payload: doc(1),
  ...over,
});

describe('ConfigurationPage', () => {
  it('shows each layer with its active version and offers a new draft when none is open', async () => {
    server.use(http.get(`${ORIGIN}/configuration/layers`, () => HttpResponse.json([layer])));
    renderRoutes(routes, { route: '/configuration' });
    const card = await screen.findByLabelText('Tenant override');
    expect(within(card).getByText('Unvalidated')).toBeInTheDocument();
    expect(within(card).getByRole('link', { name: 'Version 1' })).toHaveAttribute('href', '/configuration/versions/v-1');
    expect(within(card).getByRole('button', { name: /New draft/ })).toBeInTheDocument();
  });

  it('gives an analyst nothing to change', async () => {
    server.use(http.get(`${ORIGIN}/configuration/layers`, () => HttpResponse.json([layer])));
    renderRoutes(routes, { route: '/configuration', auth: { principal: { subject: 'a', tenantId: 't', organizations: ['org-1'], roles: ['ANALYST'] } } });
    await screen.findByLabelText('Tenant override');
    expect(screen.queryByRole('button', { name: /New draft/ })).not.toBeInTheDocument();
  });
});

describe('ConfigurationLayerPage', () => {
  it('lists the versions and fixtures of the layer', async () => {
    server.use(
      http.get(`${ORIGIN}/configuration/layers`, () => HttpResponse.json([layer])),
      http.get(`${ORIGIN}/configuration/versions`, () => HttpResponse.json([version({ status: 'VALIDATING' }), { ...version({ id: 'v-1', version: 1, status: 'ACTIVE' }) }])),
      http.get(`${ORIGIN}/configuration/layers/layer-1/fixtures`, () => HttpResponse.json([{ id: 'f-1', name: 'Best answers', answers: [{}], expectations: {}, createdBy: 'x', updatedAt: '2026-11-01T00:00:00Z' }])),
    );
    renderRoutes(routes, { route: '/configuration/layers/tenant' });
    const table = await screen.findByRole('table');
    expect(within(table).getByText('Awaiting approval')).toBeInTheDocument();
    expect(within(table).getByText('Active')).toBeInTheDocument();
    expect(await screen.findByText('Best answers')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'New draft' })).toBeInTheDocument();
  });
});

describe('ConfigurationVersionPage', () => {
  const openJson = async () => userEvent.click(await screen.findByRole('button', { name: 'JSON' }));

  const base = () =>
    server.use(
      http.get(`${ORIGIN}/configuration/layers`, () => HttpResponse.json([layer])),
      http.get(`${ORIGIN}/configuration/versions/v-1`, () => HttpResponse.json(version({ id: 'v-1', version: 1, status: 'ACTIVE', payload: doc(1) }))),
    );

  it('edits a draft: save is only possible after a change, and the changes against the active version appear', async () => {
    base();
    let saved: unknown;
    server.use(
      http.get(`${ORIGIN}/configuration/versions/v-2`, () => HttpResponse.json(version())),
      http.put(`${ORIGIN}/configuration/versions/v-2`, async ({ request }) => {
        saved = await request.json();
        return HttpResponse.json(version({ payload: JSON.stringify(saved) }));
      }),
    );
    renderRoutes(routes, { route: '/configuration/versions/v-2' });
    await openJson();
    const editor = await screen.findByLabelText('Document (JSON)');
    expect(screen.getByRole('button', { name: 'Save draft' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Check' })).toBeEnabled();
    await userEvent.clear(editor);
    await userEvent.click(editor);
    await userEvent.paste(doc(3));
    expect(await screen.findByText(/unsaved changes/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Check' })).toBeDisabled();
    expect(await screen.findByText(/Compared with active version 1/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Save draft' }));
    await waitFor(() => expect(saved).toEqual({ questions: [{ id: 'Q01', weight: 3 }] }));
  });

  it('refuses to save text that is not JSON and says where', async () => {
    base();
    server.use(http.get(`${ORIGIN}/configuration/versions/v-2`, () => HttpResponse.json(version())));
    renderRoutes(routes, { route: '/configuration/versions/v-2' });
    await openJson();
    const editor = await screen.findByLabelText('Document (JSON)');
    await userEvent.clear(editor);
    await userEvent.click(editor);
    await userEvent.paste('{ "questions": ');
    expect(screen.getByRole('button', { name: 'Save draft' })).toBeDisabled();
    expect(screen.getByLabelText('Document (JSON)')).toHaveAttribute('aria-invalid', 'true');
  });

  it('lists the problems the server finds when checking', async () => {
    base();
    server.use(
      http.get(`${ORIGIN}/configuration/versions/v-2`, () => HttpResponse.json(version())),
      http.post(`${ORIGIN}/configuration/versions/v-2/validate`, () => HttpResponse.json({ valid: false, problems: ['questions[0]: unknown dimension', 'weights: do not sum to 1'] })),
    );
    renderRoutes(routes, { route: '/configuration/versions/v-2' });
    await userEvent.click(await screen.findByRole('button', { name: 'Check' }));
    const alert = await screen.findByRole('alert');
    expect(within(alert).getByText('The document has 2 problems:')).toBeInTheDocument();
    expect(within(alert).getByText('weights: do not sum to 1')).toBeInTheDocument();
  });

  it('previews a draft against fixtures and recent assessments', async () => {
    base();
    server.use(
      http.get(`${ORIGIN}/configuration/versions/v-2`, () => HttpResponse.json(version())),
      http.post(`${ORIGIN}/configuration/versions/v-2/preview`, () =>
        HttpResponse.json({
          versionId: 'v-2',
          summary: { fixtures: 2, fixturesPassed: 1, assessmentsUsed: true, assessments: 3, scoresChanged: 2, patternsAppearing: 1, patternsDisappearing: 0, before: { scored: 3, median: 70, bands: {} }, after: { scored: 3, median: 64, bands: {} } },
          items: [],
          fixtures: [
            { fixtureId: 'f-1', name: 'Best answers', passed: true },
            { fixtureId: 'f-2', name: 'Burnout profile', passed: false, failures: ['overall 82 is above 60'] },
          ],
        }),
      ),
    );
    renderRoutes(routes, { route: '/configuration/versions/v-2' });
    await userEvent.click(await screen.findByRole('button', { name: 'Preview' }));
    expect(await screen.findByText(/1 of 2 fixtures pass/)).toBeInTheDocument();
    expect(screen.getByText('overall 82 is above 60')).toBeInTheDocument();
    expect(screen.getByText('Fails')).toBeInTheDocument();
  });

  it('shows the approval step on a version awaiting approval and the fixtures that block it', async () => {
    base();
    server.use(
      http.get(`${ORIGIN}/configuration/versions/v-2`, () => HttpResponse.json(version({ status: 'VALIDATING', review: { submittedBy: 'assess-admin', submittedAt: '2026-11-02T09:00:00Z' } }))),
      http.post(`${ORIGIN}/configuration/versions/v-2/approve`, () =>
        HttpResponse.json({ code: 'CONFIGURATION_FIXTURES_FAILED', message: 'A fixture fails.', details: { 'fixture.Burnout profile': 'overall 82 is above 60' } }, { status: 409 }),
      ),
    );
    renderRoutes(routes, { route: '/configuration/versions/v-2' });
    await screen.findByRole('heading', { name: 'Approve or send back' });
    expect(screen.queryByRole('button', { name: 'Save draft' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Approve and activate' }));
    expect(await screen.findByText('Say what you reviewed.')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Note'), 'Checked the weights');
    await userEvent.click(screen.getByRole('button', { name: 'Approve and activate' }));
    expect(await screen.findByText('overall 82 is above 60')).toBeInTheDocument();
  });

  it('says why the author cannot approve their own version', async () => {
    base();
    server.use(
      http.get(`${ORIGIN}/configuration/versions/v-2`, () => HttpResponse.json(version({ status: 'VALIDATING' }))),
      http.post(`${ORIGIN}/configuration/versions/v-2/approve`, () =>
        HttpResponse.json({ code: 'CONFIGURATION_APPROVAL_REFUSED', message: 'a version must be approved by someone who did not create, edit or submit it' }, { status: 403 }),
      ),
    );
    renderRoutes(routes, { route: '/configuration/versions/v-2' });
    await screen.findByRole('heading', { name: 'Approve or send back' });
    await userEvent.type(screen.getByLabelText('Note'), 'ok');
    await userEvent.click(screen.getByRole('button', { name: 'Approve and activate' }));
    expect(await screen.findByText(/Not allowed: .*did not create, edit or submit it/)).toBeInTheDocument();
  });

  it('shows no approval controls to an analyst', async () => {
    base();
    server.use(http.get(`${ORIGIN}/configuration/versions/v-2`, () => HttpResponse.json(version({ status: 'VALIDATING' }))));
    renderRoutes(routes, { route: '/configuration/versions/v-2', auth: { principal: { subject: 'a', tenantId: 't', organizations: ['org-1'], roles: ['ANALYST'] } } });
    await openJson();
    await screen.findByLabelText('Document (JSON)');
    expect(screen.queryByRole('heading', { name: 'Approve or send back' })).not.toBeInTheDocument();
    expect(screen.getByLabelText('Document (JSON)')).toHaveAttribute('readonly');
  });
});
