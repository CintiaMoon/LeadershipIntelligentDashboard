import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { anonymous, API, ana, acme, configureSession, csrfHandler, signedIn } from '@/tests/session';
import { renderRoutes } from '@/tests/utils';
import { AcceptInvitationPage } from './AcceptInvitationPage';
import { AccountPage } from './AccountPage';
import { CompanyPickerPage } from './CompanyPickerPage';
import { ForgotPasswordPage, ResetPasswordPage } from './PasswordResetPages';
import { RegisterPage } from './RegisterPage';
import { SessionLoginPage } from './SessionLoginPage';
import { VerifyEmailPage } from './VerifyEmailPage';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
afterEach(() => {
  server.resetHandlers();
  vi.unstubAllGlobals();
});
afterAll(() => server.close());
beforeEach(() => {
  configureSession(null);
  server.use(csrfHandler);
});

const PASSWORD = 'correct horse battery';

/** The message of the form: the shared field component keeps an empty alert region beside every input, so ask for the one with text. */
async function alert(): Promise<HTMLElement> {
  return waitFor(() => {
    const found = screen.getAllByRole('alert').find((e) => e.textContent);
    if (!found) throw new Error('no message shown yet');
    return found;
  });
}

async function type(label: string | RegExp, text: string) {
  await userEvent.type(screen.getByLabelText(label), text);
}

describe('SessionLoginPage', () => {
  const login = (auth = anonymous, route = '/login') => renderRoutes([{ path: '/login', element: <SessionLoginPage /> }, { path: '/', element: <p>home</p> }], { route, auth });

  it('signs in with the email and the password and reads the account again', async () => {
    let body: unknown;
    server.use(http.post(`${API}/auth/login`, async ({ request }) => ((body = await request.json()), HttpResponse.json(ana))));
    const refresh = vi.fn(async () => undefined);
    login({ ...anonymous, refresh });
    await type('Email address', ' ana@example.com ');
    await type('Password', PASSWORD);
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(body).toEqual({ email: 'ana@example.com', password: PASSWORD });
  });

  it('says only that the email or the password is not correct, and clears the password', async () => {
    server.use(http.post(`${API}/auth/login`, () => HttpResponse.json({ code: 'AUTHENTICATION_FAILED', message: 'x' }, { status: 401 })));
    login();
    await type('Email address', 'nobody@example.com');
    await type('Password', PASSWORD);
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await alert()).toHaveTextContent('The email or the password is not correct.');
    expect(screen.getByLabelText('Password')).toHaveValue('');
  });

  it('tells a throttled person how long to wait', async () => {
    server.use(http.post(`${API}/auth/login`, () => HttpResponse.json({ code: 'TOO_MANY_REQUESTS', message: 'x' }, { status: 429, headers: { 'Retry-After': '300' } })));
    login();
    await type('Email address', 'ana@example.com');
    await type('Password', PASSWORD);
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await alert()).toHaveTextContent('Try again in 5 minutes');
  });

  it('does not send an empty form', () => {
    login();
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeDisabled();
  });

  it('shows the password only when asked', async () => {
    login();
    const field = screen.getByLabelText('Password');
    expect(field).toHaveAttribute('type', 'password');
    await userEvent.click(screen.getByRole('button', { name: 'Show' }));
    expect(field).toHaveAttribute('type', 'text');
  });

  it('goes on to the page it was asked for once signed in, even outside the company router', () => {
    const assign = vi.fn();
    vi.stubGlobal('location', { ...window.location, assign, pathname: '/login' });
    login(signedIn() as typeof anonymous, '/login?from=%2Fc%2Facme%2Fdashboard');
    expect(assign).toHaveBeenCalledWith('/c/acme/dashboard');
  });

  it('ignores a return address on another site', async () => {
    login(signedIn() as typeof anonymous, '/login?from=https%3A%2F%2Fevil.example%2F');
    expect(await screen.findByText('home')).toBeInTheDocument();
    login(signedIn() as typeof anonymous, '/login?from=%2F%2Fevil.example');
    expect(await screen.findAllByText('home')).not.toHaveLength(0);
  });

  it('links to creating an account, resetting the password and asking for the confirmation again', () => {
    login();
    expect(screen.getByRole('link', { name: 'Create an account' })).toHaveAttribute('href', '/register');
    expect(screen.getByRole('link', { name: 'Forgot your password?' })).toHaveAttribute('href', '/forgot-password');
    expect(screen.getByRole('link', { name: /confirmation email/ })).toHaveAttribute('href', '/verify-email');
  });
});

