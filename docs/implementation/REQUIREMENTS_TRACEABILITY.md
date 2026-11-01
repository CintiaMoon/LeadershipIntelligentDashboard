# Requirements Traceability

**Baseline:** `specs/specv3.md` (V3), which replaces `spec.md` and `specv2.md` (decision D8). `ARCH` = `specs/architect.md`, `FE` = `specs/frontend-spec.md`.
**Status legend:** `NOT_STARTED` implementation not begun; `BLOCKED` waiting on a decision (see `ARCHITECTURE_GAPS.md`); `DEFERRED` consciously out of scope.
All rows are `NOT_STARTED` unless noted. Phases refer to `IMPLEMENTATION_PLAN.md`; frontend components to `docs/frontend/COMPONENT_INVENTORY.md`.
Test IDs: `U-` unit, `I-` integration, `A-` API, `P-` property/invariant (see `TEST_STRATEGY.md`).

## 1. Functional requirements

| ID | Requirement | Spec ref | Backend component | Frontend component | Required tests | Status |
|---|---|---|---|---|---|---|
| R-001 | `/score` carries only `assessmentId`; `/calculate` carries the assessment and answers in its body (D13, D14), the server generates `assessmentId`, validated and persisted as an immutable snapshot; no configuration, tenant or weights in any request | V3 §7.1, §7.2, §7.4, §8.2, D10 | `ScoringController`, `ScoreAssessmentService`, `ScoringCommand` | none | A-request-no-config, A-calculate-validation, U-validate-inputs | DONE (`/score` has no body, `/calculate` takes the assessment and answers; MockMvc-tested, phase 12) |
| R-002 | Normalize SCALE, MULTIPLE_CHOICE, BOOLEAN, NUMBER, TEXT lists to [0,100] via strategy per type | V3 §8.1-§8.4, §8.11, ARCH §8 | `NormalizationService`, 5 strategies | none | U-norm matrix, P-range | DONE (unit-tested, phase 2) |
| R-003 | Scale positive/negative; NUMBER linear/piecewise; explicit out-of-range policy; no silent clamp | V3 §8.5, §8.8 | Scale and Number strategies | none | U-scale boundaries, decimals | DONE (unit-tested, phase 2) |
| R-004 | Question type resolved from configuration; client `questionType` only validated | V3 §4.3, §8.3 | `NormalizationService` | none | U-type-mismatch | DONE (unit-tested, phase 2) |
| R-005 | MC unknown option never 0; BOOLEAN null is missing | V3 §8.6, §8.7 | MC, Boolean strategies | none | U-mc-unknown, U-bool-null | DONE (unit-tested, phase 2) |
| R-006 | TEXT yields deterministic versioned signals only; no numeric score | V3 §8.9 | `TextNormalizationStrategy` | EvidenceDetail | U-text-flags | DONE (unit-tested, phase 2) |
| R-007 | Missing policy per question: IGNORE, NEUTRAL, RISK_SIGNAL, REQUIRED; never zero | V3 §8.10 | `NormalizationService` | none | U-missing-policy, P-missing | DONE (unit-tested, phase 2) |
| R-008 | Signal registry and deterministic derived signals with provenance and correlation groups | V3 §9, §22 | `SignalDerivationService` | EvidenceList | U-signal-derivation | DONE (unit-tested, phase 2) |
| R-009 | Question-to-dimension mapping from configuration; multi-dimension explicit; double-counting controls | V3 §10.2, §10.7 | config model, `DimensionScoringService` | DimensionContributionTable | U-multi-dimension, U-correlated | DONE (unit-tested, phase 3) |
| R-010 | `WeightedAverageStrategy` behind `AggregationStrategy`; only valid contributions in denominator | V3 §10.3, §10.5 | `WeightedAverageStrategy` | none | U-weighted-avg, zero weights | DONE (unit-tested, phase 3) |
| R-011 | Zero denominator gives `INSUFFICIENT_DATA`, not 0 or 50 | V3 §10.4, §39 inv. 24 | `DimensionScoringService` | DimensionScoreCard | U-insufficient, P-no-fabricated-score | DONE (unit-tested, phase 3) |
| R-012 | Coverage = observed / expected weight; per-dimension `requiredCoverage` (sample 0.75) (D18) | V3 §10.6 | `CoverageValidationService` | DimensionScoreCard | U-coverage matrix | DONE (unit-tested, phase 3) |
| R-013 | Contributions retained per input and dimension; normalizedWeight and contribution sum to the dimension score (D15) | V3 §4.5, §19.2 | `DimensionContribution` | DimensionContributionTable | U-contribution-sums | DONE (unit-tested, phase 3) |
| R-014 | Universal directionality: normalization owns direction; dimension scoring never reverses | V3 §3.1, §11 | strategies | none | U-direction | DONE (unit-tested, phase 2) |
| R-015 | Imbalance detection: rule, gap direction, threshold, severity, evidence; diagnostic only | V3 §12, ARCH §14 | `ImbalanceDetectionService` | DimensionImbalanceChart | U-imbalance matrix, P-no-mutation | DONE (unit-tested, phase 6) |
| R-016 | Condition evaluator: 14 operators, nested AND/OR/NOT, NOT arity 1, validation | V3 §13.4, §13.5 | `ConditionEvaluator` | none | U-condition matrix | DONE (unit-tested, phase 4) |
| R-017 | Pattern detection pure on raw dimension scores; statuses MATCHED/NOT_MATCHED/INELIGIBLE/NOT_EVALUATED with reason | V3 §6.2, §13.1, §13.6 | `PatternDetectionService` | PatternExplorer | U-pattern, P-no-mutation | DONE (unit-tested, phase 4) |
| R-018 | Pattern catalog IDs stable; entries disabled until configured and validated | V3 §13.3 | config only | PatternCard | U-config-disabled-default | NOT_STARTED |
| R-019 | Evidence retained per match; confidence = coverage x independence x reliability x freshness, versioned (D17); severity independent | V3 §14 | `PatternConfidenceService` | ConfidenceIndicator, EvidenceList | U-confidence, U-severity-independent | DONE (unit-tested, phase 4) |
| R-020 | Evidence independence and correlation groups | V3 §14.4 | `PatternConfidenceService` | EvidenceList | U-independence | DONE (unit-tested, phase 4) |
| R-021 | Penalty eligibility by evidence and confidence; high severity with low confidence is ineligible | V3 §14.5, §15 | `PenaltyEligibilityService` | PenaltyExplorer | U-eligibility | DONE (unit-tested, phase 5) |
| R-022 | Five penalty strategies (FIXED, PROPORTIONAL, SEVERITY_BASED, THRESHOLD_BASED, CONDITIONAL); explicit score base; targets incl. OVERALL | V3 §15.1, §15.3 | `PenaltyStrategy` impls | PenaltyCard | U-penalty strategies | DONE (unit-tested, phase 5) |
| R-023 | Six stacking policies; deterministic tie-break priority, rule order, penalty ID; versioned | V3 §16.1-§16.3 | `PenaltyAggregationService` | PenaltyExplorer | U-aggregation matrix | DONE (unit-tested, phase 5) |
| R-024 | Terminal state per candidate: APPLIED, SUPPRESSED, INELIGIBLE, NOT_TRIGGERED, each with reason (D7) | V3 §16.4 | `PenaltyAggregationResult` | PenaltyCard | P-terminal-state | DONE (unit-tested, phase 5) |
| R-025 | Suppression retains winner, policy, group, amount, reason, evidence | V3 §16.5, §16.6 | `PenaltySuppression` | DimensionPenaltyList | U-suppression-trace | DONE (unit-tested, phase 5) |
| R-026 | Dimension penalties before overall weighting; overall penalty once; no double application | V3 §6.1, §17.1, §17.3 | `PenaltyAggregationService`, `OverallScoreService` | ScoreCalculationExplanation | P-no-double | DONE (unit-tested, phase 5) |
| R-027 | Adjusted = max(0, raw − effective); LIS = clamp(Σ adjusted×weight − overall, 0, 100); weights sum 1.0 within tolerance | V3 §17.1-§17.4 | `OverallScoreService` | ScoreCompositionChart | U-F-LIS-1 = 69.50, U-weights-invalid | DONE (unit-tested, phase 7) |
| R-028 | Insufficient required dimension gives LIS status `INSUFFICIENT_DATA`; PARTIAL only under an explicit versioned policy | V3 §17.5, §20.2 | `OverallScoreService` | ScoreBadge | U-insufficient-lis, U-partial-policy | DONE (unit-tested, phase 7) |
| R-029 | Interpretation from configured decimal ranges; separate dimension/LIS configs allowed; INSUFFICIENT_DATA not a risk category | V3 §18 | `InterpretationService` | ScoreBadge | U-interpretation, decimal boundaries | DONE (unit-tested, phase 7) |
| R-030 | Explanation built from persisted trace; never recalculates; required data for dimension, pattern, penalty, LIS | V3 §19, ARCH §16 | `ExplainabilityService` | ScoreCalculationExplanation | U-explain-graph, P-no-recalc | DONE (unit-tested, phase 8; API wiring in phase 12) |
| R-031 | Privacy: role-based evidence exposure, data minimization, no raw answers in logs | V3 §19.5, §33.3 | mappers, logging | EvidenceDetail | A-evidence-authz, log test | DONE (VIEWER redaction of evidence and per-question values; logs checked for raw answers) |
| R-032 | Result contract: scoreStatus, status, versions, hashes, dimensions with coverage, overall block, explicit statuses (D11) | V3 §20 | response DTOs | API client | A-contract | DONE (V3 result shape with additive fields, plain decimals, status separate from scoreStatus) |
| R-033 | `BigDecimal` only; HALF_UP; scale 10 for division, 2 dp at defined stages, `roundingPolicyVersion` stored (D16) | V3 §31 | all services | display only (FE §2.6) | U-rounding, build rule | NOT_STARTED |
| R-034 | Value objects enforce invariants (score 0..100, confidence 0..1, penalty ≥ 0, weight ≥ 0) | V3 §21.1 | value objects | none | U-value-objects | NOT_STARTED |
| R-035 | Deterministic: same snapshot, configuration, engine and algorithm version give same canonical result | V3 §1.2, §27.1 | all | none | P-determinism, replay | DONE (engine determinism and shuffle tests, phase 9) |
| R-036 | Configuration externalized, validated, versioned; lifecycle DRAFT→VALIDATING→ACTIVE→PAUSED→ARCHIVED; ACTIVE immutable | V3 §23.4, §23.5 | `ScoringConfigurationService`, `ConfigurationValidationService` | none | U-lifecycle, I-immutability, U-validation | DONE (lifecycle, validation, immutability incl. DB trigger; integration-tested, phase 10) |
| R-037 | Configuration precedence Platform→Template→Organization→Tenant and merge semantics; materialized snapshot | V3 §23.2, §23.3 | `ScoringConfigurationService` | none | U-precedence, U-merge | DONE (precedence and merge, integration-tested, phase 10) |
| R-038 | Activation requires fixtures, preview, approval metadata, hash; failed activation retains errors | V3 §23.6, §42.3 | activation service | none | U-activation | PARTIAL (approval metadata, hash, effective date done; representative fixtures and preview have no spec) |
| R-039 | Configuration cache keyed by tenant and identity, with version and hash; invalidation; no effect on determinism | V3 §24 | Caffeine adapter | none | I-cache, I-cache-isolation | DONE (tenant-keyed Caffeine cache, eviction after commit, scheduled-change staleness, phase 10) |
| R-040 | Persist run: ids, snapshot, engine/algorithm/configuration versions, hashes, input hash, status, score status, result, trace, error | V3 §26.7 | `ScoringRunRepository` adapter | VersionInformation | I-run-persistence | DONE (run, versions, hashes, trace, findings stored; integration-tested, phase 11) |
| R-041 | Idempotency identity (tenant, snapshot, inputHash, configurationHash, engine, algorithm; D12); optional idempotency key; conflict rejected | V3 §7.5, §27.4 | `ScoreAssessmentService` | none | I-idempotency, A-idempotency-conflict | DONE (V3 §27.4 identity, key bound to first request, conflict rejected; API header in phase 12) |
| R-042 | Recalculation creates a new run, never overwrites; optional historical configuration selection | V3 §27.5, §28.5 | `ScoreAssessmentService` | RecalculateAction | I-recalc | DONE (new run each time, explicit version for OWNER/ADMIN is an API check in phase 12) |
| R-043 | Historical runs reproducible after configuration, catalog, penalty, interpretation changes; trace supports audit | V3 §27.6 | snapshot + trace | HistoricalResults | I-replay-after-change | DONE (old runs and configuration versions stay retrievable and unchanged, phase 11) |
| R-044 | Transaction boundaries: failures, partial writes, retries, concurrent recalculation; failed run never shown as valid | V3 §26.9, §29.4 | application service | ErrorState | I-transaction, I-concurrency | DONE (one transaction per write, rollback test, concurrency test, failed runs recorded) |
| R-045 | Endpoints: POST/GET score, POST calculate (D10), recalculate, GET scoring-runs/{id}, /explanation, /dimensions, /patterns, /penalties, /imbalances | V3 §28.1-§28.9 | `ScoringController` | API client | A-endpoints | DONE (9 V3 endpoints plus `/calculate`, phase 12) |
| R-046 | Error format with correlationId; stable error code catalog (34 codes); HTTP mapping | V3 §28.10, §28.11, §29.3 | exception mapper | ErrorState | A-errors | DONE (standard error body with correlationId; status mapping for every code; extension codes INVALID_REQUEST, UNAUTHENTICATED, ACCESS_DENIED) |
| R-047 | Tenant context from security context; isolation across all tenant-owned data; repository tenant scope; optional RLS (D9: enforced) | V3 §25 | `TenantContext`, repositories | none | I-tenant-isolation (V3 §25.6) | DONE (tenant and organization enforced in persistence, services and API; tests in phases 10-12) |
| R-048 | Authentication via platform JWT; authorization per operation | V3 §33.1, §33.2, §25.5 | security config | auth service | A-security | DONE (resource server, role matrix D19, fail-closed decoder; a real key set is deployment configuration) |
| R-049 | Audit events for configuration and scoring actions without raw content | V3 §33.4 | audit adapter | none | I-audit | DONE (configuration actions and explanation reads audited without content, D36) |
| R-050 | Metrics (12), structured logs, tracing; telemetry never influences scoring | V3 §34 | Micrometer | none | I-metrics | DONE (12 metrics, spans, ECS logs, D36; ObservabilityIntegrationTest) |
| R-051 | Stateless engine; bounded queries; performance baseline measured | V3 §35 | adapters | none | I-query-count, perf baseline | DONE (constant statements per run and linear engine cost measured; V3 §35.1 sets no latency target) |
| R-052 | Benchmarking and AI/ML never modify canonical LIS | V3 §36, §1.2, §39 inv. 22-23 | none (boundary) | none | review | DEFERRED (G17) |
| R-053 | Package structure and rules (D31 layered layout): domain imports no other layer or framework, DTOs not domain objects, entities not exposed | V3 §5.1, §37 | package layout | none | ArchUnit | DONE (ArchitectureTest, 8 rules) |
| R-054 | Intended use: reports/APIs state LIS is not an individual rating | V3 §2.4 | response/notice text | Disclaimer in dashboard and reports | A-disclaimer | DONE in the API (`notice` on results, comparison, dashboard, recommendations); the frontend disclaimer remains |
| R-055 | Calibration governance: config carries calibration references; unvalidated defaults labelled | V3 §42 | config metadata | VersionInformation | U-labels | NOT_STARTED |

