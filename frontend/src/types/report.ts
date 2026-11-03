import { z } from 'zod';
import { UnexpectedResponseError } from './views';

// A report is a list of titled tables built by the backend from a stored run (decisions D59 to D64). The page draws it as
// it is: it never computes or reinterprets a figure.
const sectionSchema = z.object({
  id: z.string(),
  heading: z.string(),
  note: z.string().nullish(),
  columns: z.array(z.string()),
  rows: z.array(z.array(z.string())),
});

export const reportSchema = z.object({
  type: z.string(),
  title: z.string(),
  redacted: z.boolean(),
  notice: z.string(),
  sections: z.array(sectionSchema),
});

export type Report = z.infer<typeof reportSchema>;
export type ReportSection = z.infer<typeof sectionSchema>;

export const REPORT_TYPES = [
  { type: 'LEADERSHIP_INTELLIGENCE', label: 'Leadership Intelligence', description: 'The score, dimensions, patterns, penalties, imbalances, evidence, advice and method of one assessment.' },
  { type: 'DIMENSION_ANALYSIS', label: 'Dimension Analysis', description: 'Each dimension, and what every question contributed to it.' },
  { type: 'PATTERN_PENALTY', label: 'Pattern and Penalty', description: 'Every pattern and what happened to every penalty.' },
  { type: 'HISTORICAL_COMPARISON', label: 'Historical Comparison', description: 'Two assessments side by side, when they can be compared.' },
] as const;

export type ReportType = (typeof REPORT_TYPES)[number]['type'];

export const isReportType = (value: string | null): value is ReportType => REPORT_TYPES.some((t) => t.type === value);

export function parseReport(value: unknown): Report {
  const parsed = reportSchema.safeParse(value);
  if (!parsed.success) throw new UnexpectedResponseError('report');
  return parsed.data;
}
