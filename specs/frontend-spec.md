# Frontend Specification — Leadership Intelligence Scoring Engine

**Document type:** Technical Product & Frontend Specification  
**Version:** 1.0  
**Status:** Proposed  
**Product:** Leadership Intelligence Platform  
**Primary application:** Leadership Intelligence Dashboard

---

## 1. Purpose

Define the frontend architecture, user experience, components, data contracts, and implementation requirements for visualizing and interacting with the Leadership Intelligence Scoring Engine.

The frontend must make complex organizational scoring understandable, actionable, and trustworthy.

Users must be able to:

- Understand the organization's overall Leadership Intelligence Score (LIS).
- Explore the four scoring dimensions.
- Understand how each dimension contributes to the overall score.
- Identify detected organizational patterns.
- Understand applied, suppressed, and ineligible penalties.
- Inspect the evidence supporting each result.
- Explore imbalances between dimensions.
- Compare scoring runs over time.
- Understand recommendations and potential areas for improvement.
- Distinguish measured evidence from interpretation and recommendations.

The interface must not make users feel that the platform is evaluating individual employees or assigning performance ratings to leaders.

## 2. Product principles

### 2.1 Explainability first

Every score must be explainable.

Users should be able to navigate from:

`Leadership Intelligence Score → Dimension → Pattern → Evidence → Scoring impact`

Every displayed score must have a traceable source in the backend response.

### 2.2 No black-box scoring

The interface must never display a penalty, pattern, or score without enough information to explain its origin.

### 2.3 Organizational perspective

The platform evaluates organizational conditions, engineering operating models, and leadership systems.

It must not present results as individual employee performance evaluations.

### 2.4 Evidence before interpretation

The UI must distinguish:

- Observed inputs.
- Normalized signals.
- Detected patterns.
- Calculated penalties.
- Scoring interpretations.
- Recommended actions.

### 2.5 Backend is the source of truth

The frontend must never:

- Calculate LIS.
- Recalculate dimension scores.
- Apply penalties.
- Infer pattern matches.
- Reconstruct scoring rules.
- Change historical scoring results.

All scoring calculations must come from backend APIs.

The frontend may calculate purely presentational values, such as chart dimensions, percentages of a displayed value, and visual positioning.

### 2.6 Avoid misleading precision

Display the precision returned by the backend. Do not introduce additional decimal places or imply that a score has greater statistical certainty than its underlying evidence supports.

---

## 3. Technology stack

Use the following stack unless the existing repository already contains compatible alternatives.

| Area | Technology |
|---|---|
| Framework | React |
| Language | TypeScript |
| Build | Vite |
| Styling | Tailwind CSS |
| Routing | React Router |
| Server state | TanStack Query |
| Client state | Zustand |
| Forms | React Hook Form |
| Validation | Zod |
| Charts | Recharts |
| Icons | Lucide |
| Unit testing | Vitest |
| Component testing | React Testing Library |
| E2E testing | Playwright |
| API client | Typed REST client |
| API documentation | OpenAPI-generated types where available |

### 3.1 Architectural principles

- Feature-oriented folder structure.
- Reusable UI components.
- Strong TypeScript typing.
- Separation between API, domain presentation, and UI.
- Centralized error handling.
- Centralized authorization handling.
- Server state managed by TanStack Query.
- Local interaction state managed by component state or Zustand.
- No duplicated backend business logic.

---

## 4. Application navigation

The main application navigation must contain:

1. Dashboard
2. Assessments
3. Leadership Intelligence
4. Patterns & Risks
5. Trends
6. Reports
7. Settings

The available sections must depend on the authenticated user's permissions.

### 4.1 Navigation structure

```text
Application
├── Dashboard
├── Assessments
│   ├── Assessment List
│   ├── Assessment Details
│   └── Assessment Results
├── Leadership Intelligence
│   ├── Score Overview
│   ├── Dimension Analysis
│   ├── Score Explanation
│   └── Historical Results
├── Patterns & Risks
│   ├── Detected Patterns
│   ├── Pattern Details
│   ├── Applied Penalties
│   └── Suppressed Penalties
├── Trends
│   ├── LIS Evolution
│   ├── Dimension Evolution
│   └── Pattern Evolution
├── Reports
└── Settings
```