## 2. Definition of Done (V3 §40)

| # | Item | Maps to | Status |
|---|---|---|---|
| 40.1-1 | Four canonical dimensions | R-009 | NOT_STARTED |
| 40.1-2 | Universal directionality enforced | R-014 | NOT_STARTED |
| 40.1-3 | Five normalization strategies | R-002 | NOT_STARTED |
| 40.1-4 | List-based normalization | R-002 | NOT_STARTED |
| 40.1-5 | Question configuration resolved internally | R-004 | NOT_STARTED |
| 40.1-6 | Deterministic signal derivation | R-008 | NOT_STARTED |
| 40.1-7 | Dimension aggregation | R-010 | NOT_STARTED |
| 40.1-8 | Coverage calculation | R-012 | NOT_STARTED |
| 40.1-9 | Insufficient-data behavior | R-011, R-028 | NOT_STARTED |
| 40.1-10 | Imbalance detection | R-015 | NOT_STARTED |
| 40.1-11 | Pattern evaluation | R-016, R-017 | NOT_STARTED |
| 40.1-12 | Evidence and confidence | R-019 | NOT_STARTED |
| 40.1-13 | All penalty strategies | R-022 | NOT_STARTED |
| 40.1-14 | All stacking policies | R-023 | NOT_STARTED |
| 40.1-15 | Suppression trace | R-025 | NOT_STARTED |
| 40.1-16 | LIS aggregation | R-027 | NOT_STARTED |
| 40.1-17 | Interpretation | R-029 | NOT_STARTED |
| 40.1-18 | Explainability | R-030 | NOT_STARTED |
| 40.2-1 | Configuration stored externally | R-036 | NOT_STARTED |
| 40.2-2 | Versioning | R-036 | NOT_STARTED |
| 40.2-3 | Precedence | R-037 | NOT_STARTED |
| 40.2-4 | Merge semantics | R-037 | NOT_STARTED |
| 40.2-5 | Validation | R-036 | NOT_STARTED |
| 40.2-6 | Lifecycle | R-036 | NOT_STARTED |
| 40.2-7 | Active immutability | R-036 | NOT_STARTED |
| 40.2-8 | Snapshots and hashes persisted | R-040 | NOT_STARTED |
| 40.2-9 | Tenant-safe caching | R-039 | NOT_STARTED |
| 40.3-1 | Assessment snapshots supported | R-001, R-040 | NOT_STARTED |
| 40.3-2 | Scoring runs persisted | R-040 | NOT_STARTED |
| 40.3-3 | Dimension results persisted | R-040 | NOT_STARTED |
| 40.3-4 | Contributions persisted | R-013 | NOT_STARTED |
| 40.3-5 | Patterns and evidence persisted | R-017, R-019 | NOT_STARTED |
| 40.3-6 | Imbalances persisted | R-015 | NOT_STARTED |
| 40.3-7 | Penalty evaluations persisted | R-024 | NOT_STARTED |
| 40.3-8 | Penalty suppressions persisted | R-025 | NOT_STARTED |
| 40.3-9 | Explainability trace persisted | R-030, R-040 | NOT_STARTED |
| 40.3-10 | Idempotency | R-041 | NOT_STARTED |
| 40.3-11 | Recalculation creates new runs | R-042 | NOT_STARTED |
| 40.3-12 | Historical runs retrievable | R-043, R-045 | DONE |
| 40.4-1 | Score endpoint | R-045 | DONE |
| 40.4-2 | Latest-score endpoint | R-045 | DONE |
| 40.4-3 | Scoring-run endpoint | R-045 | DONE |
| 40.4-4 | Explanation endpoint | R-045 | DONE |
| 40.4-5 | Recalculation endpoint | R-042 | NOT_STARTED |
| 40.4-6 | Dimension endpoint | R-045 | DONE |
| 40.4-7 | Pattern endpoint | R-045 | DONE |
| 40.4-8 | Penalty endpoint | R-045 | DONE |
| 40.4-9 | Imbalance endpoint | R-045 | DONE |
| 40.4-10 | Tenant context enforced | R-047 | DONE |
| 40.4-11 | Role-based authorization | R-048 | DONE |
| 40.4-12 | Stable API errors | R-046 | DONE |
| 40.4-13 | OpenAPI published | R-045 | DONE |
| 40.5-1 | Unit tests pass | TEST_STRATEGY §3 | NOT_STARTED |
| 40.5-2 | Integration tests with Testcontainers | TEST_STRATEGY §6 | NOT_STARTED |
| 40.5-3 | End-to-end scoring tests | TEST_STRATEGY §6a | NOT_STARTED |
| 40.5-4 | Tenant isolation tests | R-047 | DONE |
| 40.5-5 | Determinism tests | R-035 | NOT_STARTED |
| 40.5-6 | Reproducibility tests | R-043 | NOT_STARTED |
| 40.5-7 | Flyway migrations validated | IMPLEMENTATION_PLAN §4 | NOT_STARTED |
| 40.5-8 | Structured logging | R-050 | DONE |
| 40.5-9 | Micrometer metrics | R-050 | DONE |
| 40.5-10 | Sensitive data not logged | R-031 | NOT_STARTED |
| 40.5-11 | Performance baseline | R-051 | DONE |
| 40.5-12 | Security review | R-048 | DONE |

