import { useEffect } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '@/app/providers/auth-context';
import { companyUrl } from '@/app/router/company-path';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { humanizeCode } from '@/utils/presentation';
import { AuthShell } from './AuthShell';

/**
 * Where a signed-in person chooses the company to open (decision D69). Only companies where the membership is active are listed.
 * With one company the person goes straight in; with none they are told they have none yet. Opening a company is a full page load,
 * so nothing of another company's session stays behind.
 */
export function CompanyPickerPage() {
  const { account, companies, signOut } = useAuth();
  const list = companies ?? [];

  const only = list.length === 1 ? list[0] : undefined;
  // with one company the person goes straight in (a full page load: nothing of another company stays behind)
  useEffect(() => {
    if (only) window.location.replace(companyUrl(only.id));
  }, [only]);

  if (account?.platformAdmin) return <Navigate to="/platform/tenants" replace />;
  if (only) {
    return (
      <AuthShell title={`Opening ${only.name}`}>
        <p role="status" className="text-sm text-ink-soft">One moment&hellip;</p>
      </AuthShell>
    );
  }
  if (list.length === 0) {
    return (
      <AuthShell
        title="You have no company yet"
        subtitle="Your account is ready, but it is not a member of any company."
        footer={<Button variant="ghost" onClick={() => void signOut()}>Sign out</Button>}
      >
        <p className="text-sm text-ink-soft">
          Ask an owner or administrator of your company to invite <strong>{account?.email}</strong>. When you open the link in the invitation, the company appears here.
        </p>
      </AuthShell>
    );
  }
  return (
    <AuthShell title="Choose a company" subtitle={`Signed in as ${account?.email ?? ''}`} footer={<Button variant="ghost" onClick={() => void signOut()}>Sign out</Button>}>
      <ul className="space-y-2">
        {list.map((company) => (
          <li key={company.id}>
            <Card className="transition-colors hover:border-accent">
              <a href={companyUrl(company.id)} className="flex items-center justify-between gap-3 px-4 py-3">
                <span>
                  <span className="block text-sm font-medium">{company.name}</span>
                  <span className="mt-1 flex flex-wrap gap-1.5">
                    {company.roles.map((role) => (
                      <Badge key={role}>{humanizeCode(role)}</Badge>
                    ))}
                  </span>
                </span>
                <span aria-hidden="true" className="text-ink-faint">&rarr;</span>
              </a>
            </Card>
          </li>
        ))}
      </ul>
    </AuthShell>
  );
}
