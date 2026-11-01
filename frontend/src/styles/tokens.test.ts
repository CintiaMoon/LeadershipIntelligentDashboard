import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { chartColors } from '../components/charts/palette';

// read from disk (the tests run from the frontend folder): the test runner replaces CSS imports with an empty module
const css = readFileSync('src/index.css', 'utf8');

/**
 * The design tokens are the only place colours are written (decision D51). These tests keep both themes complete and keep every
 * text and background pair readable (WCAG AA, 4.5:1), so a token edit that breaks contrast fails here rather than in a review.
 */

function declarations(block: string): Record<string, string> {
  const result: Record<string, string> = {};
  for (const match of block.matchAll(/(--[a-z0-9-]+):\s*([^;]+);/g)) {
    result[match[1]!] = match[2]!.trim();
  }
  return result;
}

function blockOf(start: RegExp): string {
  const begin = css.search(start);
  expect(begin, `block ${start} exists`).toBeGreaterThanOrEqual(0);
  const open = css.indexOf('{', begin);
  let depth = 0;
  for (let i = open; i < css.length; i++) {
    if (css[i] === '{') depth++;
    if (css[i] === '}' && --depth === 0) return css.slice(open + 1, i);
  }
  throw new Error('unbalanced braces');
}

const dark = { ...declarations(blockOf(/@theme\s*\{/)), ...declarations(blockOf(/^:root\s*\{/m)) };
const light = { ...dark, ...declarations(blockOf(/:root\[data-theme="light"\]/)) };
const lightOnly = declarations(blockOf(/:root\[data-theme="light"\]/));

function luminance(hex: string): number {
  const channel = (i: number) => {
    const c = parseInt(hex.slice(1 + i * 2, 3 + i * 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * channel(0) + 0.7152 * channel(1) + 0.0722 * channel(2);
}

function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi! + 0.05) / (lo! + 0.05);
}

// [text token, background token]: every pairing a component can produce
const PAIRS: [string, string][] = [
  ['ink', 'canvas'], ['ink', 'surface'], ['ink', 'sidebar'],
  ['ink-soft', 'canvas'], ['ink-soft', 'surface'], ['ink-soft', 'sidebar'],
  ['ink-faint', 'canvas'], ['ink-faint', 'surface'], ['ink-faint', 'sidebar'],
  ['accent-ink', 'accent-soft'], ['accent-ink', 'surface'], ['accent', 'surface'], ['accent', 'canvas'], ['on-accent', 'accent'],
  ['canvas', 'ink'],
  ['risk-low', 'risk-low-soft'], ['risk-medium', 'risk-medium-soft'], ['risk-high', 'risk-high-soft'],
  ['risk-critical', 'risk-critical-soft'], ['ok', 'ok-soft'],
];

describe('design tokens', () => {
  it('defines every colour token for the light theme as well as the dark one', () => {
    const colourTokens = Object.keys(dark).filter((k) => k.startsWith('--color-') || k.startsWith('--chart-'));
    expect(colourTokens.length).toBeGreaterThan(30);
    expect(Object.keys(lightOnly).filter((k) => k.startsWith('--color-') || k.startsWith('--chart-')).sort()).toEqual(colourTokens.sort());
  });

  for (const [name, theme] of [['dark', dark], ['light', light]] as const) {
    it(`keeps every text and background pair readable in the ${name} theme`, () => {
      for (const [text, background] of PAIRS) {
        const ratio = contrast(theme[`--color-${text}`]!, theme[`--color-${background}`]!);
        expect(ratio, `${text} on ${background} in ${name}`).toBeGreaterThanOrEqual(4.5);
      }
    });

    it(`keeps chart axis text and the series lines visible on the ${name} surface`, () => {
      expect(contrast(theme['--chart-axis']!, theme['--color-surface']!), 'axis text').toBeGreaterThanOrEqual(4.5);
      for (const key of ['score', 'team-health', 'delivery-stability', 'technical-risk', 'leadership-load', 'critical', 'high', 'medium', 'low']) {
        expect(contrast(theme[`--chart-${key}`]!, theme['--color-surface']!), `chart ${key}`).toBeGreaterThanOrEqual(3);
      }
    });
  }

  it('makes the charts follow the theme through custom properties, never raw colours', () => {
    const values = [chartColors.score, chartColors.grid, chartColors.axis, chartColors.deduction, chartColors.suppressed,
      ...Object.values(chartColors.dimensions), ...Object.values(chartColors.severity)];
    for (const value of values) {
      expect(value).toMatch(/^var\(--chart-[a-z-]+\)$/);
      expect(dark[value.slice(4, -1)], `${value} is defined`).toBeDefined();
    }
  });
});

describe('components use the tokens only', () => {
  const sources = import.meta.glob(['../**/*.tsx', '../**/*.ts', '!../**/*.test.ts', '!../**/*.test.tsx', '!../tests/**', '!../types/api.d.ts'], {
    query: '?raw',
    import: 'default',
    eager: true,
  }) as Record<string, string>;

  it('writes no colour literal and no stock palette class outside the tokens', () => {
    const offenders: string[] = [];
    for (const [file, text] of Object.entries(sources)) {
      const stripped = text.replace(/\/\*[\s\S]*?\*\//g, '').replace(/(^|\s)\/\/.*$/gm, '$1');
      if (/#[0-9a-fA-F]{3,8}\b/.test(stripped) && !/(\d)#/.test(stripped) && /['"`]#[0-9a-fA-F]{3,8}['"`]/.test(stripped)) offenders.push(`${file}: hex colour`);
      if (/\b(?:bg|text|border|ring|fill|stroke|from|to|via)-(?:white|black|slate|gray|zinc|neutral|stone|red|orange|amber|yellow|lime|green|emerald|teal|cyan|sky|blue|indigo|violet|purple|fuchsia|pink|rose)(?:-\d{2,3})?\b/.test(stripped)) {
        offenders.push(`${file}: stock palette class`);
      }
      if (/\brgba?\(/.test(stripped)) offenders.push(`${file}: rgb literal`);
    }
    expect(offenders).toEqual([]);
  });
});
