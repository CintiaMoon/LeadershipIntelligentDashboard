import { jsonProblem } from './diff';

/**
 * A plain editor for a JSON document (decision D58, version 1): a monospace text area that says as you type whether the text is
 * valid JSON and where it is not. It checks nothing about the configuration itself; the server does that on save, validate and
 * submit, and the problems it finds are shown by the page.
 */
export function JsonEditor({ id, label, value, onChange, readOnly = false, rows = 22 }: { id: string; label: string; value: string; onChange: (text: string) => void; readOnly?: boolean; rows?: number }) {
  const problem = value.trim() === '' ? 'The document is empty.' : jsonProblem(value);
  const lines = value === '' ? 0 : value.split('\n').length;
  return (
    <div>
      <label htmlFor={id} className="text-sm font-medium">
        {label}
      </label>
      <textarea
        id={id}
        spellCheck={false}
        autoCapitalize="off"
        autoCorrect="off"
        rows={rows}
        value={value}
        readOnly={readOnly}
        aria-invalid={problem !== null}
        aria-describedby={`${id}-status`}
        onChange={(e) => onChange(e.target.value)}
        className="mt-1 block w-full min-w-0 rounded-md border border-line bg-canvas px-3 py-2 font-mono text-xs leading-5 text-ink aria-[invalid=true]:border-risk-critical"
      />
      <p id={`${id}-status`} role="status" className={`mt-1 text-xs ${problem ? 'text-risk-critical' : 'text-ink-soft'}`}>
        {problem ?? `Valid JSON, ${lines} ${lines === 1 ? 'line' : 'lines'}.`}
      </p>
    </div>
  );
}
