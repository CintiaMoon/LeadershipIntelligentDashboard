import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { isRecord, setIn, type Path } from './path';
import { QuestionsSection } from './QuestionsSection';
import { ImbalanceSection, PatternsSection, SignalsSection } from './RulesSections';
import { CoverageSection, OverallSection, PenaltiesSection, RecommendationsSection, TextsSection, type SectionProps } from './sections';

const SECTIONS: { id: string; label: string; Component: (p: SectionProps) => React.JSX.Element }[] = [
  { id: 'overall', label: 'Overall score', Component: OverallSection },
  { id: 'coverage', label: 'Coverage and group answers', Component: CoverageSection },
  { id: 'questions', label: 'Questions', Component: QuestionsSection },
  { id: 'signals', label: 'Signals', Component: SignalsSection },
  { id: 'patterns', label: 'Patterns', Component: PatternsSection },
  { id: 'imbalances', label: 'Imbalance', Component: ImbalanceSection },
  { id: 'penalties', label: 'Penalties', Component: PenaltiesSection },
  { id: 'recommendations', label: 'Advice', Component: RecommendationsSection },
  { id: 'texts', label: 'Display texts', Component: TextsSection },
];

/**
 * Forms for a configuration (decision D58, second and third steps). They edit the same document as the JSON
 * view: each change produces new JSON text for the page to hold. Adding or removing a pattern or an imbalance rule, changing
 * a question's kind and renaming an id are still done as JSON.
 */
export function ConfigForms({ text, onChange, readOnly }: { text: string; onChange: (text: string) => void; readOnly: boolean }) {
  const [section, setSection] = useState(SECTIONS[0]!.id);
  let doc: unknown;
  try {
    doc = JSON.parse(text);
  } catch {
    doc = undefined;
  }
  if (!isRecord(doc)) {
    return <p role="alert" className="text-sm text-risk-critical">The JSON view holds text that is not a valid document, so the forms cannot show it. Fix it there first.</p>;
  }
  const update = (change: (d: Record<string, unknown>) => Record<string, unknown>) => onChange(JSON.stringify(change(doc), null, 2));
  const set = (path: Path, value: unknown) => update((d) => setIn(d, path, value));
  const current = SECTIONS.find((s) => s.id === section) ?? SECTIONS[0]!;
  return (
    <div className="space-y-5">
      <div role="group" aria-label="Form sections" className="flex flex-wrap gap-2">
        {SECTIONS.map((s) => (
          <Button key={s.id} variant={s.id === current.id ? 'primary' : 'secondary'} aria-pressed={s.id === current.id} onClick={() => setSection(s.id)}>
            {s.label}
          </Button>
        ))}
      </div>
      <current.Component doc={doc} set={set} update={update} disabled={readOnly} />
    </div>
  );
}
