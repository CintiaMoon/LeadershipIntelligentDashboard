import { UserManager, WebStorageStateStore, type UserManagerSettings } from 'oidc-client-ts';
import { authConfig } from './config';

const PERSONA_KEY = 'lis.persona';
const RETURN_KEY = 'lis.returnTo';

export interface Persona {
  id: string;
  label: string;
  description: string;
}

/** Development only: the local mock identity provider maps claims by client_id (decision D41). */
export const PERSONAS: Persona[] = [
  { id: 'owner', label: 'Owner', description: 'Full access to both organizations' },
  { id: 'admin', label: 'Administrator', description: 'Full access, can choose configuration versions' },
  { id: 'leader', label: 'Engineering leader', description: 'Can submit and score assessments' },
  { id: 'analyst', label: 'Analyst', description: 'Reads everything, including evidence' },
  { id: 'viewer', label: 'Viewer', description: 'Reads results; evidence is redacted' },
  { id: 'org1-viewer', label: 'Viewer, one organization', description: 'Sees org-1 only' },
  { id: 'other-admin', label: 'Administrator of another tenant', description: 'Sees none of the demo data' },
  { id: 'assess-admin', label: 'Assessments administrator', description: 'Administrator of a scratch organization for trying out questionnaires, people and runs' },
  { id: 'respondent-1', label: 'Respondent', description: 'Answers the questionnaires assigned to them and sees nothing else' },
  { id: 'platform-admin', label: 'Platform administrator', description: 'Creates and lists tenants; sees no tenant data' },
  { id: 'sandbox-admin', label: 'Sandbox administrator', description: 'Sees the scratch organization used for trying out answers from teams' },
];

let manager: UserManager | null = null;
let managerClientId: string | null = null;

function clientId(): string {
  if (authConfig.devPersonas) {
    return window.sessionStorage.getItem(PERSONA_KEY) ?? authConfig.clientId;
  }
  return authConfig.clientId;
}

function settings(id: string): UserManagerSettings {
  const origin = window.location.origin;
  return {
    authority: authConfig.authority,
    client_id: id,
    redirect_uri: `${origin}/auth/callback`,
    post_logout_redirect_uri: `${origin}/login`,
    response_type: 'code',
    scope: 'openid',
    // Tokens live in sessionStorage (cleared with the tab), never in localStorage.
    userStore: new WebStorageStateStore({ store: window.sessionStorage }),
    automaticSilentRenew: false,
    loadUserInfo: false,
  };
}

export function getUserManager(): UserManager {
  const id = clientId();
  if (!manager || managerClientId !== id) {
    manager = new UserManager(settings(id));
    managerClientId = id;
  }
  return manager;
}

export function rememberPersona(personaId: string): void {
  window.sessionStorage.setItem(PERSONA_KEY, personaId);
}

export function rememberReturnTo(path: string): void {
  window.sessionStorage.setItem(RETURN_KEY, path);
}

export function takeReturnTo(): string {
  const path = window.sessionStorage.getItem(RETURN_KEY);
  window.sessionStorage.removeItem(RETURN_KEY);
  // the home route decides where a user starts: a platform administrator has no organization, so no dashboard
  return path && path.startsWith('/') && !path.startsWith('//') ? path : '/';
}
