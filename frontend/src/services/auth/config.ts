const env = import.meta.env;

export const authConfig = {
  authority: (env.VITE_OIDC_AUTHORITY as string | undefined) ?? '',
  clientId: (env.VITE_OIDC_CLIENT_ID as string | undefined) ?? 'lis-web',
  devPersonas: env.VITE_DEV_PERSONAS === 'true',
};
