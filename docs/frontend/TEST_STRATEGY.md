# Frontend Test Strategy

**Source:** FE §22. Tooling: Vitest, React Testing Library, Playwright. Backend: `docs/implementation/TEST_STRATEGY.md`.
Rule: do not write tests that duplicate backend scoring algorithms (FE §22.1).

## 1. Unit tests (Vitest)

Score display formatting (preserves backend scale, no added precision); classification display using the backend label; confidence presentation; severity presentation (independent of confidence); dimension mapping; pattern status rendering; penalty status rendering (four states); explanation rendering; historical comparability warnings; permission-based UI behavior; missing value rendering (never zero).

## 2. Component tests (React Testing Library)

LIS summary; dimension cards; score composition; pattern explorer filters and sort; penalty explorer; evidence details; imbalance visualization; historical trends; empty, error, loading and stale-data states; confirmation dialog for recalculation; chart text alternatives.

## 3. Integration tests

API integration with a mock server isolated from production code; loading states; error handling for 400, 401, 403, 404, 409, 422, 429, 500, 503; query invalidation; scoring operation lifecycle; organization switching; authorization behavior; pagination and filtering.

## 4. E2E scenarios (Playwright), minimum 14 (FE §22.4)

1. Login and dashboard access.
2. Select an organization.
3. Open latest scoring result.
4. Explore each dimension.
5. Open a detected pattern.
6. Inspect supporting evidence.
7. Open an applied penalty.
8. Inspect a suppressed penalty.
9. Review an imbalance.
10. Compare historical runs.
11. Generate a report.
12. Unauthorized user cannot reach restricted functions.
13. Request recalculation and see the new run.
14. Previous results remain accessible.

Scenario 11 (reports) stays BLOCKED until F13 is specified. Scenario 10 depends on the history endpoints (D26, D27). Scenario 1 uses the D19 roles.

## 5. Critical invariants as tests (FE §22.5)

| Invariant | Approach |
|---|---|
| no frontend score or penalty calculation | lint rule forbidding arithmetic on score fields outside `formatScore`; test that rendered values equal payload values |
| no frontend pattern detection | test with payload lacking a pattern: nothing rendered |
| no fabricated evidence or history | test empty and partial payloads |
| missing is not zero | test null and absent values render "not available" |
| incompatible versions not directly comparable | test warning when configuration versions differ |
| no unauthorized tenant data | test role and organization guards (backend remains authoritative) |
| no result shown complete before backend confirmation | test pending and failed states |

## 6. Accessibility, responsive, performance

axe checks in component and E2E tests (WCAG 2.2 AA); keyboard navigation and focus order; contrast; no reliance on color alone; responsive viewports desktop, tablet, mobile; large tables scroll or reflow. Performance targets in FE §23 are initial and must be validated; use pagination for large lists.

## 7. Data and mocks

Contract fixtures generated from OpenAPI; mocks live outside production paths; no sample scoring data in production builds (FE §26.8, §26.9). Fixtures must be self-consistent (FE §16.2 example is not: contributions sum to 71.45, LIS shows 72.4).

## 8. Gates

Unit, component, integration green before a feature is considered done; E2E per FE phase; report tests not run or skipped explicitly.
