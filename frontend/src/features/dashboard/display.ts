import { useMemo } from 'react';
import type { RunCatalog } from '@/types/api';
import { humanizeCode } from '@/utils/presentation';

export interface Display {
  name: string;
  description?: string;
  explanation?: string;
}

type Entries = Record<string, { name?: string; description?: string; explanation?: string }> | undefined;

function lookup(entries: Entries, id: string, fallback: string): Display {
  const entry = entries?.[id];
  return { name: entry?.name || fallback, description: entry?.description || undefined, explanation: entry?.explanation || undefined };
}

/** Names and descriptions come from the backend catalog of the run; the code is shown (humanized) only when it has none. */
export interface Names {
  pattern: (id: string, code?: string) => Display;
  penalty: (id: string, code?: string) => Display;
  dimension: (key: string) => Display;
  signal: (id: string) => Display;
  question: (id: string) => Display;
}

export function makeNames(catalog: RunCatalog | undefined): Names {
  return {
    pattern: (id, code) => lookup(catalog?.patterns, id, humanizeCode(code ?? id)),
    penalty: (id, code) => lookup(catalog?.penalties, id, humanizeCode(code ?? id)),
    dimension: (key) => lookup(catalog?.dimensions, key, humanizeCode(key)),
    signal: (id) => lookup(catalog?.signals, id, humanizeCode(id)),
    question: (id) => lookup(catalog?.questions, id, id),
  };
}

export const useNames = (catalog: RunCatalog | undefined): Names => useMemo(() => makeNames(catalog), [catalog]);
