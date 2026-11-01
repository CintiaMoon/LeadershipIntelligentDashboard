import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { configureApi } from '@/services/api/client';
import { useSession } from '@/stores/session';
import { ORIGIN } from '@/tests/msw';
import { renderRoutes } from '@/tests/utils';
import type { PersonView, TeamView } from '@/types/api';
import { PeoplePage } from './PeoplePage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
beforeEach(() => {
  configureApi({ getToken: () => 'tok', onUnauthorized: () => undefined });
  useSession.setState({ organizationId: 'org-1' });
});

let teams: TeamView[] = [];
let people: PersonView[] = [];
const alpha: TeamView = { id: 't-a', organizationId: 'org-1', name: 'Alpha', activePeople: 1 };
const beta: TeamView = { id: 't-b', organizationId: 'org-1', name: 'Beta', activePeople: 0 };
const ada: PersonView = { id: 'p-1', teamId: 't-a', organizationId: 'org-1', name: 'Ada Lovelace', email: 'ada@acme.test', active: true };
const elsewhere: PersonView = { id: 'p-9', teamId: 't-z', organizationId: 'org-2', name: 'Someone Else', email: 'else@acme.test', active: true };

const serve = () => {
  server.use(
    http.get(`${ORIGIN}/teams`, () => HttpResponse.json(teams)),
    http.get(`${ORIGIN}/people`, () => HttpResponse.json(people)),
  );
};
const render = () => renderRoutes([{ path: '/people', element: <PeoplePage /> }], { route: '/people' });

