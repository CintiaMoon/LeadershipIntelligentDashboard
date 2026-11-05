import { useMemo, useState, type FormEvent } from 'react';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Field, inputClass, selectClass } from '@/components/ui/Form';
import { PageHeader } from '@/components/ui/PageHeader';
import { QuerySection } from '@/components/ui/QuerySection';
import { EmptyState } from '@/components/ui/StateViews';
import { useSecurityAudit } from '@/services/queries/identity';
import type { SecurityAuditParams, SecurityEvent } from '@/types/identity';
import { humanizeCode } from '@/utils/presentation';

const SIZE = 25;

/** The events worth looking for first; any other event type can still be typed. */
const KNOWN_EVENTS = [
  'MEMBERSHIP_INVITED',
  'MEMBERSHIP_ACCEPTED',
  'MEMBERSHIP_ROLE_CHANGED',
  'MEMBERSHIP_SCOPE_CHANGED',
  'MEMBERSHIP_SUSPENDED',
  'MEMBERSHIP_REACTIVATED',
  'MEMBERSHIP_REMOVED',
  'API_TOKEN_CREATED',
  'API_TOKEN_REVOKED',
  'API_TOKEN_REJECTED',
  'API_TOKEN_USED',
  'COMPANY_ACCESS_DENIED',
  'RATE_LIMITED',
  'LOGIN',
  'LOGOUT',
  'REGISTERED',
  'PASSWORD_CHANGED',
  'PASSWORD_RESET_COMPLETED',
  'SECURITY_AUDIT_READ',
];

function dayStart(date: string): string | undefined {
  return date ? `${date}T00:00:00Z` : undefined;
}

/** The first moment after the chosen day: the audit's end of a period is exclusive. */
function dayAfter(date: string): string | undefined {
  if (!date) return undefined;
  const next = new Date(`${date}T00:00:00Z`);
  next.setUTCDate(next.getUTCDate() + 1);
  return next.toISOString();
}

/**
 * The security events (decision D74): who invited, changed, suspended or removed whom, API token use, refused access. A company's
 * owners and administrators see their own company's events; the platform administrator sees every event and may narrow it to a
 * company. Details are short labels; no password or token is ever stored.
 */
