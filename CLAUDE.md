# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Leadership Intelligence Scoring Engine

## Repository State

The specifications and decisions are complete (see `docs/implementation/`).
Implementation is at **phase 15 done: engine, configuration, run persistence, the REST API with JWT security, integration tests, observability (metrics, ECS logs, access audit) and the read endpoints for the frontend; reports are blocked (F13); answers from members, teams and the organization are supported end to end (decision D45); questionnaires authored in a builder and published as tenant template layers, a roster of teams and people, assessment runs and an anonymous respondent flow are built end to end (decisions D47 to D51, `docs/implementation/QUESTIONNAIRES_PROPOSAL.md`, Flyway V1-V9); the frontend (`frontend/`, React + Vite) is complete through phase 5 (foundation, executive dashboard, explainability pages, end-to-end integration, quality and release readiness; see `docs/release/`) and carries the questionnaire, people, run and respondent screens in a restyled dark/light interface; see `docs/frontend/DTO_VIEW_MAPPING.md`** (see the plan): the Maven project (`pom.xml`, Java 21
via `--release 21`, Spring Boot 3.5), the package skeleton under
`src/main/java/com/company/leadershipintelligence/` and an architecture
test exist.

Commands (Maven 3.9, JDK 21+; integration tests need Docker):

``` text
mvn -B verify                              build and run all tests (integration tests start PostgreSQL in Docker)
mvn -B test -Dtest='!*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false   unit tests only, no Docker
mvn -B test -Dtest=ArchitectureTest        run one test class
docker compose up -d --build               local stack: PostgreSQL, mock identity provider (:8081), app (:8080, profile dev)
docker compose up -d postgres mock-oauth2  services only; then mvn spring-boot:run -Dspring-boot.run.profiles=dev
npx newman run docs/postman/lis.postman_collection.json   run every endpoint against the local stack
cd frontend && npm run dev                 web UI on :5173 (proxies /api to :8080); also npm run lint | typecheck | test | build
cd frontend && npm run e2e                 Playwright against the compose stack (web UI on :3000); seeds org-1 and org-2 first
cd frontend && npm run verify              lint, typecheck, unit tests, build and the no-test-code-in-production check
cd frontend && npm run e2e:seed            create the deterministic demo series only (after docker compose down -v for a clean start)
cd frontend && npm run gen:api             regenerate src/types/api.d.ts from the running backend
mvn -B test -Dtest=ArchitectureTest#domainDoesNotDependOnFrameworks   run one test
```

Layout:

``` text
CLAUDE.md                 this file
specs/specv3.md           functional contract (top precedence; replaces spec.md and specv2.md)
specs/architect.md        architecture contract (Sections 13-15 = patterns/imbalance/overall score;
                          Section 37 = pattern and penalty execution architecture;
                          Section 35 = implementation order)
specs/frontend-spec.md    frontend contract
specs/spec.md, specv2.md  superseded, kept for history only
docs/implementation/      plan, traceability, gaps and decisions (D1-D45), API contract, test strategy,
                          RESPONSE_SCOPES_PROPOSAL.md (member/team/organization answers, D45)
docs/frontend/            frontend analysis, plan, API dependencies, components, tests
docs/release/             implementation report, test results, traceability, known limitations, deployment guide
.claude/agents/           backend-engineer, frontend-engineer, ui-engineer subagents
.claude/skills/           api-contracts, flyway-migrations, jpa-hibernate,
                          spring-boot-feature
```

Precedence: `specv3.md` > `architect.md` > `frontend-spec.md` > this file.
References below to `specv3.md` / `architect.md` mean the files in `specs/`.
Decisions that settle spec gaps are recorded in
`docs/implementation/ARCHITECTURE_GAPS.md`; read it before changing scoring
behavior. Use the `.claude/skills/` skills when working on the matching
concern (migrations, JPA, API contracts, Spring features).

## Purpose

This repository contains the deterministic **Leadership Intelligence
Scoring Engine**. It transforms assessment answers into normalized
signals, four leadership dimensions, penalties, organizational patterns,
imbalance indicators, an overall Leadership Intelligence Score (LIS),
and structured explanations.

Dimensions:

