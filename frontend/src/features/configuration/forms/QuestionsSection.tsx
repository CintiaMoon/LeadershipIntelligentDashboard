import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Field, inputClass, selectClass, Switch } from '@/components/ui/Form';
import { humanizeCode } from '@/utils/presentation';
import { SelectField } from './controls';
import { NumberField } from './NumberField';
import { getIn, isRecord, setIn, unsetIn } from './path';
import { DIMENSIONS, DIRECTIONS, MISSING_POLICIES, nextId, OUT_OF_RANGE, questionIds, UNKNOWN_OPTION, usesOfQuestion } from './refs';
import { Missing, type SectionProps } from './sections';

const TEMPLATES: Record<string, { type: string; normalization: Record<string, unknown> }> = {
  SCALE: { type: 'SCALE', normalization: { min: 1, max: 5, direction: 'POSITIVE', outOfRangePolicy: 'REJECT', allowDecimals: false } },
  NUMBER: { type: 'NUMBER', normalization: { min: 0, max: 10, direction: 'NEGATIVE', outOfRangePolicy: 'CLAMP' } },
  BOOLEAN: { type: 'BOOLEAN', normalization: { trueValue: 100, falseValue: 0 } },
  MULTIPLE_CHOICE: { type: 'MULTIPLE_CHOICE', normalization: { values: { OPTION_A: 100, OPTION_B: 0 }, unknownOption: 'REJECT' } },
};
const TYPE_WORDS: Record<string, string> = { SCALE: 'Scale', NUMBER: 'Number', BOOLEAN: 'Yes or no', MULTIPLE_CHOICE: 'Choice' };
const ID_FORMAT = /^[A-Za-z][A-Za-z0-9_]*$/;

function Normalization({ index, q, props }: { index: number; q: Record<string, unknown>; props: SectionProps }) {
  const n = isRecord(q.normalization) ? q.normalization : {};
  const base = ['questions', index, 'normalization'];
  const id = `q-${index}`;
  const num = (key: string, label: string, step = 'any') => (
    <NumberField id={`${id}-${key}`} label={`${label} (${String(q.id)})`} step={step} value={n[key] as number} disabled={props.disabled} onChange={(v) => props.set([...base, key], v)} />
  );
  switch (q.type) {
    case 'SCALE':
    case 'NUMBER':
      return (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {num('min', 'Lowest answer')}
          {num('max', 'Highest answer')}
          <SelectField id={`${id}-direction`} label={`Higher answer is (${String(q.id)})`} value={String(n.direction ?? '')} options={DIRECTIONS} words={{ POSITIVE: 'Better', NEGATIVE: 'Worse' }} disabled={props.disabled} onChange={(v) => props.set([...base, 'direction'], v)} />
          <SelectField id={`${id}-range`} label={`Answer out of range (${String(q.id)})`} value={String(n.outOfRangePolicy ?? '')} options={OUT_OF_RANGE} words={{ REJECT: 'Rejected', CLAMP: 'Pulled into range' }} disabled={props.disabled} onChange={(v) => props.set([...base, 'outOfRangePolicy'], v)} />
          {q.type === 'SCALE' && typeof n.allowDecimals === 'boolean' && (
            <Switch label={`Decimals allowed (${String(q.id)})`} checked={n.allowDecimals} disabled={props.disabled} onChange={(v) => props.set([...base, 'allowDecimals'], v)} />
          )}
        </div>
      );
    case 'BOOLEAN':
      return (
        <div className="grid gap-3 sm:grid-cols-2">
          {num('trueValue', 'Score for yes')}
          {num('falseValue', 'Score for no')}
        </div>
      );
    case 'MULTIPLE_CHOICE':
    case 'MULTI_SELECT':
      return <Choices index={index} q={q} n={n} props={props} />;
    default:
      return <p className="text-xs text-ink-soft">The normalization of a {humanizeCode(String(q.type))} question is edited in the JSON view.</p>;
  }
}

