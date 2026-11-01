# Requirements traceability: executive dashboard brief

Maps each requirement of the frontend brief (sections 10 to 14) to the component that implements it and the tests that prove it. The functional scoring requirements (R-001 to R-055, specv3 sections) are traced separately in `docs/implementation/REQUIREMENTS_TRACEABILITY.md`.

Status: DONE, PARTIAL (explained), NOT DONE (see `KNOWN_LIMITATIONS.md`). Test names refer to files under `frontend/src` (unit, `*.test.tsx`) and `frontend/e2e` (Playwright).

## 10.1 Design principles

| Requirement | Implementation | Tests | Status |
|---|---|---|---|
| Modern, premium, clean, restrained colour used for risk and status | design tokens in `index.css`, `components/ui/*`; colour only for severity, state and interpretation | visual review of screenshots; `accessibility.spec` colour-is-not-the-only-signal | DONE |
| Responsive | Tailwind breakpoints; tables scroll inside their wrapper | `accessibility.spec` no horizontal scrolling at 390 px on five screens | DONE |
| Accessible | axe clean on every screen; labels, roles, keyboard paths, skip link | `accessibility.spec` (6 tests) | DONE (automated plus manual review; no screen-reader audit) |
| Consistent, easy to navigate | shared layout, back links, header navigation | `AppLayout.test`, journey | DONE |
| No generic admin-panel look, no clutter, no decorative charts | single anchor (LIS), cards, restrained palette | design review | DONE |

## 10.2 Main dashboard

| Requirement | Implementation | Tests | Status |
|---|---|---|---|
| Overall LIS, label, score range, last assessment date | `LisHero`, `ScoreGauge` (bands from the catalog) | `LisHero.test` (score, label, band, date, notice), journey step 4 | DONE |
| Comparison with the previous assessment when available; trend | `ComparisonNote` (change only when `comparable`), `Sparkline` | `LisHero.test` (comparable, not comparable with reasons, first assessment, text alternative) | DONE |
| Does not imply certainty or an individual rating | the backend `notice` is shown with the score, comparison, dashboard and recommendations | `LisHero.test`, `DashboardPage.test` | DONE |
| Four dimension cards: score, interpretation, trend, risk, key signals, navigation | `DimensionCard`, `DimensionOverview` | `DimensionCard.test` (7), dashboard e2e | DONE |
| Patterns grouped by dimension, severity, confidence; name, description, severity, confidence, evidence count, affected dimensions, actions; evidence drill-down | `PatternOverview`, `PatternCard`, `EvidenceList`, `selectors.ts` | `PatternOverview.test` (10), `selectors.test` (8), `explainability.spec` | DONE |
| Risk overview by severity with filtering and navigation | `RiskOverview` (URL-backed filter) | `RiskOverview.test`, `DashboardPage.test` (filter, restore from address), `dashboard.spec` | DONE |
| Penalty overview: applied, suppressed, ineligible, deduction, target, trigger, policy, explanation; suppression never hidden | `PenaltyOverview`, `PenaltyCard` | `PenaltyOverview.test` (6), journey step 8, `invariants.spec` | DONE |
| Prioritized actions from real findings, traceable, never invented | `RecommendationsPanel` (catalog text, source links, unmapped keys listed) | `RecommendationsPanel.test`, `CatalogAndTrendsApiIntegrationTest`, `RecommendationsApiIntegrationTest` | DONE |

## 10.3 Dimension detail

| Requirement | Implementation | Tests | Status |
|---|---|---|---|
| Score, raw, adjusted, trend, contributions, positive and negative signals, patterns, applied and suppressed penalties, evidence, recommendations | `DimensionDetailPage` | `DimensionDetailPage.test` (11), journey step 5 | DONE |
| Drill-down from a contribution to the source question and answer, subject to authorization | "Show answer" for OWNER, ADMIN, ANALYST via `GET /scoring-runs/{id}/answers` (D42), audited | `DimensionDetailPage.test` (viewer never requests answers), `AnswersApiIntegrationTest` (5), `explainability.spec`, `security.spec` role matrix | DONE |

## 10.4 LIS explanation view

