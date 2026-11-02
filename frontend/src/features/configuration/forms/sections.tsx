import { Field, selectClass, Switch } from '@/components/ui/Form';
import { humanizeCode } from '@/utils/presentation';
import { bandsProblem, unitProblem, weightsProblem } from './checks';
import { NumberField, TextField } from './NumberField';
import { getIn, isRecord, type Path } from './path';

export interface SectionProps {
  doc: Record<string, unknown>;
  set: (path: Path, value: unknown) => void;
  disabled: boolean;
}

/** A configuration key (baseDeduction, MAX_PER_CATEGORY) written as words. */
export const keyLabel = (key: string) => humanizeCode(key.replace(/([a-z0-9])([A-Z])/g, '$1_$2'));

export function Missing({ what }: { what: string }) {
  return <p className="text-sm text-ink-soft">This version has no {what} of its own, so it uses the layer below. Add the section in the JSON view if this layer should change it.</p>;
}

const Problem = ({ id, text }: { id: string; text: string | undefined }) =>
  text ? (
    <p id={id} role="alert" className="text-sm text-risk-critical">
      {text}
    </p>
  ) : null;

function Bands({ title, path, props }: { title: string; path: Path; props: SectionProps }) {
  const bands = getIn(props.doc, path);
  if (!Array.isArray(bands)) return null;
  const id = path.join('-');
  return (
    <fieldset className="min-w-0 space-y-3">
      <legend className="text-sm font-semibold">{title}</legend>
      <div className="grid gap-3 sm:grid-cols-2">
        {(bands as Record<string, unknown>[]).map((b, i) => (
          <div key={String(b.label)} className="grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-3 rounded-md border border-line p-3">
            <p className="col-span-2 text-sm font-medium">{humanizeCode(String(b.label))}</p>
            <NumberField id={`${id}-${i}-min`} label="From" value={b.min as number} disabled={props.disabled} onChange={(v) => props.set([...path, i, 'min'], v)} />
            <NumberField id={`${id}-${i}-max`} label="Up to" value={b.max as number} disabled={props.disabled} onChange={(v) => props.set([...path, i, 'max'], v)} />
          </div>
        ))}
      </div>
      <Problem id={`${id}-problem`} text={bandsProblem(bands)} />
    </fieldset>
  );
}

/** Overall score: the weight of each dimension, the rounding tolerance and the score bands (specv3 Sections 17 and 18). */
export function OverallSection(props: SectionProps) {
  const overall = props.doc.overall;
  if (!isRecord(overall)) return <Missing what="overall score settings" />;
  const weights = overall.weights;
  return (
    <div className="space-y-6">
      {isRecord(weights) && (
        <fieldset className="min-w-0 space-y-3">
          <legend className="text-sm font-semibold">Weight of each dimension</legend>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {Object.entries(weights).map(([dimension, weight]) => (
              <NumberField key={dimension} id={`weight-${dimension}`} label={humanizeCode(dimension)} step="0.01" value={weight as number} disabled={props.disabled} onChange={(v) => props.set(['overall', 'weights', dimension], v)} />
            ))}
          </div>
          <p className="text-sm text-ink-soft">Together: {Number((Object.values(weights) as number[]).reduce((a, b) => a + (typeof b === 'number' ? b : 0), 0).toFixed(6))} (must be 1)</p>
          <Problem id="weights-problem" text={weightsProblem(weights, overall.tolerance)} />
        </fieldset>
      )}
      {typeof overall.tolerance === 'number' && (
        <NumberField id="tolerance" label="Rounding tolerance for the weights" value={overall.tolerance} disabled={props.disabled} onChange={(v) => props.set(['overall', 'tolerance'], v)} help="How far from 1 the weights may add up." className="max-w-xs" />
      )}
      <Bands title="Bands of the overall score" path={['overall', 'lisInterpretation']} props={props} />
      <Bands title="Bands of a dimension score" path={['overall', 'dimensionInterpretation']} props={props} />
    </div>
  );
}

