import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { isRecord, setIn, type Path } from './path';
import { CoverageSection, OverallSection, PenaltiesSection, RecommendationsSection, TextsSection, type SectionProps } from './sections';

const SECTIONS: { id: string; label: string; Component: (p: SectionProps) => React.JSX.Element }[] = [
  { id: 'overall', label: 'Overall score', Component: OverallSection },
  { id: 'coverage', label: 'Coverage and group answers', Component: CoverageSection },
  { id: 'penalties', label: 'Penalties', Component: PenaltiesSection },
  { id: 'recommendations', label: 'Advice', Component: RecommendationsSection },
  { id: 'texts', label: 'Display texts', Component: TextsSection },
];

/**
 * Forms for the numbers and the wording of a configuration (decision D58, second step). They edit the same document as the JSON
 * view: each change produces new JSON text for the page to hold. Questions, signals, pattern conditions and imbalance rules have
 * no form yet and are edited as JSON.
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
  const set = (path: Path, value: unknown) => onChange(JSON.stringify(setIn(doc, path, value), null, 2));
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
      <current.Component doc={doc} set={set} disabled={readOnly} />
    </div>
  );
}