## 3. Global invariants (V3 §39), 25 items

| # | Invariant | Tests | Status |
|---|---|---|---|
| 1-3 | Normalized, dimension and LIS values in [0,100] | P-range | NOT_STARTED |
| 4-6 | Deductions non-negative; never raise a score; adjusted ≤ raw | P-penalty-sign, P-adjusted<=raw | NOT_STARTED |
| 7-8 | Pattern and imbalance detection never change a score | P-no-mutation | NOT_STARTED |
| 9-10 | Missing evidence/answers never silently zero | P-missing | NOT_STARTED |
| 11-12 | Every candidate has one terminal status; every suppression is explained | P-terminal-state | NOT_STARTED |
| 13-14 | Dimension and overall penalties applied once | P-no-double | NOT_STARTED |
| 15 | Overall weights satisfy sum constraint | U-weights-invalid | NOT_STARTED |
| 16-17 | Configuration snapshots immutable during scoring; active versions immutable | I-immutability | NOT_STARTED |
| 18 | No cross-tenant leakage | I-tenant-isolation | NOT_STARTED |
| 19-20 | Same inputs and versions give same result; history reproducible | P-determinism, I-replay-after-change | NOT_STARTED |
| 21 | Explanation retrieval does not recalculate | P-no-recalc | NOT_STARTED |
| 22-23 | Benchmarking and AI/ML do not change canonical scores | review | DEFERRED |
| 24 | Insufficient dimension never converted to a numeric score | P-no-fabricated-score | NOT_STARTED |
| 25 | Partial LIS clearly identified | U-partial-policy | NOT_STARTED |