/** How much of a dimension must be answered before it is scored, and how answers from several people are combined. */
export function CoverageSection(props: SectionProps) {
  const settings = getIn(props.doc, ['dimensions', 'settings']);
  const responses = props.doc.responses;
  if (!isRecord(settings) && !isRecord(responses)) return <Missing what="coverage or group-answer settings" />;
  return (
    <div className="space-y-6">
      {isRecord(settings) && (
        <fieldset className="min-w-0 space-y-3">
          <legend className="text-sm font-semibold">Required coverage of each dimension</legend>
          <p className="text-sm text-ink-soft">The share of a dimension's weight that must be answered; below it the dimension is not scored (insufficient data), never guessed.</p>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            {Object.entries(settings).map(([dimension, value]) => {
              const coverage = isRecord(value) ? (value.requiredCoverage as number | undefined) : undefined;
              return (
                <NumberField key={dimension} id={`coverage-${dimension}`} label={humanizeCode(dimension)} step="0.05" value={coverage} disabled={props.disabled} error={unitProblem(coverage)} onChange={(v) => props.set(['dimensions', 'settings', dimension, 'requiredCoverage'], v)} />
              );
            })}
          </div>
        </fieldset>
      )}
      {isRecord(responses) && (
        <fieldset className="min-w-0 space-y-3">
          <legend className="text-sm font-semibold">Answers from several people</legend>
          <p className="text-sm text-ink-soft">Members are never shown one by one; their answers are combined per question once enough have answered.</p>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Field id="responses-method" label="Combine by">
              <select id="responses-method" disabled={props.disabled} className={selectClass} value={String(responses.defaultMethod ?? '')} onChange={(e) => props.set(['responses', 'defaultMethod'], e.target.value)}>
                <option value="MEDIAN">Median</option>
                <option value="MEAN">Mean</option>
              </select>
            </Field>
            <NumberField id="responses-min" label="Fewest answers" step="1" value={responses.minResponses as number} disabled={props.disabled} onChange={(v) => props.set(['responses', 'minResponses'], v)} />
            <NumberField id="responses-share" label="Share needed for a signal" step="0.05" value={responses.signalShare as number} disabled={props.disabled} error={unitProblem(responses.signalShare)} onChange={(v) => props.set(['responses', 'signalShare'], v)} />
            <NumberField id="responses-full" label="Answers for full confidence" step="1" value={responses.fullConfidenceResponses as number} disabled={props.disabled} onChange={(v) => props.set(['responses', 'fullConfidenceResponses'], v)} />
          </div>
        </fieldset>
      )}
    </div>
  );
}

const STACKING = ['STACK', 'MAX', 'MAX_PER_CATEGORY', 'PRIORITY', 'CAPPED_STACK', 'EXCLUSIVE_GROUP'];

/** The numbers of a strategy, whatever its kind: plain numbers and groups of numbers (such as the severity multipliers). */
function StrategyFields({ index, strategy, props }: { index: number; strategy: Record<string, unknown>; props: SectionProps }) {
  const base: Path = ['penalties', index, 'strategy'];
  return (
    <>
      {Object.entries(strategy)
        .filter(([key]) => key !== 'type')
        .flatMap(([key, value]) => {
          if (typeof value === 'number') {
            return [<NumberField key={key} id={`pen-${index}-${key}`} label={keyLabel(key)} value={value} disabled={props.disabled} onChange={(v) => props.set([...base, key], v)} />];
          }
          if (isRecord(value) && Object.values(value).every((x) => typeof x === 'number')) {
            return Object.entries(value).map(([sub, n]) => (
              <NumberField key={`${key}.${sub}`} id={`pen-${index}-${key}-${sub}`} label={`${keyLabel(key)}: ${humanizeCode(sub)}`} step="0.05" value={n as number} disabled={props.disabled} onChange={(v) => props.set([...base, key, sub], v)} />
            ));
          }
          return [
            <p key={key} className="text-xs text-ink-soft sm:col-span-2">
              {keyLabel(key)} is edited in the JSON view.
            </p>,
          ];
        })}
    </>
  );
}

