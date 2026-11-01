import { setupServer } from 'msw/node';
import { afterAll, afterEach, beforeAll, beforeEach } from 'vitest';
import { configureApi } from '@/services/api/client';
import { useSession } from '@/stores/session';
import { dashboardHandlers } from './msw';

/** One mock API per test file, with the happy-path handlers installed before every test. */
export function installMockApi() {
  const server = setupServer();
  beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
  afterEach(() => server.resetHandlers());
  afterAll(() => server.close());
  beforeEach(() => {
    configureApi({ getToken: () => 'tok', onUnauthorized: () => undefined });
    useSession.setState({ organizationId: 'org-1' });
    server.use(...dashboardHandlers());
  });
  return server;
}
