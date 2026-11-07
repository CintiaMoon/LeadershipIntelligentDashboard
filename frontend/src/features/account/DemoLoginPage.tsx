import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useAuth } from '@/app/providers/auth-context';
import { Button } from '@/components/ui/Button';
import { identityApi, describeAuthError } from '@/services/api/identity';
import { AuthShell, Notice } from './AuthShell';
import { goTo, safeReturn } from './navigation';

/**
 * TESTING ONLY. One click signs in as one of the demo accounts that the backend creates with the `dev` profile
 * (config/beans/DevDemoAccounts.java), so nobody types an address and a password. The page and its route exist only in a build made
 * with VITE_DEMO_LOGIN=true (npm run dev, and the local compose stack); a normal production build contains neither the page nor the
 * published password (`npm run check:no-mocks` fails if it does).
 */
const PASSWORD = 'demo-password-1234';

interface Demo {
  name: string;
  label: string;
  roles: string;
}

const GROUPS: { title: string; accounts: Demo[] }[] = [
  {
    title: 'Company demo-tenant (organizations org-1 and org-2)',
    accounts: [
      { name: 'owner', label: 'Owner', roles: 'Everything in the company' },
      { name: 'admin', label: 'Administrator', roles: 'Manages members, tokens, questionnaires, roster' },
      { name: 'leader', label: 'Engineering leader', roles: 'Scores assessments, reads results' },
      { name: 'analyst', label: 'Analyst', roles: 'Reads results, answers and the configuration' },
      { name: 'viewer', label: 'Viewer', roles: 'Reads results with values withheld' },
      { name: 'org1-viewer', label: 'Viewer, one organization', roles: 'Limited to org-1' },
    ],
  },
  {
    title: 'Scratch organizations of the same company',
    accounts: [
      { name: 'sandbox-admin', label: 'Sandbox administrator', roles: 'Administrator of org-sandbox' },
      { name: 'sandbox-leader', label: 'Sandbox engineering leader', roles: 'Leader in org-sandbox' },
      { name: 'assess-admin', label: 'Assessments administrator', roles: 'Administrator of org-assess' },
      { name: 'assess-owner', label: 'Assessments owner', roles: 'Owner of org-assess' },
    ],
  },
  { title: 'Another company', accounts: [{ name: 'other-admin', label: 'Administrator of another company', roles: 'Company other-tenant' }] },
  {
    title: 'Respondents (they answer questionnaires, nothing else)',
    accounts: [1, 2, 3, 4, 5, 6].map((n) => ({ name: `respondent${n}`, label: `Respondent ${n}`, roles: 'Answers questionnaires' })),
  },
  {
    title: 'Platform',
    accounts: [
      { name: 'platform-admin', label: 'Platform administrator', roles: 'Tenants and the platform configuration' },
      { name: 'platform-admin-2', label: 'Second platform administrator', roles: 'Approves the first one\'s changes' },
    ],
  },
];

export function DemoLoginPage() {
  const { status, refresh } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const from = safeReturn(params.get('from'));
  const [pending, setPending] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (status === 'authenticated') goTo(navigate, from ?? '/');
  }, [status, from, navigate]);

  if (status === 'authenticated') return null;

  const signInAs = async (account: Demo) => {
    setError(null);
    setPending(account.name);
    try {
      await identityApi.login(`${account.name}@demo.test`, PASSWORD);
      await refresh?.();
    } catch (e) {
      setError(describeAuthError(e));
    } finally {
      setPending(null);
    }
  };

  return (
    <AuthShell
      title="Test accounts"
      subtitle="Testing only: sign in as a demo account with one click."
      footer={
        <Link to="/login" className="font-medium text-accent-ink underline-offset-2 hover:underline">
          Sign in with an email and a password
        </Link>
      }
    >
      <Notice tone="error">These accounts exist only on a development stack. Never enable this page in production.</Notice>
      {error && <Notice tone="error">{error}</Notice>}
      {GROUPS.map((group) => (
        <section key={group.title} aria-label={group.title} className="space-y-2">
          <h2 className="text-xs font-semibold uppercase tracking-wide text-ink-faint">{group.title}</h2>
          <ul className="space-y-1.5">
            {group.accounts.map((account) => (
              <li key={account.name}>
                <Button
                  variant="secondary"
                  className="w-full !justify-between text-left"
                  disabled={pending !== null}
                  aria-label={`Sign in as ${account.label}`}
                  onClick={() => void signInAs(account)}
                >
                  <span className="font-medium">{pending === account.name ? 'Signing in' : account.label}</span>
                  <span className="text-xs font-normal text-ink-soft">{account.roles}</span>
                </Button>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </AuthShell>
  );
}
