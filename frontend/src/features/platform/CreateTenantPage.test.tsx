import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { configureApi } from '@/services/api/client';
import { ORIGIN } from '@/tests/msw';
import { renderRoutes } from '@/tests/utils';
import { CreateTenantPage } from './CreateTenantPage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());
beforeEach(() => configureApi({ getToken: () => 'tok', onUnauthorized: () => undefined }));

const platform = { subject: 'root', tenantId: '', organizations: [], roles: [], platformAdmin: true };
const routes = [
  { path: '/platform/tenants/new', element: <CreateTenantPage /> },
  { path: '/platform/tenants', element: <p>tenant list page</p> },
];
const render = () => renderRoutes(routes, { route: '/platform/tenants/new', auth: { principal: platform } });
const fill = async (id: string, name: string) => {
  if (id) await userEvent.type(screen.getByLabelText('Tenant id'), id);
  if (name) await userEvent.type(screen.getByLabelText('Name'), name);
};
const submit = () => userEvent.click(screen.getByRole('button', { name: 'Create tenant' }));

describe('CreateTenantPage', () => {
  it('says the id is permanent before anything is submitted', () => {
    render();
    expect(screen.getByText(/cannot be changed later/)).toBeInTheDocument();
  });

  it('sends the id and the name, then returns to the list and names the new tenant', async () => {
    let body: unknown;
    server.use(
      http.post(`${ORIGIN}/platform/tenants`, async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({ id: 'acme-corp', name: 'Acme Corp', createdAt: '2026-11-01T09:00:00Z' }, { status: 201 });
      }),
    );
    const view = render();
    await fill('acme-corp', '  Acme Corp  ');
    await submit();
    await screen.findByText('tenant list page');
    expect(body).toEqual({ id: 'acme-corp', name: 'Acme Corp' });
    expect(view.router.state.location.state).toEqual({ created: 'acme-corp' });
  });

  it('explains what is wrong without sending anything', async () => {
    let requests = 0;
    server.use(
      http.post(`${ORIGIN}/platform/tenants`, () => {
        requests += 1;
        return HttpResponse.json({}, { status: 201 });
      }),
    );
    render();
    await submit();
    expect(await screen.findByText(/Use 2 to 63 lowercase letters, digits or hyphens/)).toBeInTheDocument();
    expect(screen.getByText('Enter a name.')).toBeInTheDocument();
    expect(screen.getByLabelText('Tenant id')).toHaveAttribute('aria-invalid', 'true');
    expect(requests).toBe(0);
  });

  it.each([['Acme'], ['acme corp'], ['-acme'], ['a'], ['acme_corp'], ['a'.repeat(64)]])('refuses the id %s', async (id) => {
    render();
    await fill(id, 'Name');
    await submit();
    expect(await screen.findByText(/Use 2 to 63 lowercase letters/)).toBeInTheDocument();
  });

  it('refuses a name longer than 200 characters', async () => {
    render();
    await fill('acme', 'x'.repeat(201));
    await submit();
    expect(await screen.findByText('Use at most 200 characters.')).toBeInTheDocument();
  });

  it('shows a taken id on the id field and stays on the form', async () => {
    server.use(http.post(`${ORIGIN}/platform/tenants`, () => HttpResponse.json({ code: 'TENANT_ALREADY_EXISTS', message: 'exists' }, { status: 409 })));
    render();
    await fill('acme', 'Acme');
    await submit();
    expect(await screen.findByText(/A tenant with this id already exists/)).toBeInTheDocument();
    expect(screen.getByLabelText('Tenant id')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.queryByText('tenant list page')).not.toBeInTheDocument();
  });

  it('shows any other failure with its reference and keeps what was typed', async () => {
    server.use(http.post(`${ORIGIN}/platform/tenants`, () => HttpResponse.json({ code: 'X', message: 'boom', correlationId: 'abc-123' }, { status: 500 })));
    render();
    await fill('acme', 'Acme');
    await submit();
    expect(await screen.findByText(/Something went wrong on our side/)).toBeInTheDocument();
    expect(screen.getByText(/abc-123/)).toBeInTheDocument();
    expect(screen.getByLabelText('Tenant id')).toHaveValue('acme');
    expect(screen.getByLabelText('Name')).toHaveValue('Acme');
  });

  it('says access is missing when the backend refuses', async () => {
    server.use(http.post(`${ORIGIN}/platform/tenants`, () => HttpResponse.json({ code: 'FORBIDDEN', message: 'no' }, { status: 403 })));
    render();
    await fill('acme', 'Acme');
    await submit();
    expect(await screen.findByText(/You do not have access to this/)).toBeInTheDocument();
  });

  it('cannot be submitted twice while the request is running', async () => {
    let requests = 0;
    server.use(
      http.post(`${ORIGIN}/platform/tenants`, async () => {
        requests += 1;
        await new Promise((r) => setTimeout(r, 150));
        return HttpResponse.json({ id: 'acme', name: 'Acme', createdAt: '2026-11-01T09:00:00Z' }, { status: 201 });
      }),
    );
    render();
    await fill('acme', 'Acme');
    await submit();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Creating' })).toBeDisabled());
    await userEvent.click(screen.getByRole('button', { name: 'Creating' }));
    await screen.findByText('tenant list page');
    expect(requests).toBe(1);
  });

  it('cancels back to the list', async () => {
    const view = render();
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(view.router.state.location.pathname).toBe('/platform/tenants');
  });
});