| Requirement | Implementation | Tests | Status |
|---|---|---|---|
| Visual sequence inputs, normalization, contributions, raw scores, patterns, penalties, adjusted dimensions, final LIS | `ExplanationPage`, `features/explanation/stages.tsx` (eight numbered steps) | `ExplanationPage.test` (12), journey step 9 | DONE |
| Waterfall, contribution breakdown, dimension comparison, evidence panels, expandable details | `Waterfall`, `SegmentTrack`, step tables, `details` sections | `Waterfall.test` (4) | DONE |
| All values from the backend; the frontend never calculates or modifies scores | trace rendered as stored; ESLint rule on score arithmetic; waterfall uses no sums | ESLint `no-restricted-syntax`, `Waterfall.test` (widths equal single values), e2e final score equals the dashboard's | DONE |

## 10.5 Pattern detail

| Requirement | Implementation | Tests | Status |
|---|---|---|---|
| Name, description, category, severity, confidence, matched conditions, evidence, affected dimensions, related patterns, penalties, actions | `PatternDetailPage`, `ConditionTree`, `conditions.ts` | `PatternDetailPage.test` (9), `conditions.test` (4), journey steps 6 and 7 | DONE |
| Explain why in language an engineering leader understands | catalog explanation plus readable conditions ("Workload pressure is at least 75 (it was 80.00)"); unknown outcomes shown as "could not be evaluated" | `conditions.test`, `PatternDetailPage.test` | DONE (wording depends on the configured catalog) |

## 10.6 Penalty detail

| Requirement | Implementation | Tests | Status |
|---|---|---|---|
| Name, trigger, target, requested and effective deduction, stacking policy, aggregation result, suppression reason | `PenaltyDetailPage` | `PenaltyDetailPage.test` (7), journey step 8 | DONE |
| Maximum deduction | not part of the stored trace; the page says so (D42) | `PenaltyDetailPage.test` | PARTIAL |
| Candidate versus applied visually distinct | two bars, banner "This is a candidate, not an applied deduction" | `PenaltyDetailPage.test`, journey | DONE |

## 10.7 Historical trends

| Requirement | Implementation | Tests | Status |
|---|---|---|---|
| LIS, dimension, pattern frequency, risk and penalty evolution | `TrendsSection` (five views, each with a data table), `GET /organizations/{id}/trends` (D40) | `TrendsSection.test` (6), `CatalogAndTrendsApiIntegrationTest` | DONE |
| Incompatible configurations never compared without naming the difference | banner and chart markers wherever `comparable` is false; `ComparePage` "what differs" table | `TrendsSection.test`, `ComparePage.test`, journey step 10 | DONE |

## 10.8 State and data handling

| Requirement | Implementation | Tests | Status |
|---|---|---|---|
| TanStack Query for fetching | `services/queries/*` (one hook per endpoint, bounded stale times, no retries for client errors) | `client.test`, page tests | DONE |
| Zustand only for appropriate shared state | `stores/session.ts` holds the selected organization only | `AppLayout.test`, `hardening.spec` (no scoring data in storage) | DONE |
| React Hook Form and zod for forms | comparison form; zod validates every free-form API response | `ComparePage.test` (validation), `views.test`, `trace.test` | DONE |
| Loading, empty, error states, retry, permission-aware rendering, responsive, accessible | `QuerySection`, `StateViews`, `Permitted`, role helpers | page tests, `security.spec`, `accessibility.spec` | DONE |
| No hard-coded results in production; mocks isolated and replaced by real integration | fixtures only under `src/tests`; `npm run check:no-mocks` | lint rule, `check:no-mocks`, all e2e against the real API | DONE |

## 11 Component architecture and 12 API integration

| Requirement | Implementation | Status |
|---|---|---|
| Suggested structure adapted | `app`, `components/{ui,charts,score,dimensions,patterns,penalties,evidence,explanations}`, `features/*`, `services/{api,auth,queries}`, `stores`, `types`, `utils`, `tests` | DONE |
| DTOs, JSON examples, types, clients, hooks, integration tests | OpenAPI-generated types (`npm run gen:api`), `docs/frontend/DTO_VIEW_MAPPING.md`, fixtures captured from the real API | DONE |
| No business logic duplicated; no canonical value computed in TypeScript | ESLint rule; `presentation.ts` only formats and positions | DONE |