The frontend must not expose configuration-management functionality to users who do not have the required permissions.

---

## 5. User roles and permissions

The UI must respect backend authorization.

Suggested roles:

| Role | Capabilities |
|---|---|
| OWNER | Full organization access and configuration management |
| ADMIN | Organization administration and assessment management |
| ENGINEERING_LEADER | View scores, patterns, evidence, and reports |
| ANALYST | Read-only analytical access |
| VIEWER | Restricted read-only access |

These roles are presentation-level assumptions. The backend authorization model remains authoritative.

The frontend must:

- Hide unauthorized navigation items.
- Protect routes.
- Hide unauthorized actions.
- Handle HTTP 401 and 403 responses.
- Never rely on frontend permissions as a security boundary.

---

## 6. Design system and visual language

The interface should communicate intelligence, analytical depth, reliability, and clarity.

Avoid the appearance of a traditional HR evaluation dashboard.

### 6.1 Design direction

Use:

- Clean enterprise SaaS aesthetics.
- Strong visual hierarchy.
- Generous spacing.
- Neutral backgrounds.
- Clear typography.
- Subtle borders and shadows.
- Consistent data visualization.
- Carefully controlled accent colors.

### 6.2 Score visualization

All scores use a 0–100 scale.

Suggested visual interpretation:

| Score range | Label | Suggested color |
|---|---|---|
| 85–100 | Strong | Green |
| 70–84 | Healthy | Teal |
| 50–69 | Needs Attention | Amber |
| 30–49 | At Risk | Orange |
| 0–29 | Critical | Red |

These thresholds are presentation defaults only. The backend must provide authoritative interpretation labels when configured.

If the backend returns different ranges or labels, the frontend must display those values.

Never derive a score classification independently when the backend already provides one.

### 6.3 Severity visualization

| Severity | Visual treatment |
|---|---|
| LOW | Neutral |
| MEDIUM | Yellow |
| HIGH | Orange |
| CRITICAL | Red |

Severity must not be confused with confidence.

### 6.4 Confidence visualization

Display confidence independently from severity.

Suggested labels:

- Insufficient evidence
- Weak confidence
- Moderate confidence
- Strong confidence

Use the exact backend-provided confidence classification whenever available.

---

## 7. Main dashboard

Route:

`/dashboard`

The dashboard provides an executive overview of the organization's engineering health.

### 7.1 Dashboard layout

```text
------------------------------------------------------
Organization Selector                 User Profile
------------------------------------------------------
Navigation
------------------------------------------------------
Dashboard

Leadership Intelligence Score
[ Overall Score ] [ Assessment Status ] [ Last Updated ]

------------------------------------------------------
Dimension Overview

[ Team Health ] [ Delivery Stability ]
[ Technical Risk ] [ Leadership Load ]

------------------------------------------------------
Organizational Patterns

[ Critical Patterns ] [ High Severity Patterns ]
[ Total Detected Patterns ]

------------------------------------------------------
Scoring Impact

[ Applied Penalties ] [ Suppressed Penalties ]

------------------------------------------------------
Historical Evolution

[ LIS Trend Chart ]

------------------------------------------------------
Recommended Areas of Attention

[ Priority 1 ]
[ Priority 2 ]
[ Priority 3 ]
------------------------------------------------------
```

### 7.2 LIS summary component

Component:

`LeadershipIntelligenceScoreCard`

Display:

- LIS value.
- Interpretation label.
- Score status.
- Previous score, if comparable historical data exists.
- Change from previous score.
- Last scoring date.
- Assessment reference.
- Scoring configuration version.
- Engine version, available in detailed information.

The score must be visually prominent.

Example:

```text
Leadership Intelligence Score

                 72
              HEALTHY

        +4 compared to previous assessment

Last calculated: October 2026
```

The comparison must use the backend's comparison result.

Do not compare scores from incompatible scoring configuration versions unless the backend explicitly indicates that the comparison is valid.

### 7.3 Dimension overview

Component:

`DimensionScoreGrid`

Display four dimension cards.

Each card must include:

- Dimension name.
- Score.
- Interpretation.
- Trend indicator, when available.
- Number of detected patterns.
- Number of applied penalties.
- Navigation to detailed analysis.

The four dimensions are:

1. Team Health
2. Delivery Stability
3. Technical Risk
4. Leadership Load

