import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { acme, ana, API, configureSession, csrfHandler, signedIn } from '@/tests/session';
import { renderRoutes } from '@/tests/utils';
import type { Member } from '@/types/identity';
import { MembersPage } from './MembersPage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
beforeEach(() => {
  configureSession('acme');
  server.use(csrfHandler);
});

const member = (over: Partial<Member>): Member => ({
  id: 'm-x',
  email: 'x@example.com',
  displayName: 'X',
  status: 'ACTIVE',
  roles: ['VIEWER'],
  organizationIds: [],
  createdAt: '2026-11-01T09:00:00Z',
  updatedAt: '2026-11-01T09:00:00Z',
  ...over,
});

const people: Member[] = [
  member({ id: 'm-me', email: 'ana@example.com', displayName: 'Ana Silva', roles: ['OWNER'] }),
  member({ id: 'm-admin', email: 'admin@example.com', displayName: 'Admin', roles: ['ADMIN'] }),
  member({ id: 'm-viewer', email: 'viewer@example.com', displayName: 'Vera', roles: ['VIEWER'], organizationIds: ['org-1'] }),
  member({ id: 'm-invited', email: 'new@example.com', displayName: null, status: 'INVITED', roles: ['ANALYST'] }),
  member({ id: 'm-susp', email: 'susp@example.com', displayName: 'Sam', status: 'SUSPENDED', roles: ['ANALYST'] }),
];

function open(roles: string[] = ['OWNER'], list: Member[] = people) {
  server.use(http.get(`${API}/members`, () => HttpResponse.json(list)));
  return renderRoutes([{ path: '/members', element: <MembersPage /> }], { route: '/members', auth: signedIn({ company: acme(roles, ['org-1', 'org-2']) }) });
}

const row = (email: string) => screen.getByText(email, { exact: false }).closest('li') as HTMLElement;

