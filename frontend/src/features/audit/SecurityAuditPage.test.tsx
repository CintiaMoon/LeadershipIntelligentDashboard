import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { acme, ana, API, configureSession, signedIn } from '@/tests/session';
import { renderRoutes } from '@/tests/utils';
import type { SecurityEvent } from '@/types/identity';
import { SecurityAuditPage } from './SecurityAuditPage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
beforeEach(() => configureSession('acme'));

const event = (id: number, over: Partial<SecurityEvent> = {}): SecurityEvent => ({
  id,
  occurredAt: '2026-11-04T10:00:00Z',
  eventType: 'MEMBERSHIP_INVITED',
  outcome: 'SUCCESS',
  userEmail: 'ana@example.com',
  tenantId: 'acme',
  clientIp: '10.1.1.1',
  detail: { roles: 'VIEWER' },
  ...over,
});

function open(platform: boolean, seen: URL[] = [], total = 2) {
  const path = platform ? '/platform/security-audit' : '/security-audit';
  server.use(
    http.get(`${API}${path}`, ({ request }) => {
      const url = new URL(request.url);
      seen.push(url);
      const page = Number(url.searchParams.get('page') ?? 0);
      const items = page === 0 ? [event(2), event(1, { eventType: 'API_TOKEN_REJECTED', outcome: 'FAILURE', userEmail: null, detail: { reason: 'REVOKED' } })] : [event(0)];
      return HttpResponse.json({ items, page, size: 25, total });
    }),
  );
  const auth = platform ? signedIn({ account: { ...ana, platformAdmin: true }, company: null }) : signedIn({ company: acme() });
  return renderRoutes([{ path, element: <SecurityAuditPage platform={platform} /> }], { route: path, auth });
}

describe('SecurityAuditPage', () => {
  it('lists the events with who, from where and the details', async () => {
    open(false);
    const rows = await screen.findAllByRole('row');
    const first = within(rows[1] as HTMLElement);
    expect(first.getByText('Membership invited')).toBeInTheDocument();
    expect(first.getByText('Success')).toBeInTheDocument();
    expect(first.getByText('ana@example.com')).toBeInTheDocument();
    expect(first.getByText('10.1.1.1')).toBeInTheDocument();
    expect(first.getByText('roles: VIEWER')).toBeInTheDocument();
    const second = within(rows[2] as HTMLElement);
    expect(second.getByText('Failure')).toBeInTheDocument();
    expect(second.getByText('not known')).toBeInTheDocument();
    expect(second.getByText('reason: REVOKED')).toBeInTheDocument();
    expect(screen.getByText('2 events')).toBeInTheDocument();
  });

  it('asks the company endpoint, and the platform endpoint for the platform', async () => {
    const company: URL[] = [];
    open(false, company);
    await screen.findByText('2 events');
    expect(company[0]?.pathname).toBe('/api/v1/security-audit');
    expect(screen.queryByLabelText('Company id')).not.toBeInTheDocument();
  });

  it('lets the platform administrator narrow it to a company', async () => {
    const seen: URL[] = [];
    open(true, seen);
    await screen.findByText('2 events');
    expect(seen[0]?.pathname).toBe('/api/v1/platform/security-audit');
    await userEvent.type(screen.getByLabelText('Company id'), 'globex');
    await userEvent.click(screen.getByRole('button', { name: 'Show' }));
    await screen.findByText('2 events');
    expect(seen.at(-1)?.searchParams.get('tenantId')).toBe('globex');
    // and the company is named in each row
    expect(screen.getAllByText('acme').length).toBeGreaterThan(0);
  });

  it('sends the filters, with the period from the start of the first day to the end of the last', async () => {
    const seen: URL[] = [];
    open(false, seen);
    await screen.findByText('2 events');
    await userEvent.type(screen.getByLabelText('Event'), 'API_TOKEN_REJECTED');
    await userEvent.selectOptions(screen.getByLabelText('Outcome'), 'FAILURE');
    await userEvent.type(screen.getByLabelText('From'), '2026-11-01');
    await userEvent.type(screen.getByLabelText('Until (including)'), '2026-11-04');
    await userEvent.click(screen.getByRole('button', { name: 'Show' }));
    await screen.findByText('2 events');
    const last = seen.at(-1)?.searchParams;
    expect(last?.get('eventType')).toBe('API_TOKEN_REJECTED');
    expect(last?.get('outcome')).toBe('FAILURE');
    expect(last?.get('from')).toBe('2026-11-01T00:00:00Z');
    expect(last?.get('to')).toBe('2026-11-05T00:00:00.000Z');
    expect(last?.get('page')).toBe('0');
    expect(last?.get('size')).toBe('25');
  });

  it('pages through the events', async () => {
    const seen: URL[] = [];
    open(false, seen, 30);
    await screen.findByText('30 events');
    expect(screen.getByText('1 to 25 of 30')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Newer' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Older' }));
    expect(await screen.findByText('26 to 30 of 30')).toBeInTheDocument();
    expect(seen.at(-1)?.searchParams.get('page')).toBe('1');
    expect(screen.getByRole('button', { name: 'Older' })).toBeDisabled();
  });

  it('says so when nothing matches', async () => {
    server.use(http.get(`${API}/security-audit`, () => HttpResponse.json({ items: [], page: 0, size: 25, total: 0 })));
    renderRoutes([{ path: '/security-audit', element: <SecurityAuditPage /> }], { route: '/security-audit', auth: signedIn({ company: acme() }) });
    expect(await screen.findByText('No events')).toBeInTheDocument();
  });

  it('shows a refusal as an error, not an empty list', async () => {
    server.use(http.get(`${API}/security-audit`, () => HttpResponse.json({ code: 'ACCESS_DENIED', message: 'x' }, { status: 403 })));
    renderRoutes([{ path: '/security-audit', element: <SecurityAuditPage /> }], { route: '/security-audit', auth: signedIn({ company: acme() }) });
    expect(await screen.findByText('You do not have access to this')).toBeInTheDocument();
  });
});
