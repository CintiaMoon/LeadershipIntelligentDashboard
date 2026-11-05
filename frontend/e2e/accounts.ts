// The demo accounts the dev profile creates (config/beans/DevDemoAccounts.java): <name>@demo.test, one published password.
export const PASSWORD = 'demo-password-1234';

/** respondent-2 and respondent2 are the same account. */
const name = (persona: string) => persona.replace(/^respondent-(\d)$/, 'respondent$1');

export const emailOf = (persona: string) => `${name(persona)}@demo.test`;

/** The company a demo account belongs to (none for a platform administrator). */
export function companyOf(persona: string): string | null {
  if (persona.startsWith('platform-admin')) return null;
  return persona === 'other-admin' ? 'other-tenant' : 'demo-tenant';
}

/** What the persona picker used to offer (the start of the button text each spec matched) and the account that replaces it. */
const PICKER: [string, string][] = [
  ['Administrator Full access', 'admin'],
  ['Administrator of another tenant', 'other-admin'],
  ['Viewer, one organization', 'org1-viewer'],
  ['Viewer', 'viewer'],
  ['Analyst', 'analyst'],
  ['Owner', 'owner'],
  ['Platform administrator', 'platform-admin'],
  ['Sandbox administrator', 'sandbox-admin'],
  ['Assessments administrator', 'assess-admin'],
  ['Assessments owner', 'assess-owner'],
  ['Respondent', 'respondent1'],
];

/** The account for a persona-picker button pattern, so the specs keep reading as they did. */
export function accountFor(picker: RegExp): string {
  const text = picker.source.replace(/^\^/, '');
  const hit = PICKER.find(([start]) => text.startsWith(start));
  if (!hit) throw new Error('no demo account for ' + picker.source);
  return hit[1];
}
