import { Line, LineChart, ResponsiveContainer, YAxis } from 'recharts';
import { chartColors } from './palette';

/** A tiny trend line of values the backend returned. {@code summary} is the text alternative. */
export function Sparkline({ values, summary, color = chartColors.score }: { values: (number | null | undefined)[]; summary: string; color?: string }) {
  const data = values.map((v, i) => ({ i, v: v ?? null }));
  if (data.filter((d) => d.v !== null).length < 2) {
    return <p className="text-xs text-ink-faint">Trend appears after two assessments.</p>;
  }
  return (
    <figure>
      <div aria-hidden="true" className="h-10 w-full">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart accessibilityLayer={false} data={data} margin={{ top: 4, right: 4, bottom: 4, left: 4 }}>
            <YAxis hide domain={[0, 100]} />
            <Line type="monotone" dataKey="v" stroke={color} strokeWidth={2} dot={false} isAnimationActive={false} connectNulls />
          </LineChart>
        </ResponsiveContainer>
      </div>
      <figcaption className="sr-only">{summary}</figcaption>
    </figure>
  );
}
