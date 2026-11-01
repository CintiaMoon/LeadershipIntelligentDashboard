// Regenerates src/types/api.d.ts from the running backend's OpenAPI document (docker compose up -d --build).
// The document needs any authenticated role, so a token is taken from the local mock identity provider.
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';

const authUrl = process.env.AUTH_URL ?? 'http://localhost:8081/default';
const apiUrl = process.env.API_URL ?? 'http://localhost:8080';

const tokenResponse = await fetch(`${authUrl}/token`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ grant_type: 'client_credentials', client_id: 'viewer', client_secret: 'secret' }),
});
if (!tokenResponse.ok) throw new Error(`token request failed: ${tokenResponse.status}`);
const { access_token: token } = await tokenResponse.json();

const docResponse = await fetch(`${apiUrl}/v3/api-docs`, { headers: { Authorization: `Bearer ${token}` } });
if (!docResponse.ok) throw new Error(`OpenAPI request failed: ${docResponse.status}`);
mkdirSync('openapi', { recursive: true });
writeFileSync('openapi/openapi.json', JSON.stringify(await docResponse.json(), null, 2));
execFileSync('npx', ['openapi-typescript', 'openapi/openapi.json', '-o', 'src/types/api.d.ts'], { stdio: 'inherit', shell: true });
console.log('generated src/types/api.d.ts');
