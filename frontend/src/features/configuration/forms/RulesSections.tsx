import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Field, inputClass, Switch } from '@/components/ui/Form';
import { humanizeCode } from '@/utils/presentation';
import { ConfigConditionEditor } from './ConfigConditionEditor';
import { describe } from './condition';
import { CheckGroup, SelectField } from './controls';
import { NumberField, TextField } from './NumberField';
import { isRecord, setIn, unsetIn } from './path';
import { DIMENSIONS, GAP_DIRECTIONS, PATTERN_CATEGORIES, questionIds, recommendationKeys, SEVERITIES, signalIds, usesOfSignal } from './refs';
import { unitProblem } from './checks';
import { Missing, type SectionProps } from './sections';

/** Signals: the named measures that patterns look at, each derived from one or more questions. */
export function SignalsSection(props: SectionProps) {
  const signals = props.doc.signals;
  const questions = questionIds(props.doc);
  const ids = signalIds(props.doc);
  const [newId, setNewId] = useState('');
  if (!Array.isArray(signals)) return <Missing what="signals" />;
  const idProblem = newId === '' ? ' ' : !/^[A-Z][A-Z0-9_]*$/.test(newId) ? 'Use capital letters, digits and underscores.' : ids.includes(newId) ? 'There is already a signal with this id.' : undefined;
  return (
    <div className="space-y-4">
      <p className="text-sm text-ink-soft">A signal is a number from 0 to 100 (100 desirable) derived from the questions you tick. Patterns look at signals, so changing one changes every pattern that uses it.</p>
      {(signals as Record<string, unknown>[]).map((s, i) => {
        const id = String(s.id);
        const blockers = usesOfSignal(props.doc, id);
        const sources = Array.isArray(s.sources) ? (s.sources as string[]) : [];
        return (
          <fieldset key={id} className="min-w-0 space-y-3 rounded-md border border-line p-4">
            <legend className="px-1 font-mono text-xs font-semibold">{id}</legend>
            <CheckGroup legend={`Questions behind ${id}`} options={questions} selected={sources} disabled={props.disabled} onChange={(next) => props.set(['signals', i, 'sources'], next)} />
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <TextField id={`sig-${i}-version`} label={`Version of the derivation (${id})`} value={String(s.derivationVersion ?? '')} disabled={props.disabled} onChange={(v) => props.set(['signals', i, 'derivationVersion'], v)} />
              {typeof s.reliability === 'number' && <NumberField id={`sig-${i}-reliability`} label={`Reliability (${id})`} step="0.05" value={s.reliability} error={unitProblem(s.reliability)} disabled={props.disabled} onChange={(v) => props.set(['signals', i, 'reliability'], v)} />}
            </div>
            {!props.disabled && (
              <div className="flex flex-wrap items-center gap-3 border-t border-line pt-3">
                <Button disabled={blockers.length > 0} aria-label={`Remove the signal ${id}`} onClick={() => props.update((d) => unsetIn(d, ['signals', i]))}>
                  Remove the signal
                </Button>
                {blockers.length > 0 && <p className="text-xs text-ink-soft">Still used by {blockers.join(', ')}. Remove those first.</p>}
              </div>
            )}
          </fieldset>
        );
      })}
      {!props.disabled && (
        <fieldset className="min-w-0 space-y-3 rounded-md border border-dashed border-line p-4">
          <legend className="px-1 text-sm font-semibold">Add a signal</legend>
          <div className="flex flex-wrap items-end gap-3">
            <Field id="new-signal-id" label="Signal id" error={newId === '' ? undefined : idProblem}>
              <input id="new-signal-id" className={inputClass} value={newId} onChange={(e) => setNewId(e.target.value.toUpperCase())} aria-invalid={!!idProblem && newId !== ''} aria-describedby="new-signal-id-error" />
            </Field>
            <Button
              variant="primary"
              disabled={!!idProblem}
              onClick={() => {
                props.update((d) => setIn(d, ['signals', signals.length], { id: newId, dataType: 'NUMBER', sources: questions.slice(0, 1), derivationVersion: '1' }));
                setNewId('');
              }}
            >
              Add signal
            </Button>
          </div>
        </fieldset>
      )}
    </div>
  );
}

