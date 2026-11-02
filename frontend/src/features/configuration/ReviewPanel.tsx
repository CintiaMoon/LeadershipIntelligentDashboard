import { Badge } from '@/components/ui/Badge';
import { Card, CardHeader } from '@/components/ui/Card';
import type { ConfigReview } from '@/types/api';
import { formatDateTime } from '@/utils/presentation';
import { FixtureResults, SummaryLine } from './PreviewPanel';

/**
 * The calibration and approval record of a version (decision D57): who submitted it, what the checks showed, who approved it and
 * what they said it was checked against. A version without a calibration reference is plainly marked unvalidated.
 */
export function ReviewPanel({ review, unvalidated }: { review: ConfigReview; unvalidated: boolean }) {
  const approved = review.approvedBy !== undefined;
  return (
    <Card aria-label="Review record">
      <CardHeader
        title="Review record"
        action={
          <Badge tone={unvalidated ? 'medium' : 'ok'}>{unvalidated ? 'Unvalidated' : 'Calibration stated'}</Badge>
        }
      />
      <dl className="grid gap-x-6 gap-y-3 px-5 py-4 text-sm sm:grid-cols-2">
        {review.submittedAt && (
          <div>
            <dt className="text-ink-soft">Submitted</dt>
            <dd>
              {review.submittedBy} on {formatDateTime(review.submittedAt)}
            </dd>
          </div>
        )}
        {approved && (
          <div>
            <dt className="text-ink-soft">Approved</dt>
            <dd>
              {review.approvedBy} on {formatDateTime(review.approvedAt)}
            </dd>
          </div>
        )}
        {approved && (
          <div className="sm:col-span-2">
            <dt className="text-ink-soft">Note</dt>
            <dd>{review.approvalNote}</dd>
          </div>
        )}
        {approved && (
          <div>
            <dt className="text-ink-soft">Calibration reference</dt>
            <dd>{review.calibrationReference ?? 'None stated: the configuration is unvalidated.'}</dd>
          </div>
        )}
        {approved && (
          <div>
            <dt className="text-ink-soft">Known limitations</dt>
            <dd>{review.knownLimitations ?? 'None stated.'}</dd>
          </div>
        )}
        {review.rejectedBy && (
          <div className="sm:col-span-2">
            <dt className="text-ink-soft">Sent back</dt>
            <dd>
              {review.rejectedBy} on {formatDateTime(review.rejectedAt)}: {review.rejectionComment}
            </dd>
          </div>
        )}
      </dl>
      <div className="border-t border-line">
        <h3 className="px-5 pt-3 text-sm font-semibold">At submission</h3>
        <SummaryLine summary={review.preview} />
        {(review.fixtures ?? []).length > 0 && <FixtureResults fixtures={review.fixtures ?? []} />}
      </div>
    </Card>
  );
}
