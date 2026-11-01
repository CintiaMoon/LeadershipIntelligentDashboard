import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { BackLink, PageHeader } from '@/components/ui/PageHeader';
import { ApiError, describeError } from '@/services/api/errors';
import { useCreateTenant } from '@/services/queries/hooks';

// The same rule as the backend, so most mistakes are caught before the request; the backend still decides.
const schema = z.object({
  id: z
    .string()
    .trim()
    .regex(/^[a-z0-9][a-z0-9-]{1,62}$/, 'Use 2 to 63 lowercase letters, digits or hyphens, starting with a letter or digit.'),
  name: z.string().trim().min(1, 'Enter a name.').max(200, 'Use at most 200 characters.'),
});
type Values = z.infer<typeof schema>;

const input = 'mt-1 min-h-10 w-full rounded-md border border-line bg-surface px-3 text-sm text-ink aria-[invalid=true]:border-risk-critical';

/** Creates a tenant (decision D46). The id is permanent, so the page says so before it is submitted. */
export function CreateTenantPage() {
  const navigate = useNavigate();
  const create = useCreateTenant();
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { id: '', name: '' } });
  const { errors } = form.formState;

  const submit = form.handleSubmit((values) => {
    create.mutate(values, {
      onSuccess: (tenant) => navigate('/platform/tenants', { state: { created: tenant.id } }),
      onError: (error) => {
        if (error instanceof ApiError && error.status === 409) {
          form.setError('id', { type: 'server', message: 'A tenant with this id already exists. Choose another id.' });
        }
      },
    });
  });

  const failure = create.error && !(create.error instanceof ApiError && create.error.status === 409) ? describeError(create.error) : null;

  return (
    <div className="space-y-6">
      <PageHeader back={<BackLink to="/platform/tenants">Back to the tenants</BackLink>} title="Create a tenant" subtitle="A tenant is a separate customer with its own organizations and data." />
      <Card className="max-w-xl p-6">
        <form onSubmit={submit} noValidate className="space-y-5" aria-describedby={failure ? 'create-error' : undefined}>
          <div>
            <label htmlFor="tenant-id" className="text-sm font-medium">Tenant id</label>
            <input
              id="tenant-id"
              type="text"
              autoComplete="off"
              spellCheck={false}
              className={`${input} font-mono`}
              aria-invalid={!!errors.id}
              aria-describedby="tenant-id-help tenant-id-error"
              {...form.register('id')}
            />
            <p id="tenant-id-help" className="mt-1 text-xs text-ink-soft">
              Lowercase letters, digits and hyphens, for example <span className="font-mono">acme-corp</span>. It becomes the <span className="font-mono">tenant_id</span> in
              the sign-in token and <strong>cannot be changed later</strong>.
            </p>
            <p id="tenant-id-error" role="alert" className="mt-1 text-xs text-risk-critical">{errors.id?.message}</p>
          </div>
          <div>
            <label htmlFor="tenant-name" className="text-sm font-medium">Name</label>
            <input id="tenant-name" type="text" autoComplete="off" className={input} aria-invalid={!!errors.name} aria-describedby="tenant-name-error" {...form.register('name')} />
            <p id="tenant-name-error" role="alert" className="mt-1 text-xs text-risk-critical">{errors.name?.message}</p>
          </div>
          {failure && (
            <p id="create-error" role="alert" className="rounded-lg border border-risk-critical/30 bg-risk-critical-soft px-4 py-3 text-sm text-risk-critical">
              <strong>{failure.title}.</strong> {failure.message}
              {failure.correlationId && <span className="mt-1 block text-xs">Reference {failure.correlationId}</span>}
            </p>
          )}
          <div className="flex items-center gap-3">
            <Button type="submit" variant="primary" disabled={create.isPending}>
              {create.isPending ? 'Creating' : 'Create tenant'}
            </Button>
            <Button onClick={() => navigate('/platform/tenants')} disabled={create.isPending}>
              Cancel
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}