/** Penalty amounts, caps and stacking. A penalty is a consequence of a pattern; its pattern and target are changed in the JSON view. */
export function PenaltiesSection(props: SectionProps) {
  const penalties = props.doc.penalties;
  if (!Array.isArray(penalties)) return <Missing what="penalties" />;
  return (
    <div className="space-y-4">
      <p className="text-sm text-ink-soft">Deductions are unvalidated until a calibration reference is stated. No score can fall below 0 and no deduction is applied twice.</p>
      {(penalties as Record<string, unknown>[]).map((p, i) => {
        const strategy = isRecord(p.strategy) ? p.strategy : {};
        const trigger = isRecord(p.trigger) ? String(p.trigger.pattern ?? '') : '';
        return (
          <fieldset key={String(p.id)} className="min-w-0 space-y-3 rounded-md border border-line p-4">
            <legend className="px-1 text-sm font-semibold">
              {String(p.id)} <span className="font-normal text-ink-soft">{humanizeCode(String(p.code ?? ''))}</span>
            </legend>
            <p className="text-xs text-ink-soft">
              Applies to {humanizeCode(String(p.target ?? ''))} when pattern {trigger} is detected; {humanizeCode(String(strategy.type ?? ''))}.
            </p>
            <Switch label={`Enabled (${String(p.id)})`} checked={p.enabled === true} disabled={props.disabled} onChange={(v) => props.set(['penalties', i, 'enabled'], v)} />
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <StrategyFields index={i} strategy={strategy} props={props} />
              <NumberField id={`pen-${i}-max`} label="Largest single deduction" value={p.maximumDeduction as number} disabled={props.disabled} onChange={(v) => props.set(['penalties', i, 'maximumDeduction'], v)} />
              <Field id={`pen-${i}-stacking`} label="Stacking">
                <select id={`pen-${i}-stacking`} disabled={props.disabled} className={selectClass} value={String(p.stackingPolicy ?? '')} onChange={(e) => props.set(['penalties', i, 'stackingPolicy'], e.target.value)}>
                  {STACKING.map((s) => (
                    <option key={s} value={s}>
                      {humanizeCode(s)}
                    </option>
                  ))}
                </select>
              </Field>
              {typeof p.maximumCumulativeDeduction === 'number' && (
                <NumberField id={`pen-${i}-cap`} label="Cap on the total" value={p.maximumCumulativeDeduction} disabled={props.disabled} onChange={(v) => props.set(['penalties', i, 'maximumCumulativeDeduction'], v)} />
              )}
              <NumberField id={`pen-${i}-priority`} label="Priority" step="1" value={p.priority as number} disabled={props.disabled} onChange={(v) => props.set(['penalties', i, 'priority'], v)} />
            </div>
          </fieldset>
        );
      })}
    </div>
  );
}

/** The advice shown with a finding. */
export function RecommendationsSection(props: SectionProps) {
  const list = props.doc.recommendations;
  if (!Array.isArray(list)) return <Missing what="recommendations" />;
  return (
    <div className="space-y-4">
      {(list as Record<string, unknown>[]).map((r, i) => (
        <fieldset key={String(r.key)} className="min-w-0 space-y-3 rounded-md border border-line p-4">
          <legend className="px-1 font-mono text-xs">{String(r.key)}</legend>
          <TextField id={`rec-${i}-title`} label={`Title (${String(r.key)})`} value={r.title as string} disabled={props.disabled} onChange={(v) => props.set(['recommendations', i, 'title'], v)} />
          <TextField id={`rec-${i}-description`} label={`Advice (${String(r.key)})`} multiline value={r.description as string} disabled={props.disabled} onChange={(v) => props.set(['recommendations', i, 'description'], v)} />
          <NumberField id={`rec-${i}-priority`} label={`Priority (${String(r.key)})`} step="1" value={r.priority as number} disabled={props.disabled} onChange={(v) => props.set(['recommendations', i, 'priority'], v)} className="max-w-xs" />
        </fieldset>
      ))}
    </div>
  );
}

const TEXT_SECTIONS: [string, string][] = [
  ['dimensions', 'Dimensions'],
  ['patterns', 'Patterns'],
  ['penalties', 'Penalties'],
  ['signals', 'Signals'],
  ['questions', 'Questions'],
];

/** The words people read: names, descriptions and explanations of everything the scoring shows. */
export function TextsSection(props: SectionProps) {
  const presentation = props.doc.presentation;
  if (!isRecord(presentation)) return <Missing what="display texts" />;
  return (
    <div className="space-y-3">
      <p className="text-sm text-ink-soft">Explanations describe working conditions, never individuals. A number written into an explanation does not follow the settings; check it after changing one.</p>
      {TEXT_SECTIONS.filter(([key]) => isRecord(presentation[key])).map(([key, title]) => (
        <details key={key} className="rounded-md border border-line">
          <summary className="cursor-pointer px-4 py-2 text-sm font-semibold">
            {title} ({Object.keys(presentation[key] as object).length})
          </summary>
          <div className="space-y-4 border-t border-line p-4">
            {Object.entries(presentation[key] as Record<string, unknown>).map(([id, entry]) => (
              <fieldset key={id} className="min-w-0 space-y-3">
                <legend className="font-mono text-xs">{id}</legend>
                {isRecord(entry) &&
                  Object.entries(entry)
                    .filter(([, v]) => typeof v === 'string')
                    .map(([field, v]) => (
                      <TextField key={field} id={`text-${key}-${id}-${field}`} label={`${keyLabel(field)} (${id})`} multiline={field !== 'name'} value={v as string} disabled={props.disabled} onChange={(next) => props.set(['presentation', key, id, field], next)} />
                    ))}
              </fieldset>
            ))}
          </div>
        </details>
      ))}
    </div>
  );
}