function Choices({ index, q, n, props }: { index: number; q: Record<string, unknown>; n: Record<string, unknown>; props: SectionProps }) {
  const values = isRecord(n.values) ? n.values : {};
  const [option, setOption] = useState('');
  const base = ['questions', index, 'normalization', 'values'];
  const clean = option.trim().toUpperCase().replace(/[^A-Z0-9]+/g, '_');
  const taken = Object.keys(values).includes(clean);
  return (
    <div className="space-y-3">
      <p className="text-sm font-medium">Score of each choice (0 to 100, 100 is desirable)</p>
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {Object.entries(values).map(([key, score]) => (
          <div key={key} className="flex items-end gap-2">
            <NumberField id={`q-${index}-opt-${key}`} label={`${humanizeCode(key)} (${String(q.id)})`} value={score as number} disabled={props.disabled} onChange={(v) => props.set([...base, key], v)} className="min-w-0 flex-1" />
            {!props.disabled && Object.keys(values).length > 2 && (
              <Button variant="ghost" aria-label={`Remove choice ${humanizeCode(key)} of ${String(q.id)}`} onClick={() => props.set(base, unsetIn({ values }, ['values', key]).values)}>
                Remove
              </Button>
            )}
          </div>
        ))}
      </div>
      {!props.disabled && (
        <div className="flex flex-wrap items-end gap-2">
          <Field id={`q-${index}-newopt`} label={`New choice (${String(q.id)})`}>
            <input id={`q-${index}-newopt`} className={inputClass} value={option} onChange={(e) => setOption(e.target.value)} />
          </Field>
          <Button disabled={clean === '' || taken} onClick={() => { props.set(base, { ...values, [clean]: 50 }); setOption(''); }}>
            Add choice
          </Button>
        </div>
      )}
      <SelectField id={`q-${index}-unknown`} label={`An answer that is not a choice (${String(q.id)})`} value={String(n.unknownOption ?? '')} options={UNKNOWN_OPTION} words={{ REJECT: 'Rejected', TREAT_AS_MISSING: 'Treated as not answered' }} disabled={props.disabled} onChange={(v) => props.set(['questions', index, 'normalization', 'unknownOption'], v)} className="max-w-sm" />
    </div>
  );
}