-   `TEAM_HEALTH`
-   `DELIVERY_STABILITY`
-   `TECHNICAL_RISK`
-   `LEADERSHIP_LOAD`

Canonical score semantics:

-   `100` = desirable state
-   `0` = undesirable state

## Core Principles

### Deterministic scoring

For identical inputs, configuration version, and engine version, the
engine must return the same result.

### Configuration over hard-coded rules

Do not hard-code question weights, dimension mappings, normalization
mappings, thresholds, penalties, patterns, or interpretation ranges when
they can be represented by versioned configuration.

### Explainability

Every material score must be traceable through:

``` text
input → normalized value → contribution → dimension → penalty/pattern → LIS
```

Explanations must be generated from actual scoring facts. Never
calculate an alternative score inside the explanation layer.

### Separation of concerns

Keep these layers separate (layered architecture, not hexagonal):

``` text
api/         controllers, request/response DTOs, mappers, API exceptions
service/     use cases, @Transactional, commands/queries
domain/      model, value objects, enums, rules, strategies, scoring engine
repository/  JPA entities and repositories, configuration loading, cache, security adapters
config/      Spring beans, properties, security setup
```

Dependencies point down: `api` → `service` → `domain`, and
`service` → `repository` → `domain`. The domain imports no Spring, JPA,
Jackson, AWS or Redis types and no other layer; JPA types live only in
`repository`. There is no `ports` package. `ArchitectureTest` enforces this.

## Technology

Use:

-   Java 21
-   Spring Boot 3
-   Spring Web
-   Spring Data JPA
-   PostgreSQL
-   Jackson
-   Jakarta Validation
-   Spring Security + JWT
-   JUnit 5
-   Mockito where useful
-   Testcontainers
-   Flyway
-   Micrometer
-   OpenAPI/Swagger

Use Caffeine for initial local configuration caching if required. Redis
is a future distributed-cache option.


## Scoring Pipeline

Do not change the scoring sequence without considering the effect on
score semantics:

The full 25-step order is `specv3.md` Section 6; the summary is:

``` text
Resolve tenant context, load assessment snapshot
    ↓
Validate inputs
    ↓
Load configuration (once per run)
    ↓
Normalize answers
    ↓
Derive deterministic signals
    ↓
Validate coverage (INSUFFICIENT_DATA, never a fabricated number)
    ↓
Calculate raw dimension scores
    ↓
Detect imbalances
    ↓
Detect patterns
    ↓
Build pattern evidence and confidence
    ↓
Evaluate penalty eligibility/candidates
    ↓
Aggregate penalties and record suppression
    ↓
Calculate adjusted dimensions
    ↓
Calculate LIS and overall penalties
    ↓
Interpret score
    ↓
Build explanations
    ↓
Persist scoring run
```

## Normalization

`NormalizationService` receives a list:

``` java
List<NormalizedInput> normalize(
    List<ScoringInput> inputs,
    ScoringConfiguration configuration
);
```

Normalization configuration must NOT be supplied by the API request.

Resolve:

``` text
questionId
→ question configuration
→ question type
→ normalization configuration
→ strategy
```

Use the Strategy Pattern:

``` java
public interface NormalizationStrategy {
    boolean supports(QuestionType type);

    NormalizedInput normalize(
        ScoringInput input,
        NormalizationConfig config
    );
}
```

Initial strategies:

``` text
ScaleNormalizationStrategy
MultipleChoiceNormalizationStrategy
BooleanNormalizationStrategy
NumberNormalizationStrategy
TextNormalizationStrategy
```

Use `BigDecimal` for canonical scoring calculations. Do not use `double`
or `float`.

## Dimension Scoring

Question-to-dimension mappings are configuration-driven. A question may
contribute to multiple dimensions.

Default aggregation:

``` text
weighted average =
Σ(value × weight) / Σ(weight)
```

Use:

``` java
public interface AggregationStrategy {
    BigDecimal aggregate(List<WeightedValue> values);
}
```

Initial implementation:

``` text
WeightedAverageStrategy
```

## Penalties and Patterns

Keep pattern detection separate from penalty application and penalty
aggregation:

``` text
Pattern = organizational condition
Penalty = scoring consequence
```

