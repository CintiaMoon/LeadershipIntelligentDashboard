import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '@/app/providers/auth-context';
import { Badge } from '@/components/ui/Badge';
import { Button } from '@/components/ui/Button';
import { Card, CardHeader } from '@/components/ui/Card';
import { FormError } from '@/components/ui/Form';
import { BackLink, PageHeader } from '@/components/ui/PageHeader';
import { QuerySection } from '@/components/ui/QuerySection';
import { canChangeConfiguration } from '@/services/auth/principal';
import {
  useArchiveConfigVersion,
  useConfigLayers,
  useConfigVersion,
  useCopyConfigVersion,
  usePauseConfigVersion,
  usePreviewConfigVersion,
  useResumeConfigVersion,
  useSubmitConfigVersion,
  useUpdateConfigDraft,
  useValidateConfigVersion,
} from '@/services/queries/configuration';
import type { ConfigVersion } from '@/types/api';
import { formatDateTime } from '@/utils/presentation';
import { ApprovalPanel, detailsOf } from './ApprovalPanel';
import { ChangesPanel } from './ChangesPanel';
import { ConfigForms } from './forms/ConfigForms';
import { jsonProblem } from './diff';
import { JsonEditor } from './JsonEditor';
import { editable, layerName, layerPath, pretty, STATUS_LABEL, statusTone, versionPath } from './labels';
import { PreviewPanel } from './PreviewPanel';
import { ReviewPanel } from './ReviewPanel';