Use consistent visual representations for all four dimensions.

### 7.4 Pattern summary

Component:

`PatternSummary`

Display:

- Total detected patterns.
- Patterns by severity.
- Patterns by dimension.
- Newly detected patterns.
- Previously detected patterns that are no longer present.

Pattern lifecycle and historical comparison must be based on backend data.

### 7.5 Recommended areas of attention

Component:

`PriorityRecommendations`

Display recommendations returned by the backend.

Each recommendation must contain:

- Title.
- Description.
- Related dimension.
- Related pattern.
- Priority.
- Supporting evidence.
- Suggested next action.

The frontend must not generate recommendations from raw scores.

---

## 8. Leadership Intelligence Score detail

Route:

`/leadership-intelligence/:scoringRunId`

This is the main analytical screen.

### 8.1 Overview

Display:

- LIS.
- Overall interpretation.
- Dimension scores.
- Dimension weights.
- Overall penalties.
- Scoring timestamp.
- Assessment reference.
- Engine version.
- Configuration version.

### 8.2 Score composition

Component:

`ScoreCompositionChart`

Display the contribution of each dimension to the LIS.

The visualization must distinguish:

- Raw dimension score.
- Effective dimension score after dimension penalties.
- Dimension weight.
- Weighted contribution.
- Overall penalties.

The frontend must use backend-provided values for all score calculations.

Example:

| Dimension | Raw score | Adjusted score | Weight |
|---|---:|---:|---:|
| Team Health | 82 | 76 | 30% |
| Delivery Stability | 75 | 70 | 30% |
| Technical Risk | 68 | 65 | 25% |
| Leadership Load | 80 | 76 | 15% |

The table is illustrative. Actual values must come from the API.

### 8.3 Score calculation explanation

Component:

`ScoreCalculationExplanation`

Provide a step-by-step explanation:

1. Raw dimension scores.
2. Dimension penalties.
3. Adjusted dimension scores.
4. Weighted dimension contributions.
5. Overall penalties.
6. Final LIS.

Display the calculation trace returned by the backend.

Users must be able to expand each step to inspect its details.

Do not reconstruct the calculation using frontend formulas.

---

## 9. Dimension analysis

Routes:

- `/leadership-intelligence/:scoringRunId/dimensions/team-health`
- `/leadership-intelligence/:scoringRunId/dimensions/delivery-stability`
- `/leadership-intelligence/:scoringRunId/dimensions/technical-risk`
- `/leadership-intelligence/:scoringRunId/dimensions/leadership-load`

Each dimension page must follow the same layout.

### 9.1 Dimension header

Display:

- Dimension name.
- Score.
- Interpretation.
- Raw score.
- Adjusted score.
- Weight in LIS.
- Applied penalty total.
- Trend, if available.

### 9.2 Dimension contribution breakdown

Component:

`DimensionContributionTable`

Columns:

- Question or signal.
- Normalized value.
- Weight.
- Contribution.
- Source.
- Evidence reference.

Allow users to expand individual rows.

The backend must provide the contribution details.

### 9.3 Dimension patterns

Component:

`DimensionPatternList`

Display patterns associated with the selected dimension.

Each pattern card must show:

- Pattern code.
- Pattern name.
- Description.
- Severity.
- Confidence.
- Evidence count.
- Penalty eligibility.
- Whether a penalty was applied.
- Recommended actions.

Clicking a pattern opens its detail view.

### 9.4 Dimension penalties

Component:

`DimensionPenaltyList`

Display:

- Penalty code.
- Penalty name.
- Triggering pattern.
- Strategy.
- Calculated deduction.
- Effective deduction.
- Stacking policy.
- Aggregation result.
- Explanation.

Distinguish between:

- Applied.
- Suppressed.
- Ineligible.
- Rejected by validation.

Never hide suppressed penalties. They are important to explain why the final score differs from the sum of individual candidate penalties.

---

## 10. Pattern explorer

Route:

`/patterns`

Purpose: provide an organization-wide view of detected organizational conditions.

### 10.1 Pattern list

Component:

`PatternExplorer`

Support filtering by:

- Dimension.
- Severity.
- Confidence.
- Pattern category.
- Assessment.
- Detection date.
- Current or historical status.
- Penalty eligibility.
- Penalty application status.

