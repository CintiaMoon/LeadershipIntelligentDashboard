# Component Inventory

**Source:** FE §7-§17. Status for all rows: NOT_STARTED. "Blocked" lists the backend dependency from `API_DEPENDENCIES.md` or `ARCHITECTURE_GAPS.md`.
Components are driven by typed contracts and contain no scoring rules (FE §17.1).

## 1. Feature components

| Component | Feature | Route / use | Displays (key points) | Data source | Blocked by |
|---|---|---|---|---|---|
| `LeadershipIntelligenceScoreCard` | scoring | `/dashboard`, score detail | LIS, label, previous and change, date, assessment ref, config version, engine version | run / dashboard | previous/change: F5 |
| `DimensionScoreGrid` / `DimensionScoreCard` | dashboard, scoring | `/dashboard` | four cards: score, label, trend, pattern count, applied penalty count | run | label F10, trend F5 |
| `PatternSummary` | dashboard | `/dashboard` | totals, by severity and dimension, new and resolved | dashboard | F5 |
| `PriorityRecommendations` | dashboard | `/dashboard` | title, description, dimension, pattern, priority, evidence, next action | dashboard | F6 |
| `ScoreCompositionChart` | scoring | score detail | raw, adjusted, weight, weighted contribution, overall penalties | run | C13 |
| `ScoreCalculationExplanation` | scoring | score detail | six expandable steps from backend trace | explanation | F15 |
| `DimensionContributionTable` | scoring | dimension pages | signal, normalized value, weight, contribution, source, evidence ref | contributions | D15 |
| `DimensionPatternList` | scoring | dimension pages | code, name, severity, confidence, evidence count, eligibility, applied, actions | patterns | none |
| `DimensionPenaltyList` | scoring | dimension pages | code, trigger, strategy, calculated and effective deduction, policy, status | penalties | F9 |
| `PatternExplorer` | patterns | `/patterns` | filters and sort per FE §10.1 | patterns (org-wide) | list endpoint and params |
| Pattern detail page | patterns | `/patterns/:patternMatchId` | identification, detection, evidence, scoring impact, actions | pattern detail | detail endpoint; LIS impact F11 |
| Cross-dimensional pattern view | patterns | pattern pages | distinct visual, affected dimensions, relationships | patterns | none |
| `PenaltyExplorer` | penalties | `/penalties` | summary and filters per FE §11 | penalties (org-wide) | list endpoint |
| Penalty detail page | penalties | `/penalties/:penaltyApplicationId` | candidate vs effective, cap, policy, group, related, suppression reason | penalty detail | detail endpoint |
| `DimensionImbalanceChart` | imbalances | imbalances route | dimensions, gap, severity, threshold, explanation | imbalances | confidence/evidence F12 |
| `LISHistoryChart` | trends | `/trends` | LIS over time, config version, events | history | F5 |
| `DimensionTrendChart` | trends | `/trends` | selectable dimensions, raw and adjusted | history | F5 |
| `PatternTrendChart` | trends | `/trends` | new, persistent, resolved, recurring | history | F5 |
| Reports pages | reports | `/reports` | five report types, PDF/CSV | reports | F13 |
| Assessment pages | assessments | `/assessments/**` | list, status, results, scoring action | assessments | G1 |
| `RecalculateAction` | assessments | assessment results | confirmation dialog, new run notice | recalculate | needs permission (G3) |
| Settings pages | settings | `/settings` | no content defined | none | no spec |

## 2. Reusable components (FE §17.1)

`ScoreCard`, `ScoreGauge`, `ScoreBadge`, `SeverityBadge`, `ConfidenceIndicator`, `DimensionCard`, `PatternCard`, `PenaltyCard`, `EvidenceList`, `EvidenceDetail`, `ImbalanceCard`, `TrendChart`, `EmptyState`, `LoadingState`, `ErrorState`, `ConfirmationDialog`, `FilterPanel`, `DataTable`, `VersionInformation`.

Rules:
- `SeverityBadge` and `ConfidenceIndicator` are independent (FE §6.3, §6.4). Severity: LOW neutral, MEDIUM yellow, HIGH orange, CRITICAL red. Never encode meaning by color alone.
- `ScoreBadge` renders the backend label; the FE band table (FE §6.2) is only a color default, never a classifier.
- `PenaltyCard` shows four states only (D7) and always shows suppressed ones.
- `TrendChart` and any chart provide a text summary (FE §20).
- `VersionInformation` shows engine, configuration, pattern/penalty versions and hash.

## 3. Shared infrastructure

API client and generated types; `services/auth`; `services/query`; error boundary and HTTP error mapping; `formatScore`; permission guard; organization selector (`stores`); stale-data banner; layout and navigation filtered by role.

## 4. Planned folder structure

Per FE §17: `src/app`, `src/features/{dashboard,assessments,scoring,patterns,penalties,imbalances,trends,reports,settings}`, `src/shared/{components,hooks,types,utils}`, `src/services/{api,auth,query}`, `src/stores`, `src/styles`.