export function SecurityAuditPage({ platform = false }: { platform?: boolean }) {
  const [eventType, setEventType] = useState('');
  const [outcome, setOutcome] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [company, setCompany] = useState('');
  const [applied, setApplied] = useState<SecurityAuditParams>({});
  const [page, setPage] = useState(0);

  const params = useMemo(() => ({ ...applied, page, size: SIZE }), [applied, page]);
  const events = useSecurityAudit(platform, params);

  const apply = (event: FormEvent) => {
    event.preventDefault();
    setPage(0);
    setApplied({
      eventType: eventType.trim() || undefined,
      outcome: outcome || undefined,
      from: dayStart(from),
      to: dayAfter(to),
      tenantId: platform ? company.trim() || undefined : undefined,
    });
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title={platform ? 'Platform security audit' : 'Security audit'}
        subtitle={platform ? 'Every security event of the platform, newest first.' : 'Security events of this company, newest first. They are kept for 400 days.'}
      />
      <Card>
        <form onSubmit={apply} className="grid gap-4 px-5 py-4 sm:grid-cols-2 lg:grid-cols-5" aria-label="Filter the events">
          <Field id="audit-event" label="Event">
            <input id="audit-event" list="audit-events" value={eventType} onChange={(e) => setEventType(e.target.value)} className={inputClass} spellCheck={false} />
            <datalist id="audit-events">
              {KNOWN_EVENTS.map((e) => (
                <option key={e} value={e} />
              ))}
            </datalist>
          </Field>
          <Field id="audit-outcome" label="Outcome">
            <select id="audit-outcome" value={outcome} onChange={(e) => setOutcome(e.target.value)} className={selectClass}>
              <option value="">Any</option>
              <option value="SUCCESS">Success</option>
              <option value="FAILURE">Failure</option>
            </select>
          </Field>
          <Field id="audit-from" label="From">
            <input id="audit-from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} className={inputClass} />
          </Field>
          <Field id="audit-to" label="Until (including)">
            <input id="audit-to" type="date" value={to} onChange={(e) => setTo(e.target.value)} className={inputClass} />
          </Field>
          {platform && (
            <Field id="audit-company" label="Company id">
              <input id="audit-company" value={company} onChange={(e) => setCompany(e.target.value)} className={inputClass} spellCheck={false} />
            </Field>
          )}
          <div className="flex items-end">
            <Button type="submit" variant="primary">
              Show
            </Button>
          </div>
        </form>
      </Card>
      <QuerySection query={events} label="the security events">
        {() => {
          const data = events.data;
          if (!data || data.total === 0) return <EmptyState title="No events" description="Nothing matches. Widen the period or clear the filters." />;
          const last = Math.max(0, Math.ceil(data.total / data.size) - 1);
          return (
            <Card>
              <CardHeader title={`${data.total} ${data.total === 1 ? 'event' : 'events'}`} description={`Page ${data.page + 1} of ${last + 1}`} />
              <div className="overflow-x-auto">
                <table className="w-full min-w-[44rem] text-left text-sm">
                  <caption className="sr-only">Security events</caption>
                  <thead className="text-xs uppercase tracking-wide text-ink-soft">
                    <tr>
                      <th scope="col" className="px-5 py-2 font-medium">When</th>
                      <th scope="col" className="px-3 py-2 font-medium">Event</th>
                      <th scope="col" className="px-3 py-2 font-medium">Outcome</th>
                      <th scope="col" className="px-3 py-2 font-medium">Who</th>
                      <th scope="col" className="px-3 py-2 font-medium">From</th>
                      <th scope="col" className="px-5 py-2 font-medium">Details</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {data.items.map((event) => (
                      <Row key={event.id} event={event} platform={platform} />
                    ))}
                  </tbody>
                </table>
              </div>
              <nav aria-label="Pages" className="flex items-center justify-between border-t border-line px-5 py-3">
                <Button disabled={data.page === 0} onClick={() => setPage(data.page - 1)}>
                  Newer
                </Button>
                <span className="text-sm text-ink-soft">
                  {data.page * data.size + 1} to {Math.min((data.page + 1) * data.size, data.total)} of {data.total}
                </span>
                <Button disabled={data.page >= last} onClick={() => setPage(data.page + 1)}>
                  Older
                </Button>
              </nav>
            </Card>
          );
        }}
      </QuerySection>
    </div>
  );
}

function Row({ event, platform }: { event: SecurityEvent; platform: boolean }) {
  const details = Object.entries(event.detail ?? {});
  return (
    <tr>
      <td className="whitespace-nowrap px-5 py-2 align-top text-xs text-ink-soft">{new Date(event.occurredAt).toLocaleString()}</td>
      <td className="px-3 py-2 align-top">
        <span className="font-medium">{humanizeCode(event.eventType)}</span>
        {platform && event.tenantId && <span className="ml-2 font-mono text-xs text-ink-soft">{event.tenantId}</span>}
      </td>
      <td className="px-3 py-2 align-top">
        <Badge tone={event.outcome === 'SUCCESS' ? 'ok' : 'high'}>{event.outcome === 'SUCCESS' ? 'Success' : 'Failure'}</Badge>
      </td>
      <td className="px-3 py-2 align-top text-xs">{event.userEmail ?? <span className="text-ink-soft">not known</span>}</td>
      <td className="px-3 py-2 align-top font-mono text-xs text-ink-soft">{event.clientIp ?? ''}</td>
      <td className="px-5 py-2 align-top text-xs text-ink-soft">
        {details.length === 0 ? '' : details.map(([key, value]) => `${key}: ${value}`).join(' · ')}
      </td>
    </tr>
  );
}
