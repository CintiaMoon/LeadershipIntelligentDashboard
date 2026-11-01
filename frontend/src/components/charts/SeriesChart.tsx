import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { chartColors } from './palette';

export interface Series {
  key: string;
  name: string;
  color: string;
  dash?: string;
}

export interface SeriesRow {
  label: string;
  [key: string]: string | number | null;
}

interface Props {
  title: string;
  summary: string;
  rows: SeriesRow[];
  series: Series[];
  kind: 'line' | 'bar' | 'stacked-bar';
  /** x labels where the configuration changed: drawn as a reference line */
  markers?: string[];
  domain?: [number, number];
  format: (value: number | null) => string;
}

/**
 * A titled chart with a text alternative and a data table (frontend-spec 20): the figure shows the shape, the table
 * gives the exact values the backend returned. No value is calculated here.
 */
export function SeriesChart({ title, summary, rows, series, kind, markers = [], domain, format }: Props) {
  return (
    <figure className="space-y-3">
      <figcaption className="text-sm font-medium text-ink">{title}</figcaption>
      <p className="text-xs text-ink-soft">{summary}</p>
      <div aria-hidden="true" className="h-56 w-full">
        <ResponsiveContainer width="100%" height="100%">
          {kind === 'line' ? (
            <LineChart accessibilityLayer={false} data={rows} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
              <CartesianGrid stroke={chartColors.grid} vertical={false} />
              <XAxis dataKey="label" interval="preserveStartEnd" tick={{ fontSize: 11, fill: chartColors.axis }} />
              <YAxis domain={domain} tick={{ fontSize: 11, fill: chartColors.axis }} width={36} />
              <Tooltip formatter={(v) => format(typeof v === 'number' ? v : null)} />
              <Legend wrapperStyle={{ fontSize: 12 }} />
              {markers.map((m) => (
                <ReferenceLine key={m} x={m} stroke={chartColors.axis} strokeDasharray="4 3" label={{ value: 'Configuration changed', fontSize: 10, fill: chartColors.axis }} />
              ))}
              {series.map((s) => (
                <Line key={s.key} type="monotone" dataKey={s.key} name={s.name} stroke={s.color} strokeWidth={2} strokeDasharray={s.dash} dot={{ r: 3 }} isAnimationActive={false} connectNulls />
              ))}
            </LineChart>
          ) : (
            <BarChart accessibilityLayer={false} data={rows} margin={{ top: 8, right: 12, bottom: 0, left: 0 }}>
              <CartesianGrid stroke={chartColors.grid} vertical={false} />
              <XAxis dataKey="label" interval="preserveStartEnd" tick={{ fontSize: 11, fill: chartColors.axis }} />
              <YAxis allowDecimals={false} tick={{ fontSize: 11, fill: chartColors.axis }} width={36} />
              <Tooltip formatter={(v) => format(typeof v === 'number' ? v : null)} />
              <Legend wrapperStyle={{ fontSize: 12 }} />
              {series.map((s) => (
                <Bar key={s.key} dataKey={s.key} name={s.name} fill={s.color} stackId={kind === 'stacked-bar' ? 'stack' : undefined} isAnimationActive={false} />
              ))}
            </BarChart>
          )}
        </ResponsiveContainer>
      </div>
      <details className="text-sm">
        <summary className="cursor-pointer text-ink-soft hover:text-ink">View the data</summary>
        <div className="mt-2 overflow-x-auto">
          <table className="w-full min-w-max text-left text-xs">
            <caption className="sr-only">{title}</caption>
            <thead>
              <tr className="border-b border-line text-ink-soft">
                <th scope="col" className="py-1 pr-4 font-medium">
                  Assessment
                </th>
                {series.map((s) => (
                  <th key={s.key} scope="col" className="py-1 pr-4 font-medium">
                    {s.name}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.label} className="border-b border-line/60">
                  <th scope="row" className="py-1 pr-4 font-normal">
                    {row.label}
                  </th>
                  {series.map((s) => (
                    <td key={s.key} className="py-1 pr-4 tabular-nums">
                      {format(typeof row[s.key] === 'number' ? (row[s.key] as number) : null)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </figure>
  );
}
