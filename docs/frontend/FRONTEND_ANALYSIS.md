# Frontend Analysis

**Source:** `specs/frontend-spec.md` (FE). **Status:** analysis only; no frontend code exists.
Backend gaps and conflicts: `docs/implementation/ARCHITECTURE_GAPS.md`.

## 1. Purpose

The frontend is the explainability and decision-support layer (FE §27). It visualizes backend results and never computes them.

Navigation of explanation: LIS -> Dimension -> Pattern -> Evidence -> Scoring impact (FE §2.1).

## 2. Hard invariants (FE §2.5, §22.5, §26)

- No frontend calculation of LIS, dimension scores, penalties or pattern matches.
- No derived classification when the backend supplies a label (FE §6.2).
- No fabricated evidence, history or sample data in production paths; mocks isolated.
- Missing values are never shown as zero; absent patterns are not "resolved" unless the backend says so.
- No incompatible configuration versions presented as one trend (FE §13.4).
- No result shown as complete before backend confirmation.
- Display backend precision; add none (FE §2.6).
- Sensitive evidence masked per permissions; no answers in logs or URLs (FE §19).

## 3. Stack (FE §3, decision D2)

React, TypeScript, Vite, Tailwind CSS, React Router, TanStack Query, Zustand, React Hook Form, Zod, Recharts, Lucide, Vitest, React Testing Library, Playwright. API types generated from OpenAPI where available.

Conflict: `.claude/agents/frontend-engineer.md` and `ui-engineer.md` assume Next.js App Router (F1). Do not use them for stack decisions; update them with approval.

## 4. Screens and routes

| Area | Routes (FE) | Backend dependency | Readiness |
|---|---|---|---|
| Dashboard | `/dashboard` | dashboard endpoint, comparison, recommendations | PLANNED (D26 step 4, after D27, D28) |
| Score detail | `/leadership-intelligence/:scoringRunId` | run, explanation, weights | partial (C13, F15) |
| Dimension analysis | `.../dimensions/{team-health,delivery-stability,technical-risk,leadership-load}` | dimensions, contributions, patterns, penalties | derivable |
| Pattern explorer | `/patterns`, `/patterns/:patternMatchId` | patterns with filters, pagination | derivable; filter params proposed |
| Penalty explorer | `/penalties`, `/penalties/:penaltyApplicationId` | penalties | derivable (F9) |
| Imbalances | `.../:scoringRunId/imbalances` | imbalances | partial (F12) |
| Trends | `/trends` | history, compare, comparability, pattern lifecycle | PLANNED (D26, D27) |
| Reports | `/reports` | reports endpoints | BLOCKED (F13) |
| Assessments | `/assessments`, `/:id`, `/:id/results` | assessment API | PLANNED (D13, D26 step 2) |
| Settings | in navigation (FE §4) | configuration management | no spec (G2) |

Settings has no content defined in FE; treat as BLOCKED, not invented.

## 5. Roles and authorization (FE §5)

Presentation-level roles OWNER, ADMIN, ENGINEERING_LEADER, ANALYST, VIEWER. Backend authority; the UI hides navigation and actions, protects routes, handles 401/403, and never treats UI state as a security boundary. Roles and JWT claims are undefined in the backend (G3, F7).

## 6. Inconsistencies in FE itself

- Response example (FE §16.2): weighted contributions total 71.45 and `overallPenaltyTotal` is 0, yet LIS is 72.4. Illustrative only.
- Dashboard "previous score" and `change` need comparability metadata that no backend spec defines.
- FE §9.4 lists a fifth penalty state "Rejected by validation" (F9).
- FE §6.2 five score bands differ from V3 §18 (F2).
- Settings and Reports are navigation items with no content for Settings and no backend for Reports.

## 7. Dependencies on tenant decision (D9)

The backend enforces multi-tenancy (V3 §25). The tenant comes from the authenticated context and is never chosen in the UI. The organization selector lists organizations the user may access within that tenant (`organizationId`). Role and organization mapping is still open (F7, G3).

## 8. Risks

| Risk | Mitigation |
|---|---|
| Frontend built before the contract is stable | Generate types from OpenAPI; resolve C13 first; build phases 1-3 only against agreed endpoints. |
| Silent frontend scoring logic | Lint rule and tests: forbid arithmetic on scores outside formatting helpers. |
| Mock data reaching production | Mocks in a separate package; production build excludes them. |
| Over-precision | One formatting helper that preserves backend scale. |

## 9. Out of scope for now

Implementation; `IMPLEMENTATION_STATUS.md` and `KNOWN_GAPS.md` (post-implementation, FE §26).