/** Patterns: an organizational condition with its severity and the evidence needed before it counts. Adding or removing a pattern is done in JSON. */
export function PatternsSection(props: SectionProps) {
  const patterns = props.doc.patterns;
  if (!Array.isArray(patterns)) return <Missing what="patterns" />;
  const targets = { signals: signalIds(props.doc), questions: questionIds(props.doc), dimensions: DIMENSIONS };
  const advice = recommendationKeys(props.doc);
  return (
    <div className="space-y-3">
      <p className="text-sm text-ink-soft">A pattern is a condition, not a penalty: penalties are set under Penalties. Severity is how much it matters; the evidence settings say how sure the scoring must be before the pattern counts.</p>
      {(patterns as Record<string, unknown>[]).map((p, i) => {
        const id = String(p.id);
        const evidence = isRecord(p.evidence) ? p.evidence : {};
        const actions = Array.isArray(p.actions) ? (p.actions as string[]) : [];
        return (
          <details key={id} className="rounded-md border border-line">
            <summary className="cursor-pointer px-4 py-2 text-sm">
              <span className="font-semibold">{id}</span> {humanizeCode(String(p.code ?? ''))} <span className="text-ink-soft">({humanizeCode(String(p.severity))}, {p.enabled === true ? 'on' : 'off'})</span>
              <span className="block text-xs text-ink-soft">{describe(p.condition)}</span>
            </summary>
            <div className="space-y-4 border-t border-line p-4">
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                <SelectField id={`pat-${i}-category`} label={`Category (${id})`} value={String(p.category ?? '')} options={PATTERN_CATEGORIES} disabled={props.disabled} onChange={(v) => props.set(['patterns', i, 'category'], v)} />
                <SelectField id={`pat-${i}-severity`} label={`Severity (${id})`} value={String(p.severity ?? '')} options={SEVERITIES} disabled={props.disabled} onChange={(v) => props.set(['patterns', i, 'severity'], v)} />
                <NumberField id={`pat-${i}-signals`} label={`Fewest signals with data (${id})`} step="1" value={evidence.minimumSignals as number} disabled={props.disabled} onChange={(v) => props.set(['patterns', i, 'evidence', 'minimumSignals'], v)} />
                <NumberField id={`pat-${i}-confidence`} label={`Least confidence (${id})`} step="0.05" value={evidence.minimumConfidence as number} error={unitProblem(evidence.minimumConfidence)} disabled={props.disabled} onChange={(v) => props.set(['patterns', i, 'evidence', 'minimumConfidence'], v)} />
              </div>
              <div className="flex flex-wrap gap-6">
                <Switch label={`Enabled (${id})`} checked={p.enabled === true} disabled={props.disabled} onChange={(v) => props.set(['patterns', i, 'enabled'], v)} />
                <Switch label={`May lead to a penalty (${id})`} checked={p.penaltyEligible === true} disabled={props.disabled} onChange={(v) => props.set(['patterns', i, 'penaltyEligible'], v)} />
              </div>
              <CheckGroup legend={`Advice given with ${id}`} options={advice} selected={actions} disabled={props.disabled} onChange={(next) => props.set(['patterns', i, 'actions'], next)} />
              <div className="space-y-2">
                <p className="text-sm font-medium">Condition</p>
                {isRecord(p.condition) ? (
                  <ConfigConditionEditor value={p.condition} targets={targets} idPrefix={`pat-${i}-cond`} disabled={props.disabled} onChange={(next) => props.set(['patterns', i, 'condition'], next)} />
                ) : (
                  <p className="text-xs text-ink-soft">This pattern has no condition in the form's shape; edit it in the JSON view.</p>
                )}
              </div>
            </div>
          </details>
        );
      })}
    </div>
  );
}

/** Imbalance rules: a flag when two dimensions are far apart. */
export function ImbalanceSection(props: SectionProps) {
  const rules = props.doc.imbalances;
  if (!Array.isArray(rules)) return <Missing what="imbalance rules" />;
  return (
    <div className="space-y-4">
      <p className="text-sm text-ink-soft">An imbalance is a gap between two dimension scores. It is reported and advised on; it does not by itself lower a score.</p>
      {(rules as Record<string, unknown>[]).map((r, i) => {
        const id = String(r.id);
        return (
          <fieldset key={id} className="min-w-0 space-y-3 rounded-md border border-line p-4">
            <legend className="px-1 text-sm font-semibold">
              {id} <span className="font-normal text-ink-soft">{humanizeCode(String(r.left))} against {humanizeCode(String(r.right))}</span>
            </legend>
            <Switch label={`Enabled (${id})`} checked={r.enabled === true} disabled={props.disabled} onChange={(v) => props.set(['imbalances', i, 'enabled'], v)} />
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              <SelectField id={`imb-${i}-left`} label={`First dimension (${id})`} value={String(r.left ?? '')} options={DIMENSIONS} disabled={props.disabled} onChange={(v) => props.set(['imbalances', i, 'left'], v)} />
              <SelectField id={`imb-${i}-right`} label={`Second dimension (${id})`} value={String(r.right ?? '')} options={DIMENSIONS} disabled={props.disabled} onChange={(v) => props.set(['imbalances', i, 'right'], v)} />
              <SelectField id={`imb-${i}-direction`} label={`Gap measured as (${id})`} value={String(r.direction ?? '')} options={GAP_DIRECTIONS} words={{ LEFT_MINUS_RIGHT: 'First minus second', RIGHT_MINUS_LEFT: 'Second minus first' }} disabled={props.disabled} onChange={(v) => props.set(['imbalances', i, 'direction'], v)} />
              <SelectField id={`imb-${i}-operator`} label={`Flag when the gap is (${id})`} value={String(r.operator ?? '')} options={['GAP_GT', 'GAP_GTE']} words={{ GAP_GT: 'Above', GAP_GTE: 'At least' }} disabled={props.disabled} onChange={(v) => props.set(['imbalances', i, 'operator'], v)} />
              <NumberField id={`imb-${i}-threshold`} label={`Points (${id})`} value={r.threshold as number} disabled={props.disabled} onChange={(v) => props.set(['imbalances', i, 'threshold'], v)} />
              <SelectField id={`imb-${i}-severity`} label={`Severity (${id})`} value={String(r.severity ?? '')} options={SEVERITIES} disabled={props.disabled} onChange={(v) => props.set(['imbalances', i, 'severity'], v)} />
            </div>
          </fieldset>
        );
      })}
    </div>
  );
}