Patterns are configuration-driven and may inspect normalized inputs,
flags, raw dimension scores and dimension gaps. Pattern detection must
not mutate scores. Record evidence, severity and confidence separately.
Only validated ACTIVE versions execute. The catalog and candidate
penalties are specified in `specv3.md` Sections 13 and 15; do not invent
new production thresholds or deductions.

Condition operators must include:

``` text
EQ, NE, GT, GTE, LT, LTE, BETWEEN, IN, NOT_IN,
EXISTS, NOT_EXISTS, CONTAINS, GAP_GT, GAP_GTE
```

Support nested `AND` / `OR` / `NOT`. Validate signal IDs, operand types,
operator arity and missing-value semantics. See `specv3.md` Section 13 for
the canonical pattern catalog and condition contract.

Penalty strategies are FIXED, PROPORTIONAL, SEVERITY_BASED,
THRESHOLD_BASED and CONDITIONAL (`specv3.md` Section 15.3). DIMENSION and
OVERALL are penalty targets, not strategies.

Never allow a final score below `0`. Candidate penalties must be
aggregated with explicit STACK, MAX, MAX_PER_CATEGORY, PRIORITY,
CAPPED_STACK or EXCLUSIVE_GROUP policy. Record APPLIED, SUPPRESSED,
INELIGIBLE and NOT_TRIGGERED outcomes. Apply penalties only after pattern
evaluation and evidence/confidence eligibility. Never double-apply a
dimension penalty as an overall penalty.

## Overall LIS

Calculate the LIS from adjusted dimensions.

Conceptually:

``` text
LIS =
    TeamHealth × W1
  + DeliveryStability × W2
  + TechnicalRisk × W3
  + LeadershipLoad × W4
  - overall penalties
```

Weights are configuration and must sum to `1.0`.

Final LIS must be within `0..100`.

## Platform Configuration File

The platform scoring configuration is one JSON document read at startup by
`InitialConfigurationLoader` (`lis.configuration.initial.enabled|location|mode`). The bundled
`src/main/resources/config/initial-platform-default.json` is an initial, UNVALIDATED proposal
(identifiers and suggested maxima follow `specv3.md` 13.3/15.5; questions, thresholds, caps and
wording were authored for this version); `docs/configuration/INITIAL_CONFIGURATION.md` lists it
and the choices to review. Edit the JSON, never the code, to change a rule; an invalid file stops
startup; an active version is never edited, a change is a new version (`mode=SYNC`). The `dev`
profile uses the separate illustrative sample in `src/main/resources/dev/` instead. Keep catalog
identifiers stable and keep every signal in the 100-is-desirable direction.

## Configuration Versioning

Active configuration versions are immutable.

Every scoring run must retain:

``` text
engineVersion
algorithmVersion
configurationVersion
configurationHash
inputHash
roundingPolicyVersion
```

Never mutate an active configuration in place. Create a new version.

## Persistence

Recommended entities/tables:

``` text
assessment
assessment_answer

scoring_run
scoring_dimension_result
scoring_contribution
scoring_penalty
scoring_pattern_match
scoring_imbalance

scoring_configuration
scoring_configuration_version
scoring_rule
penalty_rule
pattern_rule
imbalance_rule
```

Persistence lives in the `repository` package; JPA types stay there. The
`service` layer calls repositories and passes plain domain objects to the
scoring engine; the engine never calls a repository.

## API

Assessments and answers arrive in the body of `/calculate`; the server
generates the `assessmentId` and persists an immutable snapshot.

``` http
POST /api/v1/calculate
```

``` json
{
  "assessment": {
    "organizationId": "org-001",
    "templateId": "tpl-1",
    "templateVersion": "3",
    "status": "COMPLETED",
    "completedAt": "2026-10-30T12:00:00Z"
  },
  "inputs": [ { "questionId": "Q01", "answer": 4 } ]
}
```

`/score` takes only the assessment id and re-scores a persisted
assessment (no body):

``` http
POST /api/v1/assessments/{assessmentId}/score
GET  /api/v1/assessments/{assessmentId}/score
POST /api/v1/assessments/{assessmentId}/score/recalculate
GET  /api/v1/scoring-runs/{scoringRunId}
GET  /api/v1/scoring-runs/{scoringRunId}/explanation
GET  /api/v1/scoring-runs/{scoringRunId}/dimensions
GET  /api/v1/scoring-runs/{scoringRunId}/patterns
GET  /api/v1/scoring-runs/{scoringRunId}/penalties
GET  /api/v1/scoring-runs/{scoringRunId}/imbalances
```

Read endpoints for the frontend (decisions D37, D38; they only read stored
runs and never score; all tenant- and organization-scoped):

``` http
GET  /api/v1/assessments
GET  /api/v1/assessments/{assessmentId}
GET  /api/v1/scoring-runs                       history with previous score and comparable
GET  /api/v1/scoring-runs/compare?baseRunId=&runId=
GET  /api/v1/scoring-runs/{scoringRunId}/recommendations
GET  /api/v1/organizations/{organizationId}/dashboard
GET  /api/v1/organizations/{organizationId}/trends
GET  /api/v1/scoring-runs/{scoringRunId}/catalog     display text and score bands (D39)
GET  /api/v1/scoring-runs/{scoringRunId}/answers     source answers, OWNER/ADMIN/ANALYST only, audited (D42)
```

Answers may carry a scope (decision D45, `specv3.md` 4.10-4.13): `responses` instead
of `inputs`, each `{questionId, answer, scope: MEMBER|TEAM|ORGANIZATION, teamId?,
respondents?}`. A member is an input scope only: members have no identity, are never
output, ranked or listed one by one, and answers are combined per question (median or
mean of the normalized answers, minimum 5 responses by default). Scoring then stores one
ORGANIZATION run and one TEAM run per team; history, trends, dashboard and comparison
stay organization-level. Do not add a per-member result.

No request may contain scoring configuration, weights, or a trusted
tenant/organization override. A client-supplied `questionType` is only
validated against the configured definition. Optional `Idempotency-Key`
header on scoring requests. Details: `docs/implementation/API_CONTRACT.md`.

## Questionnaires, Roster, Runs and Respondents

Owners and administrators author questionnaires in the builder (`/api/v1/questionnaires`); publishing generates a
validated TEMPLATE layer through the ordinary configuration lifecycle in one transaction, and the questionnaire id is the
template id. Teams and people (`/api/v1/teams`, `/people`) form the roster; an assessment run (`/api/v1/runs`) pins a
published version and its configuration version and snapshots one participant per active person. Respondents hold the
`RESPONDENT` role (deliberately NOT in the `Role` enum, like `PLATFORM_ADMIN`), are matched to the roster by the `email`
claim, and reach only `/api/v1/respond/**`. Submitted answers are stored with the team and the question only (no person,
no time, random id; a test asserts the columns); closing a run scores them once as MEMBER answers with the pinned
configuration. Free text is collected and never scored and is not part of the generated layer. Never add a column that
links an answer to a person, and keep the completion list free of answers.

## Platform Administration

`PLATFORM_ADMIN` (decision D46) creates and lists tenants through `/api/v1/platform/**` and the
Tenants screen. It belongs to no tenant (no `tenant_id` in its token), reaches nothing
tenant-scoped, and is deliberately NOT a value of the `Role` enum: the "every role may read" rules
are built from that enum. The tenant roles cannot reach these endpoints. A tenant is an id
(permanent) and a name only. Creation and list reads are audited.

## Multi-Tenancy

Tenancy is enforced (`specv3.md` Section 25). The tenant comes only from
the authenticated security context (`tenant_id` claim); organizations
must be in `org_ids`. All tenant-owned repository methods take a tenant
scope, configuration caches are keyed by tenant, and configuration
precedence is Platform default → Template → Organization → Tenant.
Tenant isolation tests are mandatory.

## Testing

Every scoring service must have unit tests.

Normalization tests must include:

-   positive and negative scales;
-   boundaries and decimals;
-   multiple choice;
-   boolean;
-   number;
-   text flags;
-   missing configuration;
-   unsupported types;
-   null answers;
-   invalid values;
-   mixed question types;
-   multiple strategies in one list.