function parseOrNull(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

/** The comparison base: the layer's active version, shown beside the draft so a reviewer sees exactly what the draft changes. */
function Changes({ version, text }: { version: ConfigVersion; text: string }) {
  const layers = useConfigLayers();
  const layer = (layers.data ?? []).find((l) => l.id === version.layerId);
  const activeId = layer?.active?.id && layer.active.id !== version.id ? layer.active.id : undefined;
  const active = useConfigVersion(activeId);
  if (!activeId) return null;
  if (active.isPending || active.isError || !active.data?.payload) return null;
  const after = editable(version) ? parseOrNull(text) : parseOrNull(version.payload ?? '');
  if (after === null) return null;
  return <ChangesPanel against={`active version ${active.data.version}`} before={parseOrNull(active.data.payload)} after={after} />;
}

function VersionBody({ version }: { version: ConfigVersion }) {
  const navigate = useNavigate();
  const { principal } = useAuth();
  const canChange = canChangeConfiguration(principal);
  const isDraft = editable(version);
  const saved = pretty(version.payload);
  const [text, setText] = useState(saved);
  const [view, setView] = useState<'form' | 'json'>('form');
  const dirty = isDraft && text !== saved;
  const invalid = jsonProblem(text) !== null || text.trim() === '';

  const save = useUpdateConfigDraft();
  const validate = useValidateConfigVersion();
  const preview = usePreviewConfigVersion();
  const submit = useSubmitConfigVersion();
  const pause = usePauseConfigVersion();
  const resume = useResumeConfigVersion();
  const archive = useArchiveConfigVersion();
  const copy = useCopyConfigVersion();
  const id = version.id as string;
  const busy = save.isPending || submit.isPending || pause.isPending || resume.isPending || archive.isPending || copy.isPending;
  const problems = validate.data && !validate.data.valid ? (validate.data.problems ?? []) : [];

  return (
    <div className="space-y-6">
      <PageHeader
        back={<BackLink to={layerPath({ scope: version.scope, scopeId: version.scopeId })}>{layerName(version.scope, version.scopeId)}</BackLink>}
        title={`Version ${version.version}`}
        subtitle={
          <>
            <Badge tone={statusTone(version.status)}>{STATUS_LABEL[version.status ?? ''] ?? version.status}</Badge>{' '}
            <Badge tone={version.unvalidated ? 'medium' : 'ok'}>{version.unvalidated ? 'Unvalidated' : 'Calibration stated'}</Badge>{' '}
            <span>
              created by {version.createdBy}, {formatDateTime(version.createdAt)}
            </span>
          </>
        }
        actions={
          canChange ? (
            <div className="flex flex-wrap gap-2">
              {version.status === 'ACTIVE' && (
                <Button disabled={busy} onClick={() => pause.mutate(id)}>
                  Pause
                </Button>
              )}
              {version.status === 'PAUSED' && (
                <Button disabled={busy} onClick={() => resume.mutate(id)}>
                  Resume
                </Button>
              )}
              {version.status === 'PAUSED' && (
                <Button disabled={busy} onClick={() => archive.mutate(id)}>
                  Archive
                </Button>
              )}
              {!isDraft && version.status !== 'VALIDATING' && (
                <Button disabled={busy} onClick={() => copy.mutate(id, { onSuccess: (d) => navigate(versionPath(d.id)) })}>
                  Copy as new draft
                </Button>
              )}
            </div>
          ) : undefined
        }
      />
      <FormError error={pause.error ?? resume.error ?? archive.error ?? copy.error} details={detailsOf(pause.error ?? resume.error ?? archive.error ?? copy.error)} />

      {version.status === 'VALIDATING' && canChange && <ApprovalPanel versionId={id} />}

      <Card aria-label="Document">
        <CardHeader
          title="Configuration document"
          description={isDraft ? 'Edit with the forms or as JSON (the same document), save it, then check it, preview it and submit it for approval.' : 'This version can no longer be edited. Copy it as a new draft to change it.'}
        />
        <div className="space-y-3 p-5">
          <div role="group" aria-label="View of the document" className="flex gap-2">
            <Button variant={view === 'form' ? 'primary' : 'secondary'} aria-pressed={view === 'form'} onClick={() => setView('form')}>
              Form
            </Button>
            <Button variant={view === 'json' ? 'primary' : 'secondary'} aria-pressed={view === 'json'} onClick={() => setView('json')}>
              JSON
            </Button>
          </div>
          {view === 'form' ? (
            <ConfigForms text={text} onChange={setText} readOnly={!isDraft || !canChange} />
          ) : (
            <JsonEditor id="config-document" label="Document (JSON)" value={text} onChange={setText} readOnly={!isDraft || !canChange} />
          )}
          {isDraft && canChange && (
            <>
              {dirty && <p className="text-xs text-ink-soft">You have unsaved changes. Save before checking, previewing or submitting: those work on the saved draft.</p>}
              <FormError error={save.error ?? validate.error ?? preview.error ?? submit.error} details={detailsOf(save.error ?? validate.error ?? preview.error ?? submit.error)} />
              {validate.data?.valid && !dirty && (
                <p role="status" className="text-sm text-ink-soft">
                  No problems found.
                </p>
              )}
              {problems.length > 0 && (
                <div role="alert" className="rounded-md border border-risk-critical p-3 text-sm">
                  <p className="font-medium">The document has {problems.length === 1 ? 'a problem' : `${problems.length} problems`}:</p>
                  <ul className="mt-1 list-disc pl-5">
                    {problems.map((p) => (
                      <li key={p}>{p}</li>
                    ))}
                  </ul>
                </div>
              )}
              <div className="flex flex-wrap gap-2">
                <Button
                  variant="primary"
                  disabled={busy || invalid || !dirty}
                  onClick={() => {
                    validate.reset();
                    preview.reset();
                    save.mutate({ id, document: JSON.parse(text) });
                  }}
                >
                  {save.isPending ? 'Saving' : 'Save draft'}
                </Button>
                <Button disabled={busy || dirty || validate.isPending} onClick={() => validate.mutate(id)}>
                  {validate.isPending ? 'Checking' : 'Check'}
                </Button>
                <Button disabled={busy || dirty || preview.isPending} onClick={() => preview.mutate(id)}>
                  {preview.isPending ? 'Previewing' : 'Preview'}
                </Button>
                <Button disabled={busy || dirty} onClick={() => submit.mutate(id)}>
                  {submit.isPending ? 'Submitting' : 'Submit for approval'}
                </Button>
              </div>
            </>
          )}
        </div>
      </Card>

      {preview.data && <PreviewPanel preview={preview.data} />}
      <Changes version={version} text={text} />
      {version.review && <ReviewPanel review={version.review} unvalidated={!!version.unvalidated} />}
    </div>
  );
}

/** One version: its document and, for a draft, the editor with save, check, preview and submit (decision D58); for a version waiting, the approval. */
export function ConfigurationVersionPage() {
  const { versionId } = useParams();
  const version = useConfigVersion(versionId);
  return (
    <QuerySection query={version} label="the version">
      {() => (version.data ? <VersionBody key={`${version.data.id}:${version.data.status}:${version.data.payload?.length}`} version={version.data} /> : null)}
    </QuerySection>
  );
}
