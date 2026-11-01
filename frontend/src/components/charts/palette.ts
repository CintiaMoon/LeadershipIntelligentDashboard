// Chart colours (same hues as the design tokens in index.css). Charts also use dashes, labels and text tables, so colour
// never carries the meaning alone.
export const chartColors = {
  score: '#28457f',
  grid: '#e3e6ec',
  axis: '#6b7789',
  dimensions: { TEAM_HEALTH: '#28457f', DELIVERY_STABILITY: '#1f6b4a', TECHNICAL_RISK: '#b04a00', LEADERSHIP_LOAD: '#7a4fa3' } as Record<string, string>,
  severity: { CRITICAL: '#a61b1b', HIGH: '#b04a00', MEDIUM: '#c99a1a', LOW: '#8b9bb0' } as Record<string, string>,
  deduction: '#b04a00',
  suppressed: '#c99a1a',
};