describe('PeoplePage', () => {
  beforeEach(() => {
    teams = [alpha, beta];
    people = [ada, elsewhere];
  });

  it('asks for an organization first when none is chosen', () => {
    useSession.setState({ organizationId: null });
    render();
    expect(screen.getByText('Choose an organization')).toBeInTheDocument();
  });

  it('shows each team with its people, only of the chosen organization, and says the read is recorded', async () => {
    serve();
    render();
    const alphaCard = await screen.findByRole('region', { name: 'Team Alpha' });
    expect(within(alphaCard).getByText('Ada Lovelace')).toBeInTheDocument();
    expect(within(alphaCard).getByText('ada@acme.test')).toBeInTheDocument();
    expect(within(alphaCard).getByText('Active')).toBeInTheDocument();
    expect(within(alphaCard).getByText('1 active person')).toBeInTheDocument();
    expect(within(screen.getByRole('region', { name: 'Team Beta' })).getByText('Nobody is on this team yet.')).toBeInTheDocument();
    expect(screen.queryByText('Someone Else')).not.toBeInTheDocument();
    expect(screen.getByText(/Reading this list is recorded/)).toBeInTheDocument();
  });

  it('invites the first team when there is none', async () => {
    teams = [];
    serve();
    render();
    expect(await screen.findByText('No teams yet')).toBeInTheDocument();
  });

  it('adds a team and shows it', async () => {
    serve();
    let body: unknown;
    server.use(
      http.post(`${ORIGIN}/teams`, async ({ request }) => {
        body = await request.json();
        teams = [...teams, { id: 't-g', organizationId: 'org-1', name: 'Gamma', activePeople: 0 }];
        return HttpResponse.json(teams[2], { status: 201 });
      }),
    );
    render();
    await screen.findByRole('region', { name: 'Team Alpha' });
    await userEvent.type(screen.getByLabelText('Team name'), 'Gamma');
    await userEvent.click(screen.getByRole('button', { name: 'Add team' }));
    expect(await screen.findByRole('region', { name: 'Team Gamma' })).toBeInTheDocument();
    expect(body).toEqual({ organizationId: 'org-1', name: 'Gamma' });
  });

  it('refuses an empty team name without sending anything', async () => {
    serve();
    render();
    await screen.findByRole('region', { name: 'Team Alpha' });
    await userEvent.click(screen.getByRole('button', { name: 'Add team' }));
    expect(screen.getByText('Enter a team name.')).toBeInTheDocument();
  });

  it('says so when a team name is already taken', async () => {
    serve();
    server.use(http.post(`${ORIGIN}/teams`, () => HttpResponse.json({ code: 'ROSTER_CONFLICT', message: 'the organization already has a team with this name' }, { status: 409 })));
    render();
    await screen.findByRole('region', { name: 'Team Alpha' });
    await userEvent.type(screen.getByLabelText('Team name'), 'alpha');
    await userEvent.click(screen.getByRole('button', { name: 'Add team' }));
    expect(await screen.findByText(/already has a team with this name/)).toBeInTheDocument();
  });

  it('renames a team', async () => {
    serve();
    let body: unknown;
    server.use(
      http.put(`${ORIGIN}/teams/t-a`, async ({ request }) => {
        body = await request.json();
        teams = [{ ...alpha, name: 'Alpha prime' }, beta];
        return HttpResponse.json(teams[0]);
      }),
    );
    render();
    await screen.findByRole('region', { name: 'Team Alpha' });
    await userEvent.click(screen.getByRole('button', { name: 'Rename Alpha' }));
    const input = screen.getByLabelText('Team name', { selector: '#rename-t-a' });
    await userEvent.clear(input);
    await userEvent.type(input, 'Alpha prime');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByRole('region', { name: 'Team Alpha prime' })).toBeInTheDocument();
    expect(body).toEqual({ name: 'Alpha prime' });
  });

  it('adds a person to a team with a lower-case e-mail check before sending', async () => {
    serve();
    let body: unknown;
    server.use(
      http.post(`${ORIGIN}/people`, async ({ request }) => {
        body = await request.json();
        people = [...people, { id: 'p-2', teamId: 't-b', organizationId: 'org-1', name: 'Grace Hopper', email: 'grace@acme.test', active: true }];
        return HttpResponse.json(people[2], { status: 201 });
      }),
    );
    render();
    const card = await screen.findByRole('region', { name: 'Team Beta' });
    await userEvent.click(within(card).getByRole('button', { name: 'Add person' }));
    await userEvent.type(within(card).getByLabelText('Name'), 'Grace Hopper');
    await userEvent.type(within(card).getByLabelText('E-mail address'), 'not-an-address');
    await userEvent.click(within(card).getAllByRole('button', { name: 'Add person' }).at(-1) as HTMLElement);
    expect(within(card).getByText('Enter a valid e-mail address.')).toBeInTheDocument();
    expect(body).toBeUndefined();
    await userEvent.clear(within(card).getByLabelText('E-mail address'));
    await userEvent.type(within(card).getByLabelText('E-mail address'), 'grace@acme.test');
    await userEvent.click(within(card).getAllByRole('button', { name: 'Add person' }).at(-1) as HTMLElement);
    await waitFor(() => expect(body).toEqual({ teamId: 't-b', name: 'Grace Hopper', email: 'grace@acme.test' }));
    expect(await within(screen.getByRole('region', { name: 'Team Beta' })).findByText('Grace Hopper')).toBeInTheDocument();
  });

  it('edits a person: moves them to another team and deactivates them', async () => {
    serve();
    let body: unknown;
    server.use(
      http.put(`${ORIGIN}/people/p-1`, async ({ request }) => {
        body = await request.json();
        people = [{ ...ada, teamId: 't-b', active: false }, elsewhere];
        return HttpResponse.json(people[0]);
      }),
    );
    render();
    await screen.findByRole('region', { name: 'Team Alpha' });
    await userEvent.click(screen.getByRole('button', { name: 'Edit Ada Lovelace' }));
    await userEvent.selectOptions(screen.getByLabelText('Team'), 't-b');
    await userEvent.click(screen.getByRole('switch', { name: 'Active' }));
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(body).toEqual({ teamId: 't-b', name: 'Ada Lovelace', email: 'ada@acme.test', active: false }));
    const beta2 = await screen.findByRole('region', { name: 'Team Beta' });
    expect(await within(beta2).findByText('Inactive')).toBeInTheDocument();
  });

  it('says so when an e-mail address is already on the roster', async () => {
    serve();
    server.use(http.post(`${ORIGIN}/people`, () => HttpResponse.json({ code: 'ROSTER_CONFLICT', message: 'a person with this e-mail address is already on the roster' }, { status: 409 })));
    render();
    const card = await screen.findByRole('region', { name: 'Team Beta' });
    await userEvent.click(within(card).getByRole('button', { name: 'Add person' }));
    await userEvent.type(within(card).getByLabelText('Name'), 'Ada Again');
    await userEvent.type(within(card).getByLabelText('E-mail address'), 'ada@acme.test');
    await userEvent.click(within(card).getAllByRole('button', { name: 'Add person' }).at(-1) as HTMLElement);
    expect(await screen.findByText(/already on the roster/)).toBeInTheDocument();
  });
});