Support sorting by:

- Severity.
- Confidence.
- Date detected.
- Dimension.
- Scoring impact.

The frontend must not calculate scoring impact. Sorting must use backend-provided values.

### 10.2 Pattern detail

Route:

`/patterns/:patternMatchId`

Display:

**Identification**
- Pattern code.
- Name.
- Description.
- Category.
- Severity.
- Confidence.
- Rule version.

**Detection**
- Detection timestamp.
- Matched conditions.
- Unmatched conditions, where available.
- Evidence requirements.
- Evidence count.
- Confidence explanation.

**Evidence**
- Signal.
- Source question.
- Original answer, subject to permissions.
- Normalized value.
- Evidence contribution.
- Timestamp.

**Scoring impact**
- Penalty eligibility.
- Triggered penalty.
- Applied deduction.
- Suppression reason, if applicable.
- Dimension affected.
- LIS impact, if returned by the backend.

**Recommended actions**
- Recommended action.
- Priority.
- Related evidence.
- Related patterns.

### 10.3 Cross-dimensional patterns

Cross-dimensional patterns must be visually distinguishable from single-dimension patterns.

Show all affected dimensions and the relationships between them.

Examples:

- Unsustainable Operating Model.
- Leadership-Driven Bottleneck.
- Technical Debt Acceleration.
- Organizational Dependency Trap.
- Hidden Organizational Fragility.
- Leadership Capacity Crisis.
- Growth Without Engineering Maturity.
- Hero Culture.

The frontend must display only patterns actually returned by the backend.

---

## 11. Penalty explorer

Route:

`/penalties`

Purpose: make scoring deductions transparent and auditable.

### 11.1 Penalty summary

Display:

- Total candidate penalties.
- Applied penalties.
- Suppressed penalties.
- Ineligible penalties.
- Total effective deduction.
- Penalties by dimension.
- Overall penalties.

### 11.2 Penalty list

Component:

`PenaltyExplorer`

Filters:

- Dimension.
- Penalty category.
- Strategy.
- Stacking policy.
- Status.
- Triggering pattern.
- Severity.

### 11.3 Penalty detail

Route:

`/penalties/:penaltyApplicationId`

Display:

- Penalty definition.
- Rule version.
- Triggering pattern.
- Severity.
- Confidence.
- Strategy.
- Configured maximum deduction.
- Calculated deduction.
- Effective deduction.
- Stacking policy.
- Aggregation group.
- Related penalties.
- Suppression or ineligibility explanation.

The interface must distinguish the individual candidate deduction from the final effective deduction after aggregation.

---

## 12. Imbalance analysis

Route:

`/leadership-intelligence/:scoringRunId/imbalances`

Purpose: highlight significant differences between dimensions.

### 12.1 Imbalance overview

Component:

`DimensionImbalanceChart`

Display:

- Dimensions involved.
- Score difference.
- Imbalance severity.
- Confidence.
- Threshold used.
- Explanation.
- Related patterns.

Example:

```text
Delivery Stability: 82
Technical Risk:     43

Detected imbalance: 39 points

Interpretation:
Delivery performance appears stronger than the
underlying technical foundation.
```

This text is illustrative. Actual interpretation must come from the backend.

### 12.2 Imbalance details

Display:

- Compared dimensions.
- Raw and adjusted scores.
- Detected gap.
- Detection rule.
- Evidence.
- Related patterns.
- Recommended actions.

Imbalances are diagnostic findings. They must not be displayed as penalties unless the backend explicitly returns a penalty application.

---

## 13. Historical trends

Route:

`/trends`

Purpose: understand organizational evolution over time.

### 13.1 LIS evolution

Component:

`LISHistoryChart`

Display:

- LIS over time.
- Assessment dates.
- Assessment references.
- Score changes.
- Configuration version.
- Significant events, when supplied by the backend.

### 13.2 Dimension evolution

Component:

`DimensionTrendChart`

Allow users to select one or more dimensions.

Display:

- Historical scores.
- Raw and adjusted values when available.
- Dimension-specific trends.
- Significant changes.

### 13.3 Pattern evolution

Component:

`PatternTrendChart`

Display:

- New patterns.
- Persistent patterns.
- Resolved patterns.
- Recurring patterns.
- Severity evolution.
- Confidence evolution.

