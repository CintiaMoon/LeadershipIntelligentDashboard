# Accounts, companies and sessions in the web application (identity phases P6 and P7)

The screens and rules below are the only way to sign in: the application's own accounts. The external-provider mode (OIDC, the persona picker, bearer tokens in the browser) was removed in phase P7. Decisions D65 to D74 and `docs/implementation/API_CONTRACT.md` sections 18 to 21 are the contract.

## Two routers

- **Outside a company** (`/login`, `/register`, `/verify-email`, `/forgot-password`, `/reset-password`, `/invitations/accept`, `/companies`, `/account`, and the platform administrator's `/platform/*` and `/configuration`): the router has no prefix.
- **Inside a company** (`/c/<company>/dashboard`, `/c/<company>/members`, ...): the router is built with `/c/<company>` as its basename, so every screen keeps its short path and every link written as `/dashboard` stays inside the company. Reaching a company, and leaving one, is a **full page load**: the previous company's cache and state are gone, and the company in the address is the only company the interface names (`X-Company-Id`).

## What the client does

- Sends the session cookie, never a token it can read. State-changing calls carry `X-XSRF-TOKEN` (taken from the `XSRF-TOKEN` cookie, or fetched from `/auth/csrf`); a `403 CSRF_TOKEN_INVALID` fetches a new one and repeats the call once.
- Names the company on every call except the account and company-list calls.
- A `401` on an authenticated call ends the session in the interface (the cache is cleared and the person is sent to sign in, with the address they were on remembered). A `401` from the sign-in, registration and reset calls is their answer, not the end of a session.
- A `429` shows how long to wait (`Retry-After`).
- Account messages are deliberately plain: a wrong email, a wrong password, an unconfirmed and a disabled account read the same, and registering, asking for a reset or a new confirmation link says the same whether or not the address is known.

## Screens

| Address | For | Notes |
|---|---|---|
| `/register`, `/verify-email?token=`, `/login`, `/forgot-password`, `/reset-password?token=` | anyone | Confirming an email asks for the **password** as well as the link. |
| `/invitations/accept?token=` | anyone with the link | Shows the company, the address and the roles. An address with an account signs in and accepts; one without creates the account there (no separate confirmation). |
| `/companies` | signed in | Lists the ACTIVE memberships; one company goes straight in; none says so. The platform administrator goes to `/platform/tenants`. |
| `/account` | signed in | Name, email, change password. |
| `/c/<company>/members` | owner, administrator | Invite, edit roles and organizations, suspend, remove, send again. Roles the person may not give are disabled; the server decides. |
| `/c/<company>/api-tokens` | owner, administrator | Create (the secret is shown once, in a panel that stays until it is closed), list, revoke. |
| `/c/<company>/security-audit`, `/platform/security-audit` | owner, administrator; platform administrator | Filters, paging. |
| People screen | owner, administrator | "Invite to answer" per active person (a respondent-only invitation to the roster address). |
| Platform, create tenant | platform administrator | Optional owner email: the first owner is invited. |

A member who is not limited to a list of organizations sees the organizations that exist and can **name a new one** (the first one of a new company is created by first use).

## Running it

- `npm run dev`: the application's own accounts against `mvn spring-boot:run -Dspring-boot.run.profiles=dev` or the compose stack; emails are in Mailpit (http://localhost:8025).
- **One-click sign-in (testing only):** `/demo-login` (also linked as "Test accounts" on the sign-in page) lists the demo accounts below and signs in with one click. It exists only in a build made with `VITE_DEMO_LOGIN=true`: `npm run dev` (`.env.development`) and the compose web image (`build.args`). The default is `false`, a normal production build contains neither the page nor the published password, and `npm run check:no-mocks` fails if it does.
- Demo accounts (profile `dev` only): `<name>@demo.test` with the password `demo-password-1234`: `owner`, `admin`, `leader`, `analyst`, `viewer`, `org1-viewer` (one organization), `other-admin` (another company), `sandbox-admin`, `sandbox-leader`, `assess-admin`, `assess-owner`, `respondent1` to `respondent6`, and the platform administrators `platform-admin`, `platform-admin-2` and `platform-admin-3`.
- Compose: http://localhost:3000 is the web application. `npm run e2e` drives it signed in as the demo accounts (it seeds the demo series of org-1 and org-2 first); `npm run e2e:accounts` drives the whole journey from the platform administrator to a revoked API token with accounts it creates itself, reading the emails from Mailpit (its password-reset step uses `platform-admin-3`, so run it before or after, never during, a run that needs that account). If a host port is taken: `LIS_POSTGRES_PORT=15432 LIS_REDIS_PORT=16379 docker compose up -d --build`. The account endpoints are throttled, so a few runs an hour are fine; `docker compose exec redis redis-cli FLUSHALL` clears the counters.