describe('MembersPage', () => {
  it('lists the members and the pending invitations with their roles, status and organizations', async () => {
    open();
    await screen.findByText(/viewer@example.com/);
    const viewer = row('viewer@example.com');
    await within(viewer).findByText('Active');
    expect(within(viewer).getByText('Viewer')).toBeInTheDocument();
    expect(within(viewer).getByText('Organizations: org-1')).toBeInTheDocument();
    const invited = row('new@example.com');
    expect(within(invited).getByText('Invited')).toBeInTheDocument();
    expect(within(invited).getByText('Every organization')).toBeInTheDocument();
    expect(within(row('susp@example.com')).getByText('Suspended')).toBeInTheDocument();
    expect(screen.getByText('5 members')).toBeInTheDocument();
  });

  it('offers a member no action on themselves', async () => {
    open();
    const me = await screen.findByText(/ana@example.com/).then((e) => e.closest('li') as HTMLElement);
    expect(within(me).getByText('you')).toBeInTheDocument();
    expect(within(me).getByText('You cannot change your own membership.')).toBeInTheDocument();
    expect(within(me).queryByRole('button')).not.toBeInTheDocument();
  });

  it('keeps an administrator from owners and other administrators, and from giving those roles', async () => {
    open(['ADMIN']);
    await screen.findByText(/viewer@example.com/);
    // the owner is the signed-in person here, so the first message is the one about themselves; the administrator is out of reach
    expect(within(row('ana@example.com')).getByText('You cannot change your own membership.')).toBeInTheDocument();
    expect(within(row('admin@example.com')).getByText('Your role does not allow you to manage this member.')).toBeInTheDocument();
    expect(within(row('admin@example.com')).queryByRole('button')).not.toBeInTheDocument();
    expect(within(row('viewer@example.com')).getByRole('button', { name: 'Edit' })).toBeInTheDocument();
    expect(screen.getByLabelText('Owner')).toBeDisabled();
    expect(screen.getByLabelText('Admin')).toBeDisabled();
    expect(screen.getByLabelText('Analyst')).toBeEnabled();
  });

  it('invites with the roles chosen and the organizations typed', async () => {
    let body: unknown;
    server.use(http.post(`${API}/members`, async ({ request }) => ((body = await request.json()), HttpResponse.json(member({ email: 'pat@example.com', status: 'INVITED' }), { status: 201 }))));
    open();
    await screen.findByText(/viewer@example.com/);
    await userEvent.type(screen.getByLabelText('Email address'), ' pat@example.com ');
    await userEvent.click(screen.getByLabelText('Analyst'));
    await userEvent.type(screen.getByLabelText('Limited to organizations'), 'org-1, org-2');
    await userEvent.click(screen.getByRole('button', { name: 'Send the invitation' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Invitation sent to pat@example.com.');
    expect(body).toEqual({ email: 'pat@example.com', roles: ['VIEWER', 'ANALYST'], organizationIds: ['org-1', 'org-2'] });
    expect(screen.getByLabelText('Email address')).toHaveValue('');
  });

  it('keeps a respondent on its own', async () => {
    open();
    await screen.findByText(/viewer@example.com/);
    await userEvent.click(screen.getByLabelText('Respondent'));
    expect(screen.getByLabelText('Respondent')).toBeChecked();
    expect(screen.getByLabelText('Viewer')).not.toBeChecked();
    await userEvent.click(screen.getByLabelText('Analyst'));
    expect(screen.getByLabelText('Respondent')).not.toBeChecked();
  });

  it('needs an address and a role before it sends', async () => {
    open();
    await screen.findByText(/viewer@example.com/);
    const send = screen.getByRole('button', { name: 'Send the invitation' });
    expect(send).toBeDisabled();
    await userEvent.type(screen.getByLabelText('Email address'), 'a@example.com');
    expect(send).toBeEnabled();
    await userEvent.click(screen.getByLabelText('Viewer'));
    expect(send).toBeDisabled();
  });

  it('shows why an invitation was refused', async () => {
    server.use(http.post(`${API}/members`, () => HttpResponse.json({ code: 'MEMBER_CONFLICT', message: 'This person is already a member of the company.' }, { status: 409 })));
    open();
    await screen.findByText(/viewer@example.com/);
    await userEvent.type(screen.getByLabelText('Email address'), 'viewer@example.com');
    await userEvent.click(screen.getByRole('button', { name: 'Send the invitation' }));
    expect(await screen.findByText(/already a member of the company/)).toBeInTheDocument();
  });

  it('suspends and reactivates', async () => {
    const bodies: unknown[] = [];
    server.use(http.patch(`${API}/members/:id`, async ({ request, params }) => (bodies.push([params.id, await request.json()]), HttpResponse.json(member({})))));
    open();
    await userEvent.click(await within(await screen.findByText(/viewer@example.com/).then((e) => e.closest('li') as HTMLElement)).findByRole('button', { name: 'Suspend' }));
    await userEvent.click(within(row('susp@example.com')).getByRole('button', { name: 'Reactivate' }));
    await waitFor(() => expect(bodies).toHaveLength(2));
    expect(bodies).toEqual([['m-viewer', { status: 'SUSPENDED' }], ['m-susp', { status: 'ACTIVE' }]]);
  });

  it('changes roles and organizations from the edit form', async () => {
    let seen: unknown;
    server.use(http.patch(`${API}/members/m-viewer`, async ({ request }) => ((seen = await request.json()), HttpResponse.json(member({})))));
    open();
    const viewer = await screen.findByText(/viewer@example.com/).then((e) => e.closest('li') as HTMLElement);
    await userEvent.click(within(viewer).getByRole('button', { name: 'Edit' }));
    await userEvent.click(within(viewer).getByLabelText('Analyst'));
    const orgs = within(viewer).getByLabelText('Limited to organizations');
    await userEvent.clear(orgs);
    await userEvent.click(within(viewer).getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(seen).toEqual({ roles: ['VIEWER', 'ANALYST'], organizationIds: [] }));
  });

  it('asks before removing, and removes', async () => {
    let removed: string | undefined;
    server.use(http.delete(`${API}/members/:id`, ({ params }) => ((removed = String(params.id)), new HttpResponse(null, { status: 204 }))));
    open();
    const viewer = await screen.findByText(/viewer@example.com/).then((e) => e.closest('li') as HTMLElement);
    await userEvent.click(within(viewer).getByRole('button', { name: 'Remove' }));
    expect(removed).toBeUndefined();
    expect(within(viewer).getByText('Remove this member?')).toBeInTheDocument();
    await userEvent.click(within(viewer).getByRole('button', { name: 'Yes' }));
    await waitFor(() => expect(removed).toBe('m-viewer'));
  });

  it('withdraws an invitation and sends it again', async () => {
    const calls: string[] = [];
    server.use(
      http.delete(`${API}/members/:id`, ({ params }) => (calls.push(`delete ${String(params.id)}`), new HttpResponse(null, { status: 204 }))),
      http.post(`${API}/members/:id/resend-invitation`, ({ params }) => (calls.push(`resend ${String(params.id)}`), new HttpResponse(null, { status: 204 }))),
    );
    open();
    const invited = await screen.findByText(/new@example.com/).then((e) => e.closest('li') as HTMLElement);
    await userEvent.click(within(invited).getByRole('button', { name: 'Send again' }));
    await userEvent.click(within(invited).getByRole('button', { name: 'Withdraw' }));
    expect(within(invited).getByText('Withdraw the invitation?')).toBeInTheDocument();
    await userEvent.click(within(invited).getByRole('button', { name: 'Yes' }));
    await waitFor(() => expect(calls).toEqual(['resend m-invited', 'delete m-invited']));
  });

  it('shows a refusal next to the member it concerns', async () => {
    server.use(http.patch(`${API}/members/m-viewer`, () => HttpResponse.json({ code: 'MEMBER_CONFLICT', message: 'A company needs at least one active owner.' }, { status: 409 })));
    open();
    const viewer = await screen.findByText(/viewer@example.com/).then((e) => e.closest('li') as HTMLElement);
    await userEvent.click(within(viewer).getByRole('button', { name: 'Suspend' }));
    expect(await within(viewer).findByText(/at least one active owner/)).toBeInTheDocument();
  });

  it('says so when there is nobody yet', async () => {
    open(['OWNER'], []);
    expect(await screen.findByText('No members yet')).toBeInTheDocument();
  });

  it('does not treat a member of the same name as the signed-in person unless the address is the same', async () => {
    open(['OWNER'], [member({ id: 'm-1', email: 'ANA@example.com', displayName: 'Ana', roles: ['OWNER'] }), member({ id: 'm-2', email: 'ana2@example.com', roles: ['VIEWER'] })]);
    await screen.findByText(/ANA@example.com/);
    expect(screen.getAllByText('you')).toHaveLength(1);
    expect(ana.email).toBe('ana@example.com');
  });
});