describe('RegisterPage', () => {
  const register = () => renderRoutes([{ path: '/register', element: <RegisterPage /> }], { route: '/register', auth: anonymous });
  const fill = async (password = PASSWORD, confirm = PASSWORD) => {
    await type('Your name', 'Ana Silva');
    await type('Email address', 'ana@example.com');
    await type('Password', password);
    await type('Repeat the password', confirm);
  };

  it('asks for nothing it will not use and waits for a complete form', async () => {
    register();
    const button = screen.getByRole('button', { name: 'Create account' });
    expect(button).toBeDisabled();
    await fill('short', 'short');
    expect(button).toBeDisabled();
  });

  it('points out two passwords that differ', async () => {
    register();
    await fill(PASSWORD, `${PASSWORD}x`);
    expect(screen.getByText('The two passwords are not the same.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create account' })).toBeDisabled();
  });

  it('sends the details and says the same whether or not the address was known', async () => {
    let body: unknown;
    server.use(http.post(`${API}/auth/register`, async ({ request }) => ((body = await request.json()), new HttpResponse(null, { status: 202 }))));
    register();
    await fill();
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Check your email')).toBeInTheDocument();
    expect(body).toEqual({ email: 'ana@example.com', password: PASSWORD, displayName: 'Ana Silva' });
    expect(screen.getByRole('status')).toHaveTextContent(/If ana@example.com can be used for a new account/);
    expect(screen.getByRole('link', { name: 'Back to sign in' })).toHaveAttribute('href', '/login');
  });

  it('shows what the server says about a weak password', async () => {
    server.use(http.post(`${API}/auth/register`, () => HttpResponse.json({ code: 'WEAK_PASSWORD', message: 'That password is too common.' }, { status: 422 })));
    register();
    await fill();
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await alert()).toHaveTextContent('That password is too common.');
  });
});

describe('VerifyEmailPage', () => {
  const verify = (route: string) => renderRoutes([{ path: '/verify-email', element: <VerifyEmailPage /> }], { route, auth: anonymous });

  it('asks for the password together with the link, so a stranger who registered the address cannot get it confirmed', async () => {
    let body: unknown;
    server.use(http.post(`${API}/auth/verify-email`, async ({ request }) => ((body = await request.json()), new HttpResponse(null, { status: 204 }))));
    verify('/verify-email?token=abc123');
    await type('Password', PASSWORD);
    await userEvent.click(screen.getByRole('button', { name: 'Confirm' }));
    expect(await screen.findByText('Email address confirmed')).toBeInTheDocument();
    expect(body).toEqual({ token: 'abc123', password: PASSWORD });
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login');
  });

  it('says a link or a password that does not work in one way', async () => {
    server.use(http.post(`${API}/auth/verify-email`, () => HttpResponse.json({ code: 'INVALID_TOKEN', message: 'x' }, { status: 400 })));
    verify('/verify-email?token=abc123');
    await type('Password', 'not the password');
    await userEvent.click(screen.getByRole('button', { name: 'Confirm' }));
    expect(await alert()).toHaveTextContent('not valid, has already been used, or has expired');
    expect(screen.getByLabelText('Password')).toHaveValue('');
  });

  it('sends the link again without saying whether the address has an account', async () => {
    let body: unknown;
    server.use(http.post(`${API}/auth/resend-verification`, async ({ request }) => ((body = await request.json()), new HttpResponse(null, { status: 202 }))));
    verify('/verify-email');
    await type('Email address', 'ana@example.com');
    await userEvent.click(screen.getByRole('button', { name: 'Send the link' }));
    expect(await screen.findByText(/If an account for that address is waiting/)).toBeInTheDocument();
    expect(body).toEqual({ email: 'ana@example.com' });
  });
});

