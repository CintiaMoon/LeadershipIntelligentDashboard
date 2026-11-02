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

/** A copy of the document without the key at the path (or the item of an array); a path that does not exist changes nothing. */
export function unsetIn<T>(doc: T, path: Path): T {
  if (path.length === 0 || getIn(doc, path.slice(0, -1)) === undefined) return doc;
  const parent = getIn(doc, path.slice(0, -1));
  const last = path[path.length - 1] as string | number;
  if (parent === null || typeof parent !== 'object') return doc;
  const copy = Array.isArray(parent) ? (parent as unknown[]).filter((_, i) => i !== last) : Object.fromEntries(Object.entries(parent).filter(([k]) => k !== String(last)));
  return setIn(doc, path.slice(0, -1), copy);
}
