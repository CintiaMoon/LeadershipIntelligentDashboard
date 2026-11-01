import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Card } from '@/components/ui/Card';
import { PageHeader } from '@/components/ui/PageHeader';
import { QuerySection } from '@/components/ui/QuerySection';
import { EmptyState } from '@/components/ui/StateViews';
import { Button } from '@/components/ui/Button';
import { useAuth } from '@/app/providers/auth-context';
import { useTenants } from '@/services/queries/hooks';
import { isPlatformAdmin } from '@/services/auth/principal';
import { formatDateTime } from '@/utils/presentation';

const link = 'inline-flex min-h-9 items-center rounded-md bg-accent px-3.5 text-sm font-medium text-white hover:bg-accent-ink';

/**
 * Every tenant, for the platform administrator (decision D46). A tenant is only an id and a name here: nothing about its
 * organizations, assessments or scores is shown, because the platform role sees no tenant data.
 */
export function TenantListPage() {
  const { principal } = useAuth();
  const tenants = useTenants(isPlatformAdmin(principal));
  const created = (useLocation().state as { created?: string } | null)?.created;
  const [filter, setFilter] = useState('');
  const needle = filter.trim().toLowerCase();

  return (
    <div className="space-y-6">
      <PageHeader
        title="Tenants"
        subtitle="Each tenant is a separate customer. Its data is never visible to another tenant, or to you."
        actions={
          <Link to="/platform/tenants/new" className={link}>
            Create tenant
          </Link>
        }
      />
      {created && (
        <p role="status" className="rounded-lg border border-ok/30 bg-ok-soft px-4 py-3 text-sm text-ok">
          Tenant <strong>{created}</strong> was created. Its users can sign in as soon as the identity provider issues them a token for it.
        </p>
      )}
      <QuerySection query={tenants} label="the tenants">
        {() => {
          const items = tenants.data?.items ?? [];
          if (items.length === 0) {
            return (
              <EmptyState
                title="No tenants yet"
                description="Create the first tenant to let a customer start using the product."
                action={
                  <Link to="/platform/tenants/new" className={link}>
                    Create tenant
                  </Link>
                }
              />
            );
          }
          const shown = needle ? items.filter((t) => `${t.id} ${t.name}`.toLowerCase().includes(needle)) : items;
          return (
            <Card>
              <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-5 py-4">
                <label className="flex items-center gap-2 text-sm text-ink-soft">
                  <span>Filter</span>
                  <input
                    type="search"
                    value={filter}
                    onChange={(e) => setFilter(e.target.value)}
                    placeholder="Id or name"
                    className="min-h-9 w-56 rounded-md border border-line bg-surface px-2 text-sm text-ink"
                  />
                </label>
                <div className="flex items-center gap-3 text-sm text-ink-soft">
                  <span role="status">
                    {shown.length} of {items.length} {items.length === 1 ? 'tenant' : 'tenants'}
                  </span>
                  <Button variant="ghost" onClick={() => void tenants.refetch()} disabled={tenants.isFetching}>
                    {tenants.isFetching ? 'Refreshing' : 'Refresh'}
                  </Button>
                </div>
              </div>
              {shown.length === 0 ? (
                <p className="px-5 py-8 text-sm text-ink-soft">No tenant matches &ldquo;{filter.trim()}&rdquo;.</p>
              ) : (
                <div className="overflow-x-auto">
                  <table className="w-full min-w-max text-sm">
                    <caption className="sr-only">Tenants, oldest first</caption>
                    <thead>
                      <tr className="border-b border-line text-left text-xs text-ink-soft">
                        <th scope="col" className="px-5 py-2 font-medium">Id</th>
                        <th scope="col" className="px-5 py-2 font-medium">Name</th>
                        <th scope="col" className="px-5 py-2 font-medium">Created</th>
                      </tr>
                    </thead>
                    <tbody>
                      {shown.map((t) => (
                        <tr key={t.id} className="border-b border-line/60">
                          <th scope="row" className="px-5 py-2 text-left font-mono text-xs font-normal">{t.id}</th>
                          <td className="px-5 py-2">{t.name}</td>
                          <td className="px-5 py-2 tabular-nums text-ink-soft">{formatDateTime(t.createdAt)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </Card>
          );
        }}
      </QuerySection>
    </div>
  );
}
