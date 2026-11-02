/**
 * Comparison of two configuration documents for a reviewer (decision D58). Lists are compared item by item through the item's
 * identifier, so adding a pattern or changing one threshold shows as exactly that and not as a changed line of text; everything else
 * is compared field by field with its JSON path. Pure and independent of the screen. It compares documents as they are, so it shows
 * what a person wrote; whether the result is valid is the server's business.
 */

export interface FieldChange {
  path: string;
  before: unknown;
  after: unknown;
}

export interface ConfigChange {
  /** The top-level section: questions, patterns, overall, ... */
  section: string;
  /** The item of a list section (a question id, a pattern id, ...), or null for the section as a whole. */
  item: string | null;
  kind: 'added' | 'removed' | 'changed';
  fields: FieldChange[];
}

type Plain = Record<string, unknown>;

const isObject = (v: unknown): v is Plain => typeof v === 'object' && v !== null && !Array.isArray(v);

/** How an item of a list is identified: a catalog id, a recommendation key, a mapping's question and dimension, or an interpretation band. */
function identity(item: unknown): string | undefined {
  if (!isObject(item)) return undefined;
  if (typeof item['id'] === 'string') return item['id'];
  if (typeof item['key'] === 'string') return item['key'];
  if (typeof item['question'] === 'string' && typeof item['dimension'] === 'string') return `${item['question']} / ${item['dimension']}`;
  if (typeof item['label'] === 'string') return item['label'];
  return undefined;
}

function keyed(list: unknown[]): Map<string, unknown> | null {
  const map = new Map<string, unknown>();
  for (const item of list) {
    const id = identity(item);
    if (id === undefined || map.has(id)) return null;
    map.set(id, item);
  }
  return map;
}

export function deepEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true;
  if (Array.isArray(a) && Array.isArray(b)) return a.length === b.length && a.every((x, i) => deepEqual(x, b[i]));
  if (isObject(a) && isObject(b)) {
    const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
    return [...keys].every((k) => deepEqual(a[k], b[k]));
  }
  return false;
}

/** The leaves that differ between two values, with their paths. Lists that have identifiers are matched by them. */
function leaves(before: unknown, after: unknown, path: string, out: FieldChange[]): void {
  if (deepEqual(before, after)) return;
  if (isObject(before) && isObject(after)) {
    for (const key of [...new Set([...Object.keys(before), ...Object.keys(after)])].sort()) {
      leaves(before[key], after[key], path ? `${path}.${key}` : key, out);
    }
    return;
  }
  if (Array.isArray(before) && Array.isArray(after)) {
    const a = keyed(before);
    const b = keyed(after);
    if (a && b) {
      for (const id of [...new Set([...a.keys(), ...b.keys()])].sort()) {
        leaves(a.get(id), b.get(id), `${path}[${id}]`, out);
      }
      return;
    }
  }
  out.push({ path, before, after });
}

const KEYED_SECTIONS = new Set(['questions', 'signals', 'patterns', 'penalties', 'imbalances', 'recommendations']);

export function diffDocuments(before: unknown, after: unknown): ConfigChange[] {
  const a: Plain = isObject(before) ? before : {};
  const b: Plain = isObject(after) ? after : {};
  const changes: ConfigChange[] = [];
  for (const section of [...new Set([...Object.keys(a), ...Object.keys(b)])].sort()) {
    const x = a[section];
    const y = b[section];
    if (deepEqual(x, y)) continue;
    const listX = Array.isArray(x) ? keyed(x) : x === undefined ? new Map() : null;
    const listY = Array.isArray(y) ? keyed(y) : y === undefined ? new Map() : null;
    if (KEYED_SECTIONS.has(section) && listX && listY) {
      for (const id of [...new Set([...listX.keys(), ...listY.keys()])].sort()) {
        const was = listX.get(id);
        const now = listY.get(id);
        if (deepEqual(was, now)) continue;
        if (was === undefined) {
          changes.push({ section, item: id, kind: 'added', fields: [] });
        } else if (now === undefined) {
          changes.push({ section, item: id, kind: 'removed', fields: [] });
        } else {
          const fields: FieldChange[] = [];
          leaves(was, now, '', fields);
          changes.push({ section, item: id, kind: 'changed', fields });
        }
      }
      continue;
    }
    const fields: FieldChange[] = [];
    leaves(x, y, '', fields);
    changes.push({ section, item: null, kind: x === undefined ? 'added' : y === undefined ? 'removed' : 'changed', fields: fields.length > 0 ? fields : [{ path: '', before: x, after: y }] });
  }
  return changes;
}

/** A value for one line of a table: short, never multi-line. */
export function formatValue(value: unknown, max = 80): string {
  if (value === undefined) return '(not set)';
  const text = typeof value === 'string' ? JSON.stringify(value) : JSON.stringify(value) ?? String(value);
  return text.length > max ? `${text.slice(0, max - 1)}…` : text;
}

/** How many items and sections changed, for a heading above the comparison. */
export function summarizeChanges(changes: ConfigChange[]): { sections: number; added: number; removed: number; changed: number } {
  return {
    sections: new Set(changes.map((c) => c.section)).size,
    added: changes.filter((c) => c.kind === 'added').length,
    removed: changes.filter((c) => c.kind === 'removed').length,
    changed: changes.filter((c) => c.kind === 'changed').length,
  };
}

/** Parses an editor's text, reporting where it fails; null when it is valid JSON. */
export function jsonProblem(text: string): string | null {
  try {
    const parsed: unknown = JSON.parse(text);
    return isObject(parsed) ? null : 'A configuration is a JSON object, between { and }.';
  } catch (e) {
    return e instanceof Error ? e.message : 'The text is not valid JSON.';
  }
}
