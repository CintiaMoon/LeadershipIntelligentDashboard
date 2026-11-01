# Frontend API Dependencies

**Source:** FE §16. Classification against the backend specs (`specs/specv3.md` §28 (V3, replaces spec.md and specv2.md), `specs/architect.md` §25):

- **EXISTS** specified in V3 or ARCH.
- **DERIVABLE** not specified, but can be served from persisted run data without new business rules; needs approval of the contract (F3).
- **MISSING** needs new backend capability or definition; screens depending on it are BLOCKED.

FE itself states these endpoints are proposals to be aligned with the backend specification (FE §16, §26.4).

| # | Method | Endpoint | Used by | Class | Notes / gap |
|---|---|---|---|---|---|
| 1 | GET | `/organizations/{id}/dashboard` | Dashboard | EXISTS | D37; latest per template, recent runs, counts, notice |
| 2 | GET | `/assessments` | Assessment list | EXISTS | D37; cursor paging, status and organization filters |
| 3 | GET | `/assessments/{id}` | Assessment details | EXISTS | D37; includes `latestRun` |
| 4 | POST | `/assessments/{id}/score` | Scoring action | EXISTS | V3 §28.1; request has no `inputs` (D10); synchronous (F8) |
| 5 | POST | `/assessments/{id}/score/recalculate` | Recalculation | EXISTS | V3 §28.5; confirmation dialog required; optional configuration version (G15) |
| 6 | GET | `/assessments/{id}/score` | Latest result | EXISTS | V3 §28.2; shape per C13 |
| 7 | GET | `/scoring-runs/{id}` | Run details | EXISTS | V3 §28.3 |
| 8 | GET | `/scoring-runs/{id}/explanation` | Calculation explanation | EXISTS | V3 §28.4; trace schema F15 |
| 9 | GET | `/scoring-runs/{id}/dimensions` | Dimension analysis | EXISTS | V3 §28.6; weights and labels additive (C13) |
| 10 | GET | `/scoring-runs/{id}/patterns` | Pattern explorer | EXISTS | V3 §28.7; filters/sort/pagination not in V3 |
| 11 | GET | `/scoring-runs/{id}/penalties` | Penalty explorer | EXISTS | V3 §28.8; four states only (D7) |
| 12 | GET | `/scoring-runs/{id}/imbalances` | Imbalance analysis | EXISTS | V3 §28.9; confidence absent (F12) |
| 13 | GET | `/scoring-runs/{id}/contributions` | Contribution table | DERIVABLE | inside `/dimensions` (V3 §28.6); no separate endpoint; D15 |
| 14 | GET | `/scoring-runs` | Trends | EXISTS | D37; run summaries with `previousScore`, `change`, `comparable`, `notComparableReasons` (replaces the proposed `/scoring-runs/history`) |
| 15 | GET | `/scoring-runs/compare` | Comparison | EXISTS | D37; delta only when comparable |
| 16 | GET | `/reports` | Reports list | MISSING | F13 |
| 17 | POST | `/reports` | Generate report | MISSING | F13; PDF/CSV |

Additional dependencies not listed in FE §16:

| Dependency | Needed by | Class |
|---|---|---|
| Single pattern detail `/patterns/{patternMatchId}` (route exists in FE §10.2) | Pattern detail | MISSING endpoint (could derive from #10) |
| Single penalty detail `/penalties/{penaltyApplicationId}` (FE §11.3) | Penalty detail | MISSING endpoint |
| Pattern and penalty lists across runs (organization-wide, FE §10.1, §11) | `/patterns`, `/penalties` | MISSING (listed endpoints are per run) |
| Authentication, current user, roles, organization list | Foundation | MISSING (G3, F7) |
| OpenAPI document for type generation | All | proposed (springdoc) |

## Response requirements (FE §16.1) vs backend

| Needed | Backend status |
|---|---|
| stable identifiers (run, match, application) | persistence tables exist in V3 §26.4-§26.6; IDs to be exposed |
| scores and interpretation labels | LIS interpretation yes (V3 §18); per-dimension labels allowed by V3 §18.1 (separate configuration) |
| raw and adjusted values, weights, weighted contributions | raw/adjusted yes; weights and contributions must be added (C13) |
| pattern evidence, confidence, severity | V3 §14, §19.2; confidence band label INSUFFICIENT/WEAK/MODERATE/STRONG (F14) |
| penalty evaluations, aggregation, suppression reasons | V3 §16, §19.2 |
| imbalance results | V3 §12.3 |
| calculation explanation | F15 |
| engine and configuration versions | V3 §20, §26.7, ARCH §19 |
| historical comparability metadata | MISSING (F5) |
| pagination and filtering metadata | MISSING |

## Status summary

Backend status after phase 15: everything except reports (16, 17) exists; recommendations are `GET /scoring-runs/{id}/recommendations` (D38). Original classification: EXISTS 9, DERIVABLE 3, MISSING 5 (of the 17 listed), plus the additional dependencies above. Screens that depend only on EXISTS or DERIVABLE endpoints can be built first: score detail, dimension analysis, pattern and penalty detail, imbalances for a run. Dashboard, history, compare and reports remain MISSING.
