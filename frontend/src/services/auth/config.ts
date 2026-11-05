/**
 * How the interface signs people in. {@code session} is the application's own accounts: a cookie session, CSRF protection and the
 * company named in the address (decisions D65 to D69). {@code oidc} is the external identity provider with bearer tokens; it is kept only
 * until the last phase of the identity work removes it together with its end-to-end suite, so that suite stays runnable meanwhile.
 * Build with {@code VITE_AUTH_MODE=oidc} to use it; the default is {@code session}.
 */
export type AuthMode = 'session' | 'oidc';

// Read when asked rather than once at load, so a test can switch the mode.
export const authConfig = {
  get mode(): AuthMode {
    return import.meta.env.VITE_AUTH_MODE === 'oidc' ? 'oidc' : 'session';
  },
  get authority(): string {
    return (import.meta.env.VITE_OIDC_AUTHORITY as string | undefined) ?? '';
  },
  get clientId(): string {
    return (import.meta.env.VITE_OIDC_CLIENT_ID as string | undefined) ?? 'lis-web';
  },
  get devPersonas(): boolean {
    return import.meta.env.VITE_DEV_PERSONAS === 'true';
  },
};

export const isSessionMode = (): boolean => authConfig.mode === 'session';
