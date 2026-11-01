# Test Strategy (Backend)

**Baseline:** `specs/specv3.md` (V3, replaces spec.md and specv2.md). **Sources:** V3 §38, §39, §40; ARCH §26, §27; CLAUDE.md "Testing". Frontend tests: `docs/frontend/TEST_STRATEGY.md`.

## 1. Principles

- The domain/engine is tested without Spring (ARCH §26). Unit tests for every service.
- Integration tests use Spring Boot + PostgreSQL Testcontainers (Docker is available). No in-memory substitute for persistence tests.
- Calculations use `BigDecimal`; assertions compare with explicit scale and `compareTo`, never `double`.
- Fixtures are V3's own examples (V3 §8.5-§8.9, §12.1, §13.4, §15.4, §17.6, §18). They are labelled **sample, unvalidated** and are never platform defaults.
- A regression test accompanies every fix (CLAUDE.md "Important Rule").

## 2. Canonical fixtures

| Fixture | Source | Expected |
|---|---|---|
| F-LIS-1 | V3 §17.6: raw 84/76/66/70, weights 0.25, penalties 3/0/8/7 | adjusted 81/76/58/63; LIS 69.50; interpretation HEALTHY. Raw gap DS−TR = 10 |
| F-LIS-2 | Legacy spec.md §30 (superseded): raw 82/76/64/58, penalties TR 5, LL 4 | adjusted 82/76/59/54; LIS 67.75. Kept as a second regression case only |
| F-NORM-1 | V3 §8.5-§8.7 SCALE 1-5 positive/negative, MC map, BOOLEAN | per V3 §8.5 formulas |
| F-IMB-1 | V3 §12.1 (82/79/34/38) and ARCH §14 (85 vs 48, gap 37) | gap and severity from rule |
| F-PAT-1 | V3 §13.4 TH-001 example (AND of two signals, minimumSignals 2, minimumConfidence 0.70) | MATCHED / NOT_MATCHED / INELIGIBLE / NOT_EVALUATED |
| F-PEN-1 | V3 §15.4 TH-P01 example (SEVERITY_BASED, base 12, cap 12) | per severity multiplier, cap 12 |
| F-INT-1 | V3 §18 ranges + decimals | decimal boundaries per D6 (39.99 and 40.00 map to different labels; 39.5 maps to the lowest range; 100 maps to the last range) |

## 3. Unit test matrix

| Service | Cases (V3 reference) |
|---|---|
| `NormalizationService` + 5 strategies | positive and negative scale; min/max boundaries; decimals; multiple choice; boolean; number; text flags; missing configuration; unsupported type; null answers; invalid values; mixed types in one list; several strategies in one list (V3 §38.1, CLAUDE.md) |
| `SignalDerivationService` | valid derivation; multiple source inputs; missing sources; invalid source types; correlation group kept; deterministic; versioned (V3 §38.1) |
| `CoverageValidationService` | complete; minimum boundary; insufficient; missing required dimension; excluded questions; no silent zero imputation; partial policy (V3 §38.1) |
| `AggregationStrategy` / `WeightedAverageStrategy` | weighted average; zero weights; sum of weights = 0 gives INSUFFICIENT_DATA (V3 §10.4); precision |
| `DimensionScoringService` | single and multiple contributions; question in several dimensions; mixed answer types; bounds 0..100; empty input; decimal precision; contributions sum exactly to the dimension score (D15); excluded inputs carry no contribution; 80/60 with weights 0.7/0.3 gives 56/18 and 74; missing 0.3 input gives 80 |
| `ConditionEvaluator` | all 14 operators; nested AND/OR/NOT; NOT arity = 1; unknown signal; missing/null never coerced to zero; type mismatch; BETWEEN inclusivity; GAP_* direction (V3 §13.4, §38.1, §39) |
| `PatternDetectionService` | not matched; single and multi-condition; threshold boundaries; multiple and overlapping patterns; inactive versions; insufficient signals; detection does not mutate scores; deterministic |
| `PatternConfidenceService` | deterministic; range [0,1]; product of coverage, independence, reliability, freshness (D17); two signals in one correlation group count once; unknown freshness gives 1 with a trace note; keyword text flag uses lower reliability; eligibility needs both minimumConfidence and minimumSignals; severity independent of confidence; band edges 0.39/0.40, 0.59/0.60, 0.79/0.80 |
| `PenaltyEvaluationService` + `PenaltyStrategy` | FIXED, PROPORTIONAL, SEVERITY_BASED, THRESHOLD_BASED, CONDITIONAL (V3 §15.3); targets TEAM_HEALTH..OVERALL; exact boundaries; severity multipliers; caps; zero and max; negative/non-finite rejected; confidence/evidence ineligibility; inactive rule; not triggered; rounding stage |
| `PenaltyAggregationService` | STACK, MAX, MAX_PER_CATEGORY, PRIORITY, CAPPED_STACK, EXCLUSIVE_GROUP; priority ties (priority, then rule order, then penalty ID); suppression trace with winner and amount; dimension vs overall; no double application; terminal state for every candidate |
| `ImbalanceDetectionService` | none; exact threshold; exceeded; several gaps; several rules |
| `OverallScoreService` | default and custom weights; invalid weights; penalty application; clamp 0..100; deterministic; insufficient dimension gives INSUFFICIENT_DATA LIS; partial policy; penalties applied exactly once |
| `InterpretationService` | each range; boundaries; decimals (D6); overlapping or missing ranges rejected; INSUFFICIENT_DATA; configuration version |
| `ExplainabilityService` | contribution, penalty, pattern, imbalance explanations; complete graph; never recalculates (assert against trace only) |
| Configuration validation | scale min < max; weights >= 0; overall weights sum 1.0 within tolerance; penalties >= 0; limits; interpretation coverage and overlap; unknown references; cyclic references; ambiguous thresholds; invalid lifecycle transitions; ACTIVE immutable |

## 4. Property and invariant tests

From V3 §39 and ARCH §27, run over generated inputs:

- normalized input, dimension score and LIS always in [0, 100];
- penalties are >= 0 and never increase a score; adjusted <= raw;
- detection alone changes no score;
- same inputs + engine version + configuration snapshot give byte-equivalent canonical output (excluding generated IDs and timestamps);
- missing evidence is never read as zero;
- every candidate penalty ends in exactly one of APPLIED, SUPPRESSED, INELIGIBLE, NOT_TRIGGERED;
- monotonicity: a higher normalized input with a positive weight never lowers its contribution (ARCH §27);
- results stay reproducible after a rule change (run old snapshot against new catalog).

## 5. Determinism and replay

- Same-input replay across two JVM runs and across shuffled input / collection order.
- No reliance on `HashMap` iteration or database row order in aggregation.
- Persisted trace replays to the same canonical result.
- Rounding (D16): scale 10 HALF_UP on division; dimension scores rounded once to 2 dp; downstream stages use rounded values; `.005` boundaries; a 59.996 raw value becomes 60.00 and receives the 60.00 label; replay gives identical output; `roundingPolicyVersion` persisted.

## 6. Integration tests (Testcontainers PostgreSQL)

Configuration loading; JSON configuration deserialization; scoring execution; scoring-run persistence including engine/configuration versions, hashes and input hash; historical retrieval; configuration versioning (new version never mutates ACTIVE); recalculation creates a new run; idempotent reuse; reproducibility; transaction behavior (rollback leaves no partial run); Flyway migrations on a clean database.

**Tenancy (D9, enforced):** V3 §25.6 tests are mandatory: a tenant cannot score another tenant's assessment, retrieve its result, access its configuration, read its explanation, or use its cache entry; missing tenant context and tenant mismatch are rejected; every repository method is tenant-scoped (ArchUnit or query test); configuration precedence tenant > organization > template > platform default; cache isolation.

## 6a. End-to-end (V3 §38.3)

One scenario through snapshot retrieval, configuration resolution, normalization, signal derivation, coverage, dimensions, imbalance, patterns, evidence and confidence, penalty eligibility, calculation and aggregation, adjusted dimensions, LIS, interpretation, explainability, persistence and API retrieval, using the sample configuration (D4) and fixture F-LIS-1.

## 7. API tests

MockMvc: `/score` rejects an `inputs` body; `/calculate` accepts the assessment and answers, persists an immutable snapshot, and rejects unknown or foreign questions, wrong types, duplicates, missing required answers, an incomplete assessment, a foreign `organizationId`, and any configuration or tenant field; the server generates `assessmentId` and rejects a client-supplied one; two calls with different answers create two assessments; an `Idempotency-Key` retry returns the same run and a mismatched body with the same key returns 409; `/score` of a persisted assessment reproduces the `/calculate` result; success and each of the 34 error codes (V3 §28.11); 400/401/403/404/409/422; request containing configuration is rejected; `questionType` supplied by a client is validated, not trusted; idempotency key reuse with a different request returns 409; INSUFFICIENT_DATA is returned as `scoreStatus`, not an error; `correlationId` in errors; entity never serialized; error body never leaks internals; no raw answers in logs.

## 8. Security tests

JWT required on all endpoints; role matrix per D19 (each role against each operation); VIEWER receives redacted evidence; organization outside `org_ids` rejected (D20); recalculate with a configuration version allowed only for OWNER/ADMIN and only for ACTIVE/ARCHIVED versions of the tenant (D21); idempotency key per D22; comparable flag and delta per D27; trace conforms to the D24 schema; no tenant/role taken from the request body; log inspection test for raw-answer leakage (V3 §33.3).

## 9. Non-functional

Single scoring run performs a bounded number of queries (no query per question, ARCH §28). Metrics from ARCH §29 / V3 §34 emitted. O(n) behavior checked with a size test.

## 10. Exit gates

- Unit tests green before the next implementation phase starts.
- Integration suite green on Docker before API work is called done.
- No test is reported as passing unless it was run; skipped tests are listed.