## 4. Frontend-driven requirements (FE)

Detail lives in `docs/frontend/COMPONENT_INVENTORY.md` and `API_DEPENDENCIES.md`.

| ID | FE requirement | FE ref | Backend dependency | Required tests | Status |
|---|---|---|---|---|---|
| R-F01 | Dashboard overview | FE §7 | dashboard endpoint (not in V3), F5, F6 | A-dashboard | NOT_STARTED (dashboard after D27) |
| R-F02 | LIS detail, composition, calculation trace | FE §8 | `/scoring-runs/{id}` and `/explanation` (V3 §28.3, §28.4), weights | A-run, A-explanation | NOT_STARTED |
| R-F03 | Dimension analysis and contributions | FE §9 | `/dimensions` (V3 §28.6) | A-dimensions | NOT_STARTED |
| R-F04 | Pattern explorer and detail with evidence | FE §10 | `/patterns` (V3 §28.7), pagination, filters | A-patterns | NOT_STARTED |
| R-F05 | Penalty explorer, applied and suppressed | FE §9.4, §11 | `/penalties` (V3 §28.8) | A-penalties | NOT_STARTED |
| R-F06 | Imbalance analysis | FE §12 | `/imbalances` (V3 §28.9) | A-imbalances | NOT_STARTED |
| R-F07 | Trends, history, comparison | FE §13 | history/compare (not in V3), V3 §36.3 | A-history | NOT_STARTED (D27) |
| R-F08 | Reports | FE §14 | not in V3 | A-reports | BLOCKED (F13) |
| R-F09 | Assessments, lifecycle, recalculation | FE §15 | `/calculate` (D13), derived list/details, V3 §20.2 statuses | A-assessments | NOT_STARTED |
| R-F10 | Roles, route authorization, 401/403 | FE §5, §19 | roles, JWT | A-security | NOT_STARTED |
| R-F11 | Frontend never calculates scores | FE §2.5, §22.5 | complete backend payloads | frontend lint and tests | NOT_STARTED |
| R-F12 | Accessibility, responsive, performance | FE §20, §21, §23 | none | frontend suites | NOT_STARTED |
| R-F13 | INSUFFICIENT_DATA and PARTIAL display; no fabricated numbers | V3 §20.3 | `scoreStatus`, dimension `status` | frontend tests | NOT_STARTED |