/** Questions: how an answer becomes a 0 to 100 value, what happens without one, and which dimensions the question feeds. */
export function QuestionsSection(props: SectionProps) {
  const questions = props.doc.questions;
  const mappings = (getIn(props.doc, ['dimensions', 'mappings']) as Record<string, unknown>[] | undefined) ?? [];
  const ids = questionIds(props.doc);
  const [newId, setNewId] = useState(() => nextId('Q', ids));
  const [newType, setNewType] = useState('SCALE');
  if (!Array.isArray(questions)) return <Missing what="questions" />;
  const idProblem = !ID_FORMAT.test(newId) ? 'Use letters, digits and underscores, starting with a letter.' : ids.includes(newId) ? 'There is already a question with this id.' : undefined;

  return (
    <div className="space-y-4">
      <p className="text-sm text-ink-soft">The question text and wording shown to people belong to the questionnaire; this is how a stored answer is scored. Answers are normalized to 0 to 100 where 100 is desirable.</p>
      {(questions as Record<string, unknown>[]).map((q, i) => {
        const id = String(q.id);
        const own = mappings.map((m, at) => ({ m, at })).filter(({ m }) => m.question === id);
        const blockers = usesOfQuestion(props.doc, id).filter((u) => !u.startsWith('a dimension mapping'));
        return (
          <fieldset key={id} className="min-w-0 space-y-3 rounded-md border border-line p-4">
            <legend className="px-1 text-sm font-semibold">
              {id} <span className="font-normal text-ink-soft">{TYPE_WORDS[String(q.type)] ?? humanizeCode(String(q.type))}</span>
            </legend>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <SelectField id={`q-${i}-missing`} label={`When not answered (${id})`} value={String(q.missingPolicy ?? '')} options={MISSING_POLICIES} disabled={props.disabled} onChange={(v) => props.set(['questions', i, 'missingPolicy'], v)} />
              <Field id={`q-${i}-aggregation`} label={`Group answers combined by (${id})`}>
                <select id={`q-${i}-aggregation`} className={selectClass} disabled={props.disabled} value={String(q.aggregation ?? '')} onChange={(e) => props.update((d) => (e.target.value === '' ? unsetIn(d, ['questions', i, 'aggregation']) : setIn(d, ['questions', i, 'aggregation'], e.target.value)))}>
                  <option value="">The default</option>
                  <option value="MEAN">Mean</option>
                  <option value="MEDIAN">Median</option>
                </select>
              </Field>
            </div>
            <Normalization index={i} q={q} props={props} />
            <div className="space-y-2">
              <p className="text-sm font-medium">Feeds these dimensions</p>
              {own.length === 0 && <p className="text-xs text-ink-soft">This question feeds no dimension, so it does not affect any score.</p>}
              {own.map(({ m, at }) => (
                <div key={at} className="flex flex-wrap items-end gap-2">
                  <SelectField id={`q-${i}-map-${at}-dim`} label={`Dimension (${id})`} value={String(m.dimension ?? '')} options={DIMENSIONS} disabled={props.disabled} onChange={(v) => props.set(['dimensions', 'mappings', at, 'dimension'], v)} />
                  <NumberField id={`q-${i}-map-${at}-weight`} label={`Weight (${id}, ${humanizeCode(String(m.dimension))})`} value={m.weight as number} disabled={props.disabled} onChange={(v) => props.set(['dimensions', 'mappings', at, 'weight'], v)} />
                  {!props.disabled && (
                    <Button variant="ghost" aria-label={`Remove ${id} from ${humanizeCode(String(m.dimension))}`} onClick={() => props.update((d) => unsetIn(d, ['dimensions', 'mappings', at]))}>
                      Remove
                    </Button>
                  )}
                </div>
              ))}
              {!props.disabled && (
                <Button
                  aria-label={`Add a dimension to ${id}`}
                  onClick={() => props.update((d) => setIn(d, ['dimensions', 'mappings', mappings.length], { question: id, dimension: DIMENSIONS.find((x) => !own.some(({ m }) => m.dimension === x)) ?? DIMENSIONS[0], weight: 1 }))}
                >
                  Add a dimension
                </Button>
              )}
            </div>
            {!props.disabled && (
              <div className="flex flex-wrap items-center gap-3 border-t border-line pt-3">
                <Button
                  disabled={blockers.length > 0}
                  aria-label={`Remove the question ${id}`}
                  onClick={() => props.update((d) => unsetIn(setIn(d, ['dimensions', 'mappings'], mappings.filter((m) => m.question !== id)), ['questions', i]))}
                >
                  Remove the question
                </Button>
                {blockers.length > 0 && <p className="text-xs text-ink-soft">Still used by {blockers.join(', ')}. Remove those first.</p>}
              </div>
            )}
          </fieldset>
        );
      })}
      {!props.disabled && (
        <fieldset className="min-w-0 space-y-3 rounded-md border border-dashed border-line p-4">
          <legend className="px-1 text-sm font-semibold">Add a question</legend>
          <div className="flex flex-wrap items-end gap-3">
            <Field id="new-question-id" label="Question id" error={idProblem}>
              <input id="new-question-id" className={inputClass} value={newId} onChange={(e) => setNewId(e.target.value)} aria-invalid={!!idProblem} aria-describedby="new-question-id-error" />
            </Field>
            <SelectField id="new-question-type" label="Kind" value={newType} options={Object.keys(TEMPLATES)} words={TYPE_WORDS} onChange={setNewType} />
            <Button
              variant="primary"
              disabled={!!idProblem}
              onClick={() => {
                props.update((d) => setIn(d, ['questions', questions.length], { id: newId, ...TEMPLATES[newType]!, missingPolicy: 'IGNORE' }));
                setNewId(nextId('Q', [...ids, newId]));
              }}
            >
              Add question
            </Button>
          </div>
          <p className="text-xs text-ink-soft">A new question uses starting values; change them above, then add it to a dimension and, if it should matter to a pattern, to a signal. Its display text is added under Display texts.</p>
        </fieldset>
      )}
    </div>
  );
}