### 13.4 Comparability rules

Historical comparisons must be supported by backend comparability metadata.

The frontend must:

- Display a warning when scoring configurations differ.
- Avoid presenting incompatible results as a continuous trend.
- Distinguish missing historical data from zero scores.
- Never infer that an absent pattern is resolved unless the backend confirms that status.

---

## 14. Reports

Route:

`/reports`

Reports should provide executive-friendly summaries without hiding scoring details.

Suggested report types:

1. Leadership Intelligence Report.
2. Dimension Analysis Report.
3. Organizational Risk Report.
4. Pattern and Penalty Report.
5. Historical Comparison Report.

Report content:

- Organization.
- Assessment period.
- LIS.
- Dimension scores.
- Detected patterns.
- Applied penalties.
- Imbalances.
- Key evidence.
- Recommendations.
- Methodology and scoring version.

Export formats:

- PDF.
- CSV, where structured data is appropriate.

Report generation and export must use backend endpoints when available.

The frontend must not independently generate a different scoring interpretation.

---

## 15. Assessment integration

Routes:

- `/assessments`
- `/assessments/:assessmentId`
- `/assessments/:assessmentId/results`

The frontend must support:

- Assessment listing.
- Assessment status.
- Assessment details.
- Response completion status.
- Submission status.
- Scoring status.
- Scoring result navigation.

### 15.1 Scoring states

Support these states:

- NOT_STARTED
- IN_PROGRESS
- SUBMITTED
- SCORING
- COMPLETED
- FAILED

Use backend status values as the authoritative state.

### 15.2 Scoring operation

When a user requests scoring:

1. Validate the assessment state through the backend.
2. Submit the scoring request.
3. Display a pending state.
4. Poll or subscribe for completion, according to the API contract.
5. Navigate to the result when completed.
6. Display a meaningful error if scoring fails.

Do not simulate successful scoring or display placeholder scores as actual results.

### 15.3 Recalculation

Recalculation must be a separate, permission-controlled action.

Before submitting:

- Explain that a new scoring run will be created.
- Display the assessment being recalculated.
- Explain that previous scoring results remain historical records.
- Require explicit confirmation.

The frontend must not overwrite previous scoring results.

---

## 16. API integration

The frontend must consume backend REST APIs.