describe('password reset', () => {
  it('asks for a link and says the same for every address', async () => {
    server.use(http.post(`${API}/auth/password/forgot`, () => new HttpResponse(null, { status: 202 })));
    renderRoutes([{ path: '/forgot-password', element: <ForgotPasswordPage /> }], { route: '/forgot-password', auth: anonymous });
    await type('Email address', 'whoever@example.com');
    await userEvent.click(screen.getByRole('button', { name: 'Send the link' }));
    expect(await screen.findByText(/If an account exists for that address/)).toBeInTheDocument();
  });

  it('chooses a new password with the link and says every sign-in ended', async () => {
    let body: unknown;
    server.use(http.post(`${API}/auth/password/reset`, async ({ request }) => ((body = await request.json()), new HttpResponse(null, { status: 204 }))));
    renderRoutes([{ path: '/reset-password', element: <ResetPasswordPage /> }], { route: '/reset-password?token=t0k', auth: anonymous });
    await type('New password', PASSWORD);
    await type('Repeat the new password', PASSWORD);
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }));
    expect(await screen.findByText('Password changed')).toBeInTheDocument();
    expect(body).toEqual({ token: 't0k', newPassword: PASSWORD });
    expect(screen.getByText(/signed out everywhere/)).toBeInTheDocument();
  });

  it('explains a link that has no token', () => {
    renderRoutes([{ path: '/reset-password', element: <ResetPasswordPage /> }], { route: '/reset-password', auth: anonymous });
    expect(screen.getByText('This link is incomplete')).toBeInTheDocument();
  });

  it('explains an expired link', async () => {
    server.use(http.post(`${API}/auth/password/reset`, () => HttpResponse.json({ code: 'INVALID_TOKEN', message: 'x' }, { status: 400 })));
    renderRoutes([{ path: '/reset-password', element: <ResetPasswordPage /> }], { route: '/reset-password?token=old', auth: anonymous });
    await type('New password', PASSWORD);
    await type('Repeat the new password', PASSWORD);
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }));
    expect(await alert()).toHaveTextContent('has expired');
  });
});

describe('AcceptInvitationPage', () => {
  const accept = (auth: typeof anonymous, route = '/invitations/accept?token=inv-1') =>
    renderRoutes([{ path: '/invitations/accept', element: <AcceptInvitationPage /> }], { route, auth });
  const offer = (accountExists: boolean, email = 'ana@example.com') =>
    server.use(http.post(`${API}/auth/invitations/preview`, () => HttpResponse.json({ companyName: 'Acme Corp', email, roles: ['ANALYST'], accountExists })));

  it('is incomplete without a token', () => {
    accept(anonymous, '/invitations/accept');
    expect(screen.getByText('This link is incomplete')).toBeInTheDocument();
  });

  it('refuses a link that is used or expired', async () => {
    server.use(http.post(`${API}/auth/invitations/preview`, () => HttpResponse.json({ code: 'INVALID_TOKEN', message: 'x' }, { status: 400 })));
    accept(anonymous);
    expect(await screen.findByText('This invitation cannot be used')).toBeInTheDocument();
  });

  it('shows the company, the address and the roles offered', async () => {
    offer(true);
    accept(anonymous);
    expect(await screen.findByRole('heading', { name: 'Join Acme Corp' })).toBeInTheDocument();
    expect(screen.getByText('ana@example.com')).toBeInTheDocument();
    expect(screen.getByLabelText('Roles offered')).toHaveTextContent('Analyst');
  });

  it('sends someone with an account to sign in and brings them back', async () => {
    offer(true);
    accept(anonymous);
    const link = await screen.findByRole('link', { name: 'Sign in to accept' });
    const href = link.getAttribute('href') ?? '';
    expect(href).toContain('/login?from=');
    expect(decodeURIComponent(href)).toContain('/invitations/accept?token=inv-1');
    expect(decodeURIComponent(href)).toContain('email=ana@example.com');
  });

  it('accepts for the person who is signed in with the invited address', async () => {
    offer(true);
    let accepted: unknown;
    server.use(http.post(`${API}/auth/invitations/accept`, async ({ request }) => ((accepted = await request.json()), new HttpResponse(null, { status: 204 }))));
    const refresh = vi.fn(async () => undefined);
    accept(signedIn({ company: null, refresh }) as typeof anonymous);
    await userEvent.click(await screen.findByRole('button', { name: 'Accept the invitation' }));
    expect(await screen.findByText('You joined the company.')).toBeInTheDocument();
    expect(accepted).toEqual({ token: 'inv-1' });
    expect(refresh).toHaveBeenCalled();
    expect(screen.getByRole('link', { name: 'Open my companies' })).toHaveAttribute('href', '/companies');
  });

  it('warns a person who is signed in with a different address', async () => {
    offer(true, 'someone.else@example.com');
    accept(signedIn({ company: null }) as typeof anonymous);
    expect(await screen.findByText(/signed in as/)).toHaveTextContent('ana@example.com');
    expect(screen.queryByRole('button', { name: 'Accept the invitation' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Sign out, then sign in with someone.else@example.com/ })).toBeInTheDocument();
  });

  it('creates the account for an address that has none, with no separate confirmation', async () => {
    offer(false, 'new@example.com');
    let body: unknown;
    server.use(http.post(`${API}/auth/invitations/register`, async ({ request }) => ((body = await request.json()), new HttpResponse(null, { status: 204 }))));
    accept(anonymous);
    await screen.findByLabelText('Your name');
    await type('Your name', 'New Person');
    await type('Password', PASSWORD);
    await type('Repeat the password', PASSWORD);
    await userEvent.click(screen.getByRole('button', { name: 'Create account and join' }));
    expect(await screen.findByText(/Your account is ready and you are a member/)).toBeInTheDocument();
    expect(body).toEqual({ token: 'inv-1', password: PASSWORD, displayName: 'New Person' });
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login?email=new%40example.com');
  });

  it('shows why creating the account failed', async () => {
    offer(false, 'new@example.com');
    server.use(http.post(`${API}/auth/invitations/register`, () => HttpResponse.json({ code: 'WEAK_PASSWORD', message: 'Too common.' }, { status: 422 })));
    accept(anonymous);
    await screen.findByLabelText('Your name');
    await type('Your name', 'New Person');
    await type('Password', PASSWORD);
    await type('Repeat the password', PASSWORD);
    await userEvent.click(screen.getByRole('button', { name: 'Create account and join' }));
    expect(await alert()).toHaveTextContent('Too common.');
  });
});

