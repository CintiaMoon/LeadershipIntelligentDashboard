// Regenerates src/types/api.d.ts from the running backend's OpenAPI document (docker compose up -d --build, profile dev).
// The document needs a caller with any role, so the script signs in as a demo account (decision D65: sessions and CSRF), creates a
// short-lived read-only API token, reads the document with it and revokes it.
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';

const apiUrl = process.env.API_URL ?? 'http://localhost:8080';
const email = process.env.DEMO_EMAIL ?? 'admin@demo.test';
const password = process.env.DEMO_PASSWORD ?? 'demo-password-1234';
const company = process.env.DEMO_COMPANY ?? 'demo-tenant';

let cookies = '';
async function call(path, { method = 'GET', body, headers = {} } = {}) {
  const response = await fetch(`${apiUrl}${path}`, { method, headers: { ...(cookies ? { Cookie: cookies } : {}), ...headers, ...(body ? { 'Content-Type': 'application/json' } : {}) }, body: body ? JSON.stringify(body) : undefined });
  const set = response.headers.getSetCookie?.() ?? [];
  if (set.length) {
    const jar = new Map(cookies.split('; ').filter(Boolean).map((c) => c.split(/=(.*)/s).slice(0, 2)));
    for (const line of set) { const [pair] = line.split(';'); const [k, v] = pair.split(/=(.*)/s); jar.set(k, v); }
    cookies = [...jar].map(([k, v]) => `${k}=${v}`).join('; ');
  }
  return response;
}
const csrf = async () => (await (await call('/api/v1/auth/csrf')).json()).token;

const login = await call('/api/v1/auth/login', { method: 'POST', body: { email, password }, headers: { 'X-XSRF-TOKEN': await csrf() } });
if (!login.ok) throw new Error(`sign-in failed: ${login.status}`);
const token = await csrf();
const created = await call('/api/v1/api-tokens', { method: 'POST', body: { name: 'gen:api (short-lived)', role: 'VIEWER', organizationIds: ['org-1'] }, headers: { 'X-XSRF-TOKEN': token, 'X-Company-Id': company } });
if (!created.ok) throw new Error(`token request failed: ${created.status}`);
const { token: info, secret } = await created.json();

try {
  const docResponse = await fetch(`${apiUrl}/v3/api-docs`, { headers: { Authorization: `Bearer ${secret}` } });
  if (!docResponse.ok) throw new Error(`OpenAPI request failed: ${docResponse.status}`);
  mkdirSync('openapi', { recursive: true });
  writeFileSync('openapi/openapi.json', JSON.stringify(await docResponse.json(), null, 2));
} finally {
  await call(`/api/v1/api-tokens/${info.id}`, { method: 'DELETE', headers: { 'X-XSRF-TOKEN': token, 'X-Company-Id': company } });
}
execFileSync('npx', ['openapi-typescript', 'openapi/openapi.json', '-o', 'src/types/api.d.ts'], { stdio: 'inherit', shell: true });
console.log('generated src/types/api.d.ts');
