// Chart colours. They are CSS custom properties defined in index.css for both themes, so charts follow the theme without
// being redrawn. Charts also use dashes, labels and text tables, so colour never carries the meaning alone.
const v = (name: string) => `var(--chart-${name})`;

export const chartColors = {
  score: v('score'),
  grid: v('grid'),
  axis: v('axis'),
  dimensions: {
    TEAM_HEALTH: v('team-health'),
    DELIVERY_STABILITY: v('delivery-stability'),
    TECHNICAL_RISK: v('technical-risk'),
    LEADERSHIP_LOAD: v('leadership-load'),
  } as Record<string, string>,
  severity: { CRITICAL: v('critical'), HIGH: v('high'), MEDIUM: v('medium'), LOW: v('low') } as Record<string, string>,
  deduction: v('deduction'),
  suppressed: v('suppressed'),
};