describe('CompanyPickerPage', () => {
  const picker = (auth: Partial<ReturnType<typeof signedIn>>) =>
    renderRoutes([{ path: '/companies', element: <CompanyPickerPage /> }, { path: '/platform/tenants', element: <p>tenants</p> }], { route: '/companies', auth });
  const globex = { id: 'globex', name: 'Globex', roles: ['VIEWER'], organizationIds: [], allOrganizations: true };

  it('lists the companies, each opened by a full page load into its own address', () => {
    picker(signedIn({ company: null, companies: [acme(['OWNER', 'ADMIN']), globex] }));
    const acmeLink = screen.getByRole('link', { name: /Acme Corp/ });
    expect(acmeLink).toHaveAttribute('href', '/c/acme/');
    expect(acmeLink).toHaveTextContent('Owner');
    expect(screen.getByRole('link', { name: /Globex/ })).toHaveAttribute('href', '/c/globex/');
  });

  it('goes straight into a person\'s only company', async () => {
    const replace = vi.fn();
    vi.stubGlobal('location', { ...window.location, replace });
    picker(signedIn({ company: null, companies: [acme()] }));
    await waitFor(() => expect(replace).toHaveBeenCalledWith('/c/acme/'));
    expect(screen.getByText('Opening Acme Corp')).toBeInTheDocument();
  });

  it('tells a person with no company that they have none yet and what to do', () => {
    picker(signedIn({ company: null, companies: [] }));
    expect(screen.getByText('You have no company yet')).toBeInTheDocument();
    expect(screen.getByText(/invite/)).toHaveTextContent('ana@example.com');
  });

  it('sends the platform administrator to the tenants', async () => {
    picker(signedIn({ account: { ...ana, platformAdmin: true }, company: null, companies: [] }));
    expect(await screen.findByText('tenants')).toBeInTheDocument();
  });
});

describe('AccountPage', () => {
  const page = () => renderRoutes([{ path: '/account', element: <AccountPage /> }], { route: '/account', auth: signedIn({ company: null }) });

  it('shows who is signed in', () => {
    page();
    expect(screen.getByText('Ana Silva')).toBeInTheDocument();
    expect(screen.getByText('ana@example.com')).toBeInTheDocument();
  });

  it('changes the password with the current one', async () => {
    let body: unknown;
    server.use(http.post(`${API}/auth/password/change`, async ({ request }) => ((body = await request.json()), new HttpResponse(null, { status: 204 }))));
    page();
    await type('Current password', PASSWORD);
    await type('New password', 'another long passphrase');
    await type('Repeat the new password', 'another long passphrase');
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }));
    expect(await screen.findByText(/Your password was changed/)).toBeInTheDocument();
    expect(body).toEqual({ currentPassword: PASSWORD, newPassword: 'another long passphrase' });
    expect(screen.getByLabelText('Current password')).toHaveValue('');
  });

  it('says when the current password is wrong', async () => {
    server.use(http.post(`${API}/auth/password/change`, () => HttpResponse.json({ code: 'INVALID_ACCOUNT_REQUEST', message: 'The current password is not correct.' }, { status: 422 })));
    page();
    await type('Current password', 'wrong password here');
    await type('New password', 'another long passphrase');
    await type('Repeat the new password', 'another long passphrase');
    await userEvent.click(screen.getByRole('button', { name: 'Change password' }));
    expect(await alert()).toHaveTextContent('The current password is not correct.');
  });
});
