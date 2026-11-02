import { Badge, type Tone } from '@/components/ui/Badge';
import { Card, CardHeader } from '@/components/ui/Card';
import { humanizeCode } from '@/utils/presentation';
import { diffDocuments, formatValue, summarizeChanges, type ConfigChange } from './diff';

const KIND: Record<ConfigChange['kind'], { label: string; tone: Tone }> = {
  added: { label: 'Added', tone: 'ok' },
  removed: { label: 'Removed', tone: 'critical' },
  changed: { label: 'Changed', tone: 'medium' },
};

/** What a draft changes against another version of the same layer, item by item (decision D58). */
export function ChangesPanel({ against, title, before, after }: { against: string; title?: string; before: unknown; after: unknown }) {
  const changes = diffDocuments(before, after);
  const summary = summarizeChanges(changes);
  return (
    <Card>
      <CardHeader
        title={title ?? 'What changes'}
        description={changes.length === 0 ? `Nothing differs from ${against}.` : `Compared with ${against}: ${summary.added} added, ${summary.removed} removed, ${summary.changed} changed, in ${summary.sections} ${summary.sections === 1 ? 'section' : 'sections'}.`}
      />
      {changes.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full min-w-max text-sm">
            <caption className="sr-only">Differences from {against}</caption>
            <thead>
              <tr className="border-b border-line text-left text-xs uppercase tracking-wide text-ink-faint">
                <th scope="col" className="px-5 py-2 font-medium">Section</th>
                <th scope="col" className="px-5 py-2 font-medium">Item</th>
                <th scope="col" className="px-5 py-2 font-medium">Change</th>
                <th scope="col" className="px-5 py-2 font-medium">Detail</th>
              </tr>
            </thead>
            <tbody>
              {changes.map((c) => (
                <tr key={`${c.section}/${c.item ?? ''}`} className="border-b border-line/60 align-top">
                  <th scope="row" className="px-5 py-2 text-left font-medium">{humanizeCode(c.section)}</th>
                  <td className="px-5 py-2 font-mono text-xs">{c.item ?? '(whole section)'}</td>
                  <td className="px-5 py-2">
                    <Badge tone={KIND[c.kind].tone}>{KIND[c.kind].label}</Badge>
                  </td>
                  <td className="px-5 py-2">
                    {c.fields.length === 0 ? (
                      <span className="text-ink-soft">{c.kind === 'added' ? 'a new item' : 'the item is gone'}</span>
                    ) : (
                      <ul className="space-y-0.5">
                        {c.fields.map((f) => (
                          <li key={f.path} className="font-mono text-xs">
                            {f.path && <span className="text-ink-soft">{f.path}: </span>}
                            {formatValue(f.before)} <span aria-hidden="true">&rarr;</span>
                            <span className="sr-only"> changes to </span> {formatValue(f.after)}
                          </li>
                        ))}
                      </ul>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}