Suggested endpoints:

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/organizations/{id}/dashboard` | Dashboard data |
| GET | `/api/v1/assessments` | Assessment list |
| GET | `/api/v1/assessments/{id}` | Assessment details |
| POST | `/api/v1/assessments/{id}/score` | Execute scoring |
| POST | `/api/v1/assessments/{id}/score/recalculate` | Recalculate |
| GET | `/api/v1/assessments/{id}/score` | Latest scoring result |
| GET | `/api/v1/scoring-runs/{id}` | Scoring run details |
| GET | `/api/v1/scoring-runs/{id}/explanation` | Full explanation |
| GET | `/api/v1/scoring-runs/{id}/dimensions` | Dimension results |
| GET | `/api/v1/scoring-runs/{id}/patterns` | Pattern matches |
| GET | `/api/v1/scoring-runs/{id}/penalties` | Penalty results |
| GET | `/api/v1/scoring-runs/{id}/imbalances` | Imbalance results |
| GET | `/api/v1/scoring-runs/{id}/contributions` | Contribution breakdown |
| GET | `/api/v1/scoring-runs/history` | Historical runs |
| GET | `/api/v1/scoring-runs/compare` | Historical comparison |
| GET | `/api/v1/reports` | Report listing |
| POST | `/api/v1/reports` | Generate report |

These are proposed frontend-facing contracts. Align them with the actual backend API specification before implementation.

### 16.1 API response requirements

The frontend needs the backend to provide:

- Stable identifiers.
- Scores and interpretation labels.
- Raw and adjusted dimension values.
- Dimension weights.
- Weighted contributions.
- Pattern matches.
- Pattern evidence.
- Confidence and severity.
- Penalty evaluations.
- Penalty aggregation results.
- Suppression reasons.
- Imbalance results.
- Calculation explanation.
- Engine and configuration versions.
- Historical comparability metadata.
- Pagination and filtering metadata where needed.

### 16.2 Example scoring response

```json
{
  "scoringRunId": "run-123",
  "assessmentId": "assessment-456",
  "status": "COMPLETED",
  "leadershipIntelligenceScore": {
    "value": 72.4,
    "label": "HEALTHY",
    "previousValue": 68.1,
    "change": 4.3
  },
  "dimensions": [
    {
      "code": "TEAM_HEALTH",
      "rawScore": 82.0,
      "adjustedScore": 76.0,
      "weight": 0.30,
      "weightedContribution": 22.8,
      "label": "HEALTHY",
      "appliedPenaltyTotal": 6.0
    },
    {
      "code": "DELIVERY_STABILITY",
      "rawScore": 75.0,
      "adjustedScore": 70.0,
      "weight": 0.30,
      "weightedContribution": 21.0,
      "label": "HEALTHY",
      "appliedPenaltyTotal": 5.0
    },
    {
      "code": "TECHNICAL_RISK",
      "rawScore": 68.0,
      "adjustedScore": 65.0,
      "weight": 0.25,
      "weightedContribution": 16.25,
      "label": "NEEDS_ATTENTION",
      "appliedPenaltyTotal": 3.0
    },
    {
      "code": "LEADERSHIP_LOAD",
      "rawScore": 80.0,
      "adjustedScore": 76.0,
      "weight": 0.15,
      "weightedContribution": 11.4,
      "label": "HEALTHY",
      "appliedPenaltyTotal": 4.0
    }
  ],
  "overallPenaltyTotal": 0.0,
  "detectedPatternCount": 5,
  "appliedPenaltyCount": 4,
  "suppressedPenaltyCount": 2,
  "engineVersion": "2.0.0",
  "configurationVersion": "2026.10",
  "calculatedAt": "2026-10-30T12:00:00Z"
}
```

This is an illustrative contract, not a production scoring result. The backend API specification is authoritative.

### 16.3 API error handling

Support:

- 400: Validation error.
- 401: Authentication required.
- 403: Permission denied.
- 404: Resource not found.
- 409: Invalid state or conflict.
- 422: Business validation error.
- 429: Rate limit exceeded.
- 500: Unexpected server error.
- 503: Service unavailable.

Use a consistent error presentation strategy.

---

## 17. Frontend component architecture

Suggested structure:

```text
src/
├── app/
│   ├── App.tsx
│   ├── router.tsx
│   └── providers/
│
├── features/
│   ├── dashboard/
│   │   ├── components/
│   │   ├── pages/
│   │   └── hooks/
│   │
│   ├── assessments/
│   │   ├── components/
│   │   ├── pages/
│   │   └── hooks/
│   │
│   ├── scoring/
│   │   ├── components/
│   │   │   ├── LeadershipIntelligenceScoreCard.tsx
│   │   │   ├── DimensionScoreCard.tsx
│   │   │   ├── ScoreCompositionChart.tsx
│   │   │   ├── ScoreCalculationExplanation.tsx
│   │   │   └── DimensionContributionTable.tsx
│   │   ├── pages/
│   │   ├── hooks/
│   │   ├── types/
│   │   └── api/
│   │
│   ├── patterns/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── hooks/
│   │   ├── types/
│   │   └── api/
│   │
│   ├── penalties/
│   │   ├── components/
│   │   ├── pages/
│   │   ├── hooks/
│   │   ├── types/
│   │   └── api/
│   │
│   ├── imbalances/
│   ├── trends/
│   ├── reports/
│   └── settings/
│
├── shared/
│   ├── components/
│   │   ├── charts/
│   │   ├── data-display/
│   │   ├── feedback/
│   │   ├── layout/
│   │   └── navigation/
│   ├── hooks/
│   ├── types/
│   └── utils/
│
├── services/
│   ├── api/
│   ├── auth/
│   └── query/
│
├── stores/
├── styles/
└── main.tsx
```

### 17.1 Reusable components

Implement reusable components for:

- ScoreCard.
- ScoreGauge.
- ScoreBadge.
- SeverityBadge.
- ConfidenceIndicator.
- DimensionCard.
- PatternCard.
- PenaltyCard.
- EvidenceList.
- EvidenceDetail.
- ImbalanceCard.
- TrendChart.
- EmptyState.
- LoadingState.
- ErrorState.
- ConfirmationDialog.
- FilterPanel.
- DataTable.
- VersionInformation.

Components must be driven by typed data contracts, not hard-coded scoring rules.

---

## 18. State management

### Server state

Use TanStack Query for:

- Dashboard data.
- Assessment data.
- Scoring runs.
- Dimension results.
- Patterns.
- Penalties.
- Imbalances.
- Historical trends.
- Reports.

Configure:

- Query keys.
- Cache invalidation.
- Retry policies.
- Stale time.
- Pagination.
- Background refresh for long-running scoring operations.

### Client state

Use Zustand only for shared UI state where necessary:

- Selected organization.
- Navigation preferences.
- Global filters.
- Display preferences.

Do not store canonical scoring results as mutable global state.

### Data freshness

Display the scoring timestamp and distinguish:

- Latest available scoring result.
- In-progress scoring operation.
- Historical scoring result.
- Stale cached data.

Never silently present stale data as newly calculated data.

---

## 19. Security and privacy

The frontend must:

- Use secure authentication integration.
- Follow the backend session/token strategy.
- Avoid storing sensitive authentication credentials in local storage.
- Respect tenant boundaries.
- Avoid exposing data from other organizations.
- Avoid logging assessment answers or sensitive evidence.
- Mask sensitive information where permissions require it.
- Avoid placing sensitive content in URLs.
- Handle session expiration.
- Enforce route-level authorization.
- Follow the backend's CSRF requirements.

The backend remains responsible for tenant isolation and authorization enforcement.

---

## 20. Accessibility and usability

Target WCAG 2.2 AA.

Requirements:

- Keyboard navigation.
- Visible focus indicators.
- Semantic HTML.
- Screen-reader labels.
- Accessible charts with textual summaries.
- Sufficient color contrast.
- No reliance on color alone.
- Responsive layouts.
- Clear error messages.
- Consistent terminology.
- Accessible dialogs and tables.

All charts must provide an alternative textual representation of their key data.

---

## 21. Responsive behavior

Support:

- Desktop: primary analytical experience.
- Tablet: simplified multi-column layouts.
- Mobile: prioritized summary and vertically stacked details.

Complex tables must support horizontal scrolling or responsive alternative layouts.

The score overview must remain understandable on smaller screens.

---

## 22. Testing strategy

### 22.1 Unit tests

Test:

- Score display formatting.
- Score classification display.
- Confidence presentation.
- Severity presentation.
- Dimension mapping.
- Pattern status rendering.
- Penalty status rendering.
- Explanation rendering.
- Historical comparability warnings.
- Permission-based UI behavior.

Do not write tests that duplicate backend scoring algorithms.

### 22.2 Component tests

Test:

- LIS summary.
- Dimension cards.
- Score composition.
- Pattern explorer.
- Penalty explorer.
- Evidence details.
- Imbalance visualization.
- Historical trends.
- Error and empty states.

### 22.3 Integration tests

Test:

- API integration.
- Loading states.
- Error handling.
- Query invalidation.
- Scoring operation lifecycle.
- Organization switching.
- Authorization behavior.
- Pagination and filtering.

### 22.4 E2E tests

Minimum scenarios:

1. User logs in and accesses the dashboard.
2. User selects an organization.
3. User opens the latest scoring result.
4. User explores each dimension.
5. User opens a detected pattern.
6. User inspects supporting evidence.
7. User opens an applied penalty.
8. User inspects a suppressed penalty.
9. User reviews an imbalance.
10. User compares historical scoring runs.
11. User generates a report.
12. Unauthorized user cannot access restricted functions.
13. User requests recalculation and sees the new scoring run.
14. Previous scoring results remain accessible.

### 22.5 Critical frontend invariants

- No frontend score calculation.
- No frontend penalty calculation.
- No frontend pattern detection.
- No fabricated evidence.
- No fabricated historical values.
- No missing values represented as zero.
- No incompatible historical scores presented as directly comparable.
- No unauthorized tenant data displayed.
- No scoring result presented as complete before backend confirmation.

---

## 23. Performance requirements

Suggested targets:

- Initial application shell: under 2.5 seconds under agreed test conditions.
- Dashboard usable: under 3 seconds under agreed test conditions.
- Route transitions: under 500 ms for cached data.
- Large lists: server-side pagination.
- Charts: avoid unnecessary rendering and excessive data points.
- Lazy-load secondary analytical sections.
- Use skeletons for loading states.
- Avoid unnecessary API requests.

These are initial targets and must be validated against actual infrastructure and datasets.

---

## 24. Implementation phases

### Phase 1 — Application foundation

- React and TypeScript setup.
- Routing.
- Design system.
- Authentication integration.
- Organization selector.
- API client.
- Error handling.
- Shared layout.

### Phase 2 — Scoring overview

- Dashboard.
- LIS card.
- Dimension score grid.
- Score composition chart.
- Scoring run details.

### Phase 3 — Explainability

- Calculation explanation.
- Dimension contribution breakdown.
- Evidence presentation.
- Version information.

### Phase 4 — Patterns and penalties

- Pattern explorer.
- Pattern detail.
- Penalty explorer.
- Penalty detail.
- Applied and suppressed penalty visualization.

### Phase 5 — Imbalances and trends

- Imbalance analysis.
- LIS history.
- Dimension evolution.
- Pattern evolution.
- Historical comparison.

### Phase 6 — Assessments and reports

- Assessment integration.
- Scoring operation lifecycle.
- Recalculation workflow.
- Reports and exports.

### Phase 7 — Quality and release

- Accessibility.
- Responsive behavior.
- Performance.
- Security review.
- Automated tests.
- E2E validation.
- Production readiness.

---

## 25. Definition of Done

The frontend is considered complete when:

- All primary scoring screens are implemented.
- All displayed scoring data comes from backend APIs.
- Every score can be traced to its backend explanation.
- Patterns display their evidence and confidence.
- Penalties display their calculated and effective deductions.
- Suppressed penalties have visible explanations.
- Imbalances are distinguishable from penalties.
- Historical comparisons respect comparability metadata.
- Tenant and role restrictions are enforced in the UI.
- Loading, empty, error, and stale-data states are implemented.
- Responsive behavior is validated.
- Accessibility requirements are addressed.
- Unit, integration, and E2E tests pass.
- No scoring business logic has been duplicated in the frontend.

---

## 26. Implementation guardrails for AI coding agents

When implementing this specification with Claude Code or another coding agent:

1. Inspect the existing repository before modifying code.
2. Read the backend specification and API contracts.
3. Reuse existing components and conventions where appropriate.
4. Do not invent API endpoints or response fields.
5. Do not invent scoring rules or thresholds.
6. Do not calculate LIS or penalties in React.
7. Do not hard-code patterns or scoring configurations into UI components.
8. Do not fabricate sample data in production paths.
9. Keep mock data isolated from production API integration.
10. Generate typed API contracts from the actual backend specification when possible.
11. Identify missing backend capabilities before implementing dependent screens.
12. Add tests for every significant component and interaction.
13. Do not silently change backend semantics to simplify frontend implementation.
14. Document API gaps and assumptions.
15. Preserve historical scoring results and their version metadata.

### Required implementation artifacts

Before implementation, generate:

- `docs/frontend/FRONTEND_ANALYSIS.md`
- `docs/frontend/FRONTEND_IMPLEMENTATION_PLAN.md`
- `docs/frontend/API_DEPENDENCIES.md`
- `docs/frontend/COMPONENT_INVENTORY.md`
- `docs/frontend/TEST_STRATEGY.md`

After implementation, generate:

- `docs/frontend/IMPLEMENTATION_STATUS.md`
- `docs/frontend/KNOWN_GAPS.md`

The implementation agent must distinguish completed functionality, partially completed functionality, mocked functionality, and blocked functionality.

---

## 27. Final architectural principle

The frontend is the **explainability and decision-support layer** of the Leadership Intelligence Platform.

The scoring engine owns:

- Normalization.
- Dimension scoring.
- Pattern detection.
- Confidence calculation.
- Penalty evaluation.
- Penalty aggregation.
- Imbalance detection.
- LIS calculation.
- Interpretation.
- Historical reproducibility.

The frontend owns:

- Visualization.
- Navigation.
- Filtering.
- Exploration.
- Evidence presentation.
- Explanation presentation.
- Historical comparison presentation.
- User interaction.
- Report consumption.

The separation is mandatory.

The goal is not merely to show an organizational score. It is to allow a leader to understand **why the score exists, what organizational conditions explain it, how those conditions affect the result, and where attention may produce the greatest improvement.**ss