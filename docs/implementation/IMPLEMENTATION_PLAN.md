# Implementation Plan

**Status:** Draft. All blocking decisions are recorded (D1-D30 in `ARCHITECTURE_GAPS.md`). Implementation starts only on your explicit go-ahead.
**Baseline:** `specs/specv3.md` (V3) replaces spec.md and specv2.md (D8). **Order source:** `specs/architect.md` §35 (20 steps), extended for patterns/penalties (ARCH §37) and the frontend (`docs/frontend/FRONTEND_IMPLEMENTATION_PLAN.md`).

## 1. Decisions applied

| Decision | Effect |
|---|---|
| D9 tenancy enforced (supersedes D1) | `TenantId` in domain and `ScoringContext`; `tenant_id` on every tenant-owned table; `TenantContext` resolved from the JWT/security context; every repository method tenant-scoped; cache keyed by tenant; configuration precedence includes the tenant override; tenant isolation tests mandatory (V3 §25, §38.1). PostgreSQL RLS is optional defense in depth (V3 §25.4). |
| D10, D13, D14 `/calculate` | `POST /api/v1/calculate` takes `{ assessment, inputs }`, the server generates `assessmentId`, persists an immutable snapshot, scores and persists the run; `/score` takes only `assessmentId` and re-scores a persisted assessment. Same role for both. |
| D31 layered architecture | Replaces the hexagonal layout (V3 §5, §37, ARCH ADR-005): packages `api`, `service`, `domain`, `repository`, `config`; no `ports`. |
| D8 V3 is top precedence | All spec references below are V3 sections unless prefixed ARCH or FE. |
| D2 React + Vite | Frontend per FE §3 in a separate `frontend/` module. |
| D3 Maven, `--release 21` | Maven wrapper; installed JDK is 24; Testcontainers on installed Docker. |
| D4 sample config | Illustrative config in `src/test/resources/sample-config/` and a `sample` Spring profile. Never a platform default. All V3 §13.3 and §15.5 catalog IDs stay disabled. |

## 2. Repository layout (proposed)

```text
pom.xml
src/main/java/com/company/leadershipintelligence/    (layered, decision D31)
  api/            controller, request, response, mapper, exception (DTOs only)
  service/        service (ScoreAssessmentService, @Transactional), command, query, mapper
  domain/         model, valueobject, enums, service (orchestrator, normalization,
                  signals, coverage, aggregation, patterns, penalties, imbalance,
                  overall, interpretation, explain), strategy, rule, exception
  repository/     persistence (entity, repository, mapper), configuration, cache,
                  messaging, security
  config/         beans, properties, security
src/main/resources/db/migration/                      Flyway V1..Vn
src/test/java/...  src/test/resources/sample-config/
frontend/                                              React + Vite (separate phase)
docs/implementation/  docs/frontend/
```

Dependencies point downward: `api` -> `service` -> `domain`, and `service` -> `repository` -> `domain`. The `domain` packages import no Spring, JPA, PostgreSQL, Jackson, AWS or Redis types and no other layer of this application (V3 §5.1 domain rules, kept). JPA types live only in `repository`. Enforced by `ArchitectureTest` (7 rules, passing). There are no `ports` interfaces: the `service` layer loads data from `repository` and hands plain domain objects to the engine, so the engine never calls a repository (V3 §22.1).

## 3. Phases

Each phase ends with unit tests green before the next starts (TEST_STRATEGY §10). Steps map to ARCH §35.