## 13 Testing

| Requirement | Evidence | Status |
|---|---|---|
| Vitest and React Testing Library: dashboard, score, cards, patterns, penalties, states, filtering, drill-down, API behaviour | 171 tests | DONE |
| Playwright: authenticate, select organization, dashboard, inspect LIS, dimension, pattern, evidence, applied and suppressed penalties, explanation, compare | `journey.spec` (one test, ten steps) plus the other specs | DONE |
| Normalized values, dimension scores and LIS within 0 to 100 | `invariants.spec`; backend `ScoringInvariantSweepTest` (500 inputs) | DONE |
| Penalties cannot increase scores; adjusted not above raw | `invariants.spec`; `AdjustedDimension` rejects it by construction | DONE |
| Missing data is not zero | `invariants.spec` (no score for insufficient data); normalization and coverage unit tests; `DimensionCard.test`, `LisHero.test`, `formatScore` tests | DONE |
| Pattern detection alone cannot change the score | `invariants.spec` (a dimension with no applied penalty keeps its raw score); backend `patternsAndPenaltiesNeverChangeTheRawScores` | DONE |
| Identical inputs and configuration give identical results | `invariants.spec` determinism; `ReproducibilityIntegrationTest` | DONE |
| Suppressed penalties do not affect scoring | `invariants.spec` (aggregated deduction 12, suppressed 5 ignored) | DONE |
| Overall penalties applied once according to the policy | `invariants.spec`; backend aggregation tests | DONE |

## 14 Workflow

| Phase | Result |
|---|---|
| 1 Foundation | DONE |
| 2 Scoring dashboard and visualization | DONE |
| 3 Explainability experience | DONE |
| 4 End-to-end integration | DONE |
| 5 Quality and release readiness | DONE, with the exceptions in `KNOWN_LIMITATIONS.md` |

Release documents produced: `IMPLEMENTATION_REPORT.md`, `TEST_RESULTS.md`, `REQUIREMENTS_TRACEABILITY.md`, `KNOWN_LIMITATIONS.md`, `DEPLOYMENT_GUIDE.md`.

## Answers from several people (decision D45, specv3 4.10 to 4.13 and 14.2.1)

Requested after the first release run: the engine accepts answers that represent a member, a team or the organization. The wording is `specv3.md` sections 4.10 to 4.13 and 14.2.1; the decision and its limits are in `docs/implementation/RESPONSE_SCOPES_PROPOSAL.md`.

| Requirement | Implementation | Tests | Status |
|---|---|---|---|
| Answers carry a scope; a member has no identity and is never output, ranked or listed one by one | `ScopedResponse`, `POST /calculate` `responses[]`, members stored with an ordinal only, answers returned as groups | `ResponseAggregationServiceTest` (scope rules), `ScopedResponsesApiIntegrationTest` (nothing identifying in storage, trace or logs; answers as sorted groups), `scopes.spec` | DONE |
| Several answers to one question are combined after normalization by a configured method (median or mean, per question) | `ResponseAggregationService`, configuration `responses` and question `aggregation` | `ResponseAggregationServiceTest` (odd, even, direction, order independence, identical answers), `ResponseSettingsParserTest` | DONE (defaults unvalidated) |
| Minimum number of responses; fewer makes the question missing, never zero; a unit with too little data has no score | `ResponseSettings.minResponses`, missing-data policy applied once per question | `ResponseScopesEngineTest`, `ScopedResponsesApiIntegrationTest`, `ScopedRequiredQuestionIntegrationTest`, `ResponsesPanel.test`, `DashboardPage.scoped.test`, `scopes.spec` | DONE |
| Mixing scopes for one question is rejected | `MIXED_RESPONSE_SCOPES`, `DUPLICATE_TEAM_SCOPE`, `INVALID_RESPONSE_SCOPE` (422) | `ResponseAggregationServiceTest`, `ScopedResponsesApiIntegrationTest` | DONE |
| One result for the organization and one for each team with enough data; teams never ranked; history, trends and comparison at organization level | organization run plus team runs in one transaction, V6, scope filters in the history queries | `ScopedResponsesApiIntegrationTest` (history, latest, compare, trends, dashboard), `scopes.spec`, `ScopeSwitcher.test` | DONE |
| Respondent count lowers confidence; earlier runs keep the earlier algorithm | `PatternConfidenceService` version 2 (participation) | `PatternConfidenceParticipationTest`, `PatternDetailPage.scoped.test` | DONE (formula unvalidated) |
| Earlier requests, hashes, runs and data are unchanged | `input-hash-v1` kept for one answer per question; V6 backfills organization scope | `ResponseScopesEngineTest` (identical scores), `FlywayMigrationTest` (upgrade of a populated database), every earlier test | DONE |
| The interface lets a reader switch scope, see how answers were combined, and understand a missing score | `ScopeSwitcher`, `ResponsesPanel`, `SourceAnswers`, team run in the explanation and dimension views | `DashboardPage.scoped.test`, `ExplanationPage.scoped.test`, `DimensionDetailPage.scoped.test`, `scopes.spec` (axe, 390 px) | DONE |
| Collecting answers from people in the product | not built (answers arrive through the API) | | NOT DONE (limitation 28) |

