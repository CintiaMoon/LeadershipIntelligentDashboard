import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { Field, FormError, inputClass, textareaClass } from '@/components/ui/Form';
import { ApiError } from '@/services/api/errors';
import { useApproveConfigVersion, useRejectConfigVersion } from '@/services/queries/configuration';

/** Every line the server gave for a refusal, in order (the fixtures that failed, the problems found). */
export function detailsOf(error: unknown): string[] {
  if (!(error instanceof ApiError) || !error.details) return [];
  return Object.entries(error.details)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([, text]) => text);
}

/**
 * The step that needs a second person (decision D53): approve and activate with a note, or send back with a reason. The page cannot
 * know who else worked on the version, so a refusal for that reason comes from the server and is shown as it is.
 */
export function ApprovalPanel({ versionId }: { versionId: string }) {
  const approve = useApproveConfigVersion();
  const reject = useRejectConfigVersion();
  const [note, setNote] = useState('');
  const [effective, setEffective] = useState('');
  const [reference, setReference] = useState('');
  const [limitations, setLimitations] = useState('');
  const [comment, setComment] = useState('');
  const [problem, setProblem] = useState<{ note?: string; comment?: string }>({});

  return (
    <Card aria-label="Approval">
      <CardHeader
        title="Approve or send back"
        description="This version waits for someone who did not create, edit or submit it. Approving makes it the active configuration from the date you choose."
      />
      <div className="grid gap-6 p-5 lg:grid-cols-2">
        <form
          noValidate
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (!note.trim()) {
              setProblem({ note: 'Say what you reviewed.' });
              return;
            }
            setProblem({});
            approve.mutate({
              id: versionId,
              request: {
                note: note.trim(),
                effectiveFrom: effective ? new Date(effective).toISOString() : undefined,
                calibrationReference: reference.trim() || undefined,
                knownLimitations: limitations.trim() || undefined,
              },
            });
          }}
        >
          <h3 className="text-sm font-semibold">Approve and activate</h3>
          <Field id="approve-note" label="Note" error={problem.note} help="What you reviewed and why it is right to approve.">
            <textarea id="approve-note" rows={3} className={textareaClass} value={note} onChange={(e) => setNote(e.target.value)} aria-invalid={!!problem.note} aria-describedby="approve-note-help approve-note-error" />
          </Field>
          <Field id="approve-reference" label="Calibration reference (optional)" help="The dataset, pilot or review this was checked against. Without one the version stays marked unvalidated.">
            <input id="approve-reference" type="text" className={inputClass} value={reference} onChange={(e) => setReference(e.target.value)} />
          </Field>
          <Field id="approve-limitations" label="Known limitations (optional)">
            <textarea id="approve-limitations" rows={2} className={textareaClass} value={limitations} onChange={(e) => setLimitations(e.target.value)} />
          </Field>
          <Field id="approve-effective" label="Applies from (optional)" help="Leave empty for now. A later date keeps the current version in use until then.">
            <input id="approve-effective" type="datetime-local" className={`${inputClass} !w-auto`} value={effective} onChange={(e) => setEffective(e.target.value)} />
          </Field>
          <FormError error={approve.error} details={detailsOf(approve.error)} />
          <Button type="submit" variant="primary" disabled={approve.isPending || reject.isPending}>
            {approve.isPending ? 'Approving' : 'Approve and activate'}
          </Button>
        </form>

        <form
          noValidate
          className="space-y-4"
          onSubmit={(e) => {
            e.preventDefault();
            if (!comment.trim()) {
              setProblem({ comment: 'Say what must change.' });
              return;
            }
            setProblem({});
            reject.mutate({ id: versionId, comment: comment.trim() });
          }}
        >
          <h3 className="text-sm font-semibold">Send back to draft</h3>
          <Field id="reject-comment" label="What must change" error={problem.comment}>
            <textarea id="reject-comment" rows={4} className={textareaClass} value={comment} onChange={(e) => setComment(e.target.value)} aria-invalid={!!problem.comment} aria-describedby="reject-comment-error" />
          </Field>
          <FormError error={reject.error} />
          <Button type="submit" disabled={approve.isPending || reject.isPending}>
            {reject.isPending ? 'Sending back' : 'Send back'}
          </Button>
        </form>
      </div>
    </Card>
  );
}