| Phase | ARCH §35 steps | Deliverable | Key spec refs | Gate |
|---|---|---|---|---|
| 0 Scaffolding **DONE** | none | Maven project (Java 21 release, Spring Boot 3, JUnit 5, Testcontainers, Flyway, Micrometer, springdoc), ArchUnit rule, CI-ready build | CLAUDE.md "Technology" | build + empty test pass |
| 1 Domain core **DONE** | 1, 2 | enums, value objects with invariants (V3 §21.1), `ScoringInput(questionId, answer)`, `NormalizedInput`, `ScoringContext` as immutable records | V3 §4, §21; ARCH §7 | unit tests |
| 2 Normalization and signals **DONE** | 3, 4, 5 | config model, 5 strategies, `NormalizationService` taking a list, missing-data policies, `SignalDerivationService` | V3 §8, §9; ARCH §8 | normalization matrix (TEST §3); signal content needs G13 |
| 3 Dimensions and coverage **DONE** | 6, 7, 8 | mapping, `WeightedAverageStrategy`, `DimensionScoringService`, `CoverageValidationService`, INSUFFICIENT_DATA, contributions | V3 §10, §11; ARCH §11 | dimension and coverage matrices; decisions D15, D16, D18 |
| 4 Conditions and patterns **DONE** | 10 | `ConditionEvaluator` (14 operators, AND/OR/NOT), `PatternDetectionService`, `PatternConfidenceService` | V3 §13, §14; ARCH §13, §37 | pattern matrix; confidence per D17 |
| 5 Penalties **DONE** | 9 | `PenaltyEligibilityService`, 5 strategies (V3 §15.3), evaluation, aggregation with 6 policies, deterministic tie-break, four terminal states | V3 §15, §16; ARCH §12 | penalty and aggregation matrices |
| 6 Imbalance **DONE** | 11 | `ImbalanceDetectionService` | V3 §12; ARCH §14 | imbalance matrix |
| 7 Overall + interpretation **DONE** | 12, 13 | `OverallScoreService` (INSUFFICIENT_DATA and partial policy), `InterpretationService` | V3 §17, §18; ARCH §15 | F-LIS-1 = 69.50; decimal ranges per D6; coverage per D18 |
| 8 Explainability **DONE** | 14 | `ExplainabilityService` serializing the trace only | V3 §19; ARCH §16 | complete-graph tests; needs F15 |
| 9 Orchestrator **DONE** | 15 | `ScoringOrchestrator` sequencing the 25 steps of V3 §6 | V3 §6, §22; ARCH §6, §37.1 | end-to-end unit test with sample config; determinism/replay |
| 10 Configuration persistence **DONE** | 16 | Flyway schema, JPA adapters, versioned config, lifecycle DRAFT..ARCHIVED, precedence and merge, validation, activation, tenant-keyed Caffeine cache | V3 §23, §24, §30; ARCH §9, §10, §17, §18, §23, §24 | Testcontainers integration |
| 11 Result persistence **DONE** | 17 | `scoring_run` and child tables, snapshot (engine/algorithm/config versions, hashes, input hash), V3 §27.4 idempotency identity | V3 §26, §27; ARCH §19, §20, §21 | reproducibility + transaction tests |
| 12 REST API + security **DONE** | 18 | 9 V3 endpoints (V3 §28.1-§28.9) plus `/calculate`, 34 error codes, JWT, OpenAPI | V3 §28, §29, §33; ARCH §25 | MockMvc tests; roles per D19, idempotency per D22 |
| 13 Integration tests **DONE** | 19 | full integration suite, end-to-end scenario | V3 §38.2, §38.3 | all green |
| 14 Observability **DONE** | 20 | Micrometer metrics, tracing spans, log hygiene, audit events | V3 §33.4, §34; ARCH §29, §37.6 | metric and log tests |
| 15 Frontend-driven backend **DONE** (reports stay blocked, F13) | none | additive response fields (C13); later assessments, history/compare/dashboard/reports after G1/F5/F13 | FE §16 | contract tests; needs F3, C13 |
| 16 Frontend | none | see `docs/frontend/FRONTEND_IMPLEMENTATION_PLAN.md` | FE §24 | per FE phase |

### Phase 13-15 results