## Configuration provided as JSON (initial version)

Requested after the first release run: the configuration is provided as a JSON document and an initial version is included with everything needed.

| Requirement | Implementation | Tests | Status |
|---|---|---|---|
| The configuration is a JSON document read by the application | `InitialConfigurationLoader`, `lis.configuration.initial.*`, `ConfigurationService.hashOf` | `InitialConfigurationLoaderIntegrationTest` (9), real startup on an empty database | DONE |
| An initial version with everything needed: questions, signals, dimensions, patterns, penalties, imbalance, weights and bands, answer-combination rules, recommendation and presentation text | `src/main/resources/config/initial-platform-default.json`, documented in `docs/configuration/INITIAL_CONFIGURATION.md` | `InitialConfigurationTest` (14) | DONE (content unvalidated, limitation 2) |
| Follows the specification's catalog, invents no identifiers and does not exceed suggested maxima; enables only what the questionnaire can measure | catalog identifiers of specv3 13.3 and 15.5, 16 of the 48 catalog patterns enabled | `InitialConfigurationTest` | DONE |
| A broken file never leaves the application running without rules; an active version is never edited | startup fails with the reason; changes are new versions (`SYNC`) | `InitialConfigurationLoaderIntegrationTest` | DONE |
| Tenants, template and organization overrides, runtime changes | not provided | | NOT DONE (limitations 1 and 4) |

## Platform administration: create and list tenants (decision D46)

Requested after the first release run: a screen an administrator can use to create a tenant, and a screen that lists the tenants.

| Requirement | Implementation | Tests | Status |
|---|---|---|---|
| A screen to create a tenant | `CreateTenantPage`, `POST /api/v1/platform/tenants` | `CreateTenantPage.test` (15), `PlatformTenantsApiIntegrationTest`, `platform.spec` | DONE |
| A screen that lists the tenants | `TenantListPage`, `GET /api/v1/platform/tenants` | `TenantListPage.test` (8), `platform.spec` | DONE |
| "An admin" can reach them, without breaking tenant isolation: a role outside every tenant | `PLATFORM_ADMIN` (not in the `Role` enum), `PlatformRole`, request rule and controller check | `PlatformTenantsApiIntegrationTest` (role matrix), `guards.platform.test`, `platform.spec` | DONE (needs the role in the identity provider, limitation 36) |
| Tenant roles and the platform role cannot reach each other's data | request rules; the role carries no tenant | `PlatformTenantsApiIntegrationTest`, `platform.spec` | DONE |
| Creation and list reads are recorded | `scoring_access_audit` rows `TENANT_CREATED` and `TENANTS_LISTED`, Flyway V7 | `PlatformTenantsApiIntegrationTest`, `FlywayMigrationTest` | DONE |
| Change, disable or delete a tenant; organizations and users | not built | | NOT DONE (limitation 4) |
