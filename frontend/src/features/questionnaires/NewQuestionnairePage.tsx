import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Field, FormError, inputClass } from '@/components/ui/Form';
import { BackLink, PageHeader } from '@/components/ui/PageHeader';
import { useCreateQuestionnaire } from '@/services/queries/hooks';

const schema = z.object({
  name: z.string().trim().min(1, 'Enter a name.').max(200, 'Use at most 200 characters.'),
  start: z.enum(['empty', 'initial']),
});
type Values = z.infer<typeof schema>;

/** Creates a questionnaire: empty, or as a copy of the initial one to adjust (decision D50). Nothing is published here. */
export function NewQuestionnairePage() {
  const navigate = useNavigate();
  const create = useCreateQuestionnaire();
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { name: '', start: 'empty' } });
  const { errors } = form.formState;

  const submit = form.handleSubmit((values) => {
    create.mutate(
      { name: values.name, fromInitial: values.start === 'initial' },
      { onSuccess: (detail) => navigate(`/questionnaires/${detail.id}`, { state: { notes: detail.notes ?? [] } }) },
    );
  });

  return (
    <div className="space-y-6">
      <PageHeader
        back={<BackLink to="/questionnaires">Back to the questionnaires</BackLink>}
        title="New questionnaire"
        subtitle="Name it first. You add and configure the questions next, and nothing is published until you say so."
      />
      <Card className="max-w-xl p-6">
        <form onSubmit={submit} noValidate className="space-y-5">
          <Field id="questionnaire-name" label="Name" error={errors.name?.message}>
            <input
              id="questionnaire-name"
              type="text"
              autoComplete="off"
              placeholder="e.g. Q1 Leadership 360"
              className={inputClass}
              aria-invalid={!!errors.name}
              aria-describedby="questionnaire-name-error"
              {...form.register('name')}
            />
          </Field>
          <fieldset className="space-y-2">
            <legend className="text-sm font-medium">Start from</legend>
            <label className="flex items-start gap-2 text-sm">
              <input type="radio" value="empty" className="mt-1" {...form.register('start')} />
              <span>
                <strong>An empty questionnaire.</strong> <span className="text-ink-soft">You write every question yourself.</span>
              </span>
            </label>
            <label className="flex items-start gap-2 text-sm">
              <input type="radio" value="initial" className="mt-1" {...form.register('start')} />
              <span>
                <strong>The initial questionnaire.</strong>{' '}
                <span className="text-ink-soft">A copy of the platform questions and rules to adjust. What the builder cannot carry over is listed afterwards.</span>
              </span>
            </label>
          </fieldset>
          <FormError error={create.error} />
          <div className="flex items-center gap-3">
            <Button type="submit" variant="primary" disabled={create.isPending}>
              {create.isPending ? 'Creating' : 'Create questionnaire'}
            </Button>
            <Button onClick={() => navigate('/questionnaires')} disabled={create.isPending}>
              Cancel
            </Button>
          </div>
        </form>
      </Card>
    </div>
  );
}
