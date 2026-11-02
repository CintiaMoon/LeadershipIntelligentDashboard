export type Path = (string | number)[];

/** The value at a path of a parsed document, or undefined when any step is missing. */
export function getIn(doc: unknown, path: Path): unknown {
  let at: unknown = doc;
  for (const step of path) {
    if (at === null || typeof at !== 'object') return undefined;
    at = (at as Record<string | number, unknown>)[step];
  }
  return at;
}

/** A copy of the document with the value at the path replaced; nothing is mutated and untouched parts are shared. */
export function setIn<T>(doc: T, path: Path, value: unknown): T {
  if (path.length === 0) return value as T;
  const [head, ...rest] = path as [string | number, ...Path];
  const base = (doc !== null && typeof doc === 'object' ? doc : typeof head === 'number' ? [] : {}) as Record<string | number, unknown>;
  const next = Array.isArray(base) ? [...base] : { ...base };
  (next as Record<string | number, unknown>)[head] = setIn(base[head], rest, value);
  return next as T;
}

export const isRecord = (v: unknown): v is Record<string, unknown> => v !== null && typeof v === 'object' && !Array.isArray(v);