Also test dimension scoring, coverage and insufficient data, penalties
(every strategy and stacking policy), patterns, imbalances, overall
scoring, interpretation, explainability, tenant isolation, and
configuration validation.

Use PostgreSQL Testcontainers for integration tests.

## Coding Rules

Prefer immutable records/value objects.

Avoid mutable global scoring state.

Avoid `Map<String, Object>` as the main scoring state.

Avoid God services.

The orchestrator coordinates specialized services:

``` text
ScoringOrchestrator
 ├── NormalizationService
 ├── DimensionScoringService
 ├── ImbalanceDetectionService
 ├── ConditionEvaluator
 ├── PatternDetectionService
 ├── PatternConfidenceService
 ├── PenaltyEvaluationService
 ├── PenaltyAggregationService
 ├── OverallScoreService
 ├── InterpretationService
 └── ExplainabilityService
```

## AI/ML Boundary

Canonical scoring remains deterministic.

Preferred:

``` text
Assessment
    ↓
Deterministic Scoring Engine
    ↓
LIS + Patterns + Explainability Facts
    ↓
Analytics / AI
```

AI may generate summaries, recommendations, narratives, and trend
explanations, but must not silently modify the canonical LIS.

## Safe Change Workflow

Before changing scoring behavior:

1.  Read `specv3.md` and `docs/implementation/ARCHITECTURE_GAPS.md`.
2.  Read `architect.md`.
3.  Identify affected domain services.
4.  Identify configuration changes.
5.  Identify persistence/API impact.
6.  Add regression tests.
7.  Implement the smallest coherent change.
8.  Run unit tests.
9.  Run integration tests when applicable.
10. Verify deterministic behavior.
11. Verify explainability.

If changing a formula or rule, explicitly consider historical
reproducibility.

## Important Rule

Never fix a score by changing the output after calculation.

If a score is incorrect:

``` text
Find source defect
    ↓
Fix rule/configuration/calculation
    ↓
Add regression test
    ↓
Recalculate using an explicit version
```


## Pattern and Penalty Implementation Guardrails

Treat `specv3.md` Sections 13–16 as the canonical functional contract and
`architect.md` Sections 13–15 and 37 as the architecture contract.
If these documents conflict, stop and report the inconsistency; do not
silently choose one or invent a third behavior.

- Keep the catalog IDs stable (`TH-*`, `DS-*`, `TR-*`, `LL-*`, `XD-*`
  and corresponding penalty IDs). Do not rename or reuse IDs.
- Catalog entries are candidate taxonomy, not executable rules until
  conditions, signals, evidence requirements and thresholds are defined
  and validated. Suggested deduction caps are not automatic deductions.
- Do not claim thresholds, severity mappings or confidence bands are
  empirically calibrated. Keep them configurable and label unvalidated
  defaults clearly.
- A pattern match is not a penalty. Pattern detection is pure and cannot
  alter a score. Penalty eligibility, calculation and aggregation are
  separate stages/services.
- Use raw dimension scores for pattern evaluation by default. Avoid
  circular evaluation from adjusted scores unless a versioned rule
  explicitly requires it.
- Never treat missing/null data as zero. Reject unknown signals and
  type-incompatible expressions during configuration validation.
- Confidence is evidence quality; severity is impact. Never substitute
  one for the other. Record evidence references and explain ineligibility.
- All stacking policies require deterministic tie-breaking. Never depend
  on collection/database iteration order.
- Every candidate penalty must have a terminal state: APPLIED,
  SUPPRESSED, INELIGIBLE or NOT_TRIGGERED. Explain suppressed amounts.
- Preserve raw, candidate, effective, suppressed and adjusted values.
  Never apply a deduction twice or allow penalties to increase scores.
- Active configuration versions are immutable. Persist exact pattern,
  penalty and scoring configuration versions plus hashes with each run.
- Explainability serializes the calculation trace; it must not recalculate
  or infer evidence.
- Do not add every catalog item to the first release by default. Enable
  only rules backed by available assessment signals and approved fixtures.

Required tests include nested AND/OR/NOT, missing data, invalid types,
confidence/evidence gates, every penalty strategy, every stacking policy,
threshold boundaries, suppression, overlap, deterministic
replay and no-double-application invariants.