- Phase 13 tests: `FlywayMigrationTest` (clean database, 5 migrations in order, idempotent), `ScoringStatementCountIntegrationTest` (15 statements per run for 5 and for 55 answers), `EnginePerformanceBaselineTest` (development machine: 55 questions = 0.77 ms per run, 1005 questions = 4.4 ms per run; linear, V3 §35.1 sets no absolute latency target), `ReproducibilityIntegrationTest` (same facts and trace across tenants and months later), `EndToEndJourneyIntegrationTest` (platform default, tenant override, recalculation, history untouched, other tenant unaffected). The earlier suites already cover the remaining V3 §38.2 items (schema, lifecycle, tenant isolation, rollback, concurrency).
- Phase 14: `StageTimer`, `ObservedStageTimer`, `ScoringMetrics`, `ScoringLog`, `AccessAuditStore`, migration V5 (decision D36).
- Phase 15: `HistoryStore`, `ScoringHistoryService`, `RecommendationService`, `HistoryController` (decisions D37, D38). Reports (F13) remain blocked: V3 does not define them.

## 4. Flyway migration sequence (implemented as V1 core, V2 configuration, V3 one active version, V4 runs and findings; the list below was the proposal)

1. `V1__core_assessment` (`tenant`, `organization`, `assessment`, `assessment_snapshot`, `assessment_answer`, `assessment_template`, `assessment_template_version`; V3 §26.1)
2. `V2__scoring_configuration` (`scoring_configuration`, `scoring_configuration_version` with JSONB payload, status, hash, `scoring_configuration_activation`, `scoring_configuration_audit`; V3 §26.2)
3. `V3__rules` (`scoring_pattern_definition`, `scoring_pattern_rule_version`, `scoring_imbalance_definition`, `scoring_penalty_definition`, `scoring_penalty_rule_version`; V3 §26.4-§26.6, avoiding duplicate representations per V3 §26.8)
4. `V4__scoring_run` (`scoring_run`, `scoring_dimension_result`, `scoring_contribution`, `scoring_signal_result`, `scoring_explanation`; V3 §26.3)
5. `V5__findings` (`scoring_pattern_evaluation`, `scoring_pattern_evidence`, `scoring_imbalance_result`, `scoring_penalty_evaluation`, `scoring_penalty_application`, `scoring_penalty_suppression`, `scoring_penalty_aggregation`; V3 §26.4-§26.6)
6. `V6__constraints_indexes` (uniqueness for the V3 §27.4 idempotency identity, tenant-scoped indexes, check constraints for score ranges and non-negative deductions)

Relational columns: ids, `tenant_id`, version, status, timestamps (V3 §26.2). JSONB: versioned configuration and trace payloads; it never replaces relational identifiers or authorization fields (V3 §26.8). Hibernate `ddl-auto=validate`; Flyway owns DDL.

## 5. Cross-cutting rules (from CLAUDE.md)

- `BigDecimal` only; no `double`/`float`. Rounding policy per D16 (HALF_UP, scale 10 for division, 2 dp at defined stages).
- Configuration over hard-coded rules; no production thresholds invented.
- Orchestrator stays small; specialised services only.
- Active configuration versions immutable; new version for any change.
- Never fix a score by altering output after calculation.
- Hold the stated tie-break: priority, then rule order, then penalty ID.

## 6. Risks

| Risk | Mitigation |
|---|---|
| Engine parameters are unvalidated (D15-D18: weights, 2 dp rounding, confidence factors, 0.75 coverage) | Keep all in versioned configuration; label as unvalidated; calibration per V3 §42 before production. |
| No real question catalog (G9) | Sample config only; engine verified against V3 fixtures. |
| Large frontend-implied API surface (F3) | Phase it; do not implement blocked endpoints. |
| Tenancy scope | Enforcement adds work to phases 10-13 (tenant-scoped repositories, cache, security tests); budget for it from phase 1. |
| `/score` depends on `/calculate` | Assessments are created only by `/calculate` (D13, D14), so `/score` and the read endpoints need snapshots it created. Build `/calculate` first in phase 12. |
| JDK 24 vs 21 | `--release 21`; CI on 21. |

## 7. Not in scope now

Implementation of any phase; edits to `specs/`, `CLAUDE.md` or `.claude/agents`; reports (F13); history and comparison (F5); benchmarking (V3 §36).
