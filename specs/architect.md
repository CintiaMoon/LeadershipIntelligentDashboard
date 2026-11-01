# Scoring Engine Architecture

**Version:** 1.0\
**Status:** Draft for implementation\
**Related documents:** `spec.md`, `CLAUDE.md`

## 1. Architectural Vision

The Scoring Engine is a deterministic, configuration-driven domain
runtime for the Leadership Intelligence platform.

It transforms assessment inputs into:

``` text
Normalized Inputs
      ↓
Dimension Scores
      ↓
Patterns / Imbalances / Penalties
      ↓
Leadership Intelligence Score
      ↓
Interpretation + Explanation
```

The central principle is:

> **The engine executes scoring behavior; configuration defines scoring
> behavior.**

## 2. Architecture Style

Use:

-   Clean Architecture;
-   domain-driven design principles;
-   Strategy Pattern;
-   configuration-driven rules.

High-level:

``` text
                  +----------------------+
                  |       REST API       |
                  +----------+-----------+
                             |
                             v
                  +----------------------+
                  |      Service Layer   |
                  +----------+-----------+
                             |
                             v
                  +----------------------+
                  |       Domain         |
                  |   Scoring Engine     |
                  +----------+-----------+
                             |
                             v
                  +----------------------+
                  |       Persistence    |
                  |         Layer        |
                  +----------+-----------+
                             |
                             v
                  +----------------------+
                  |    JPA/PostgreSQL    |
                  +----------------------+
```

## 3. Dependency Direction

``` text
API
 ↓
Service
 ↓
Persistence
 ↓
Domain
```

The domain must not depend on:

``` text
PostgreSQL
AWS
```

## 4. Runtime Flow

``` text
Client
  |
  v
ScoringController
  |
  v
ScoringApplicationService
  |
  +---- AssessmentProvider
  |
  +---- ConfigurationProvider
  |
  v
ScoringOrchestrator
  |
  +---- NormalizationService
  +---- DimensionScoringService
  +---- ImbalanceDetectionService
  +---- ConditionEvaluator
  +---- PatternDetectionService
  +---- PatternConfidenceService
  +---- PenaltyEvaluationService
  +---- PenaltyAggregationService
  +---- OverallScoreService
  +---- InterpretationService
  +---- ExplainabilityService
  |
  v
ScoringResult
  |
  v
ScoringRunRepository
  |
  v
PostgreSQL
```

## 5. Service Layer

The Service layer owns use cases and transactions.

Primary service:

``` java
public interface ScoreAssessmentService {
    ScoringResult execute(ScoreAssessmentCommand command);
}
```

The service:

1.  resolves tenant context;
2.  loads assessment data;
3.  loads scoring configuration;
4.  executes the scoring engine;
5.  persists the result;
6.  optionally publishes an event.

It does not implement individual scoring formulas.

## 6. Scoring Orchestrator

The orchestrator coordinates:

``` text
Validate
  ↓
Resolve configuration
  ↓
Normalize
  ↓
Calculate dimensions
  ↓
Detect imbalances
  ↓
Detect patterns
  ↓
Build evidence and confidence
  ↓
Evaluate penalty eligibility/candidates
  ↓
Aggregate penalties and record suppression
  ↓
Calculate adjusted dimensions
  ↓
Calculate overall score and overall penalties
  ↓
Interpret
  ↓
Explain
```

The orchestrator must remain small and focused on sequencing.

## 7. Core Domain Model

``` text
ScoringContext
ScoringInput
NormalizedInput
DimensionContribution
DimensionScore
PenaltyResult
PatternMatch
ImbalanceResult
OverallScore
ScoreInterpretation
ScoringExplanation
ScoringResult
```

Use immutable records/value objects where appropriate.

Example:

``` java
public record ScoringContext(
    TenantId tenantId,
    OrganizationId organizationId,
    AssessmentId assessmentId,
    String templateVersion,
    String configurationVersion,
    List<ScoringInput> inputs
) {}
```

## 8. Normalization Architecture

``` text
                 NormalizationService
                         |
                         v
                NormalizationStrategy
                         |
        +----------------+----------------+
        |        |        |       |       |
        v        v        v       v       v
      Scale    MC      Boolean Number    Text
```

Interface:

``` java
public interface NormalizationStrategy {

    boolean supports(QuestionType type);

    NormalizedInput normalize(
        ScoringInput input,
        NormalizationConfig config
    );
}
```

Initial implementations:

``` text
ScaleNormalizationStrategy
MultipleChoiceNormalizationStrategy
BooleanNormalizationStrategy
NumberNormalizationStrategy
TextNormalizationStrategy
```

`NormalizationService` receives a list of inputs and resolves
configuration internally.

## 9. Configuration Resolution

Configuration precedence:

``` text
Tenant
 ↓
Organization
 ↓
Assessment Template
 ↓
Platform Default
```

Use:

``` java
public interface ScoringConfigurationProvider {
    ScoringConfiguration getConfiguration(ScoringContext context);
}
```

Configuration is loaded once per scoring operation and then used in
memory.

## 10. Configuration Cache

Initial option:

``` text
Caffeine
```

Cache key:

``` text
scoring-config:{tenantId}:{configurationVersion}
```

Configuration objects should be immutable.

Redis can be introduced later for distributed caching.

## 11. Dimension Scoring

The four initial dimensions:

``` text
TEAM_HEALTH
DELIVERY_STABILITY
TECHNICAL_RISK
LEADERSHIP_LOAD
```

Question-to-dimension mappings are configuration-driven.

A question may contribute to multiple dimensions.

Aggregation abstraction:

``` java
public interface AggregationStrategy {
    BigDecimal aggregate(List<WeightedValue> values);
}
```

Initial strategy:

``` text
WeightedAverageStrategy
```

Formula:

``` text
Σ(value × weight)
-----------------
     Σ(weight)
```

## 12. Penalty Architecture

``` text
Raw Dimension Scores
        ↓
Penalty Rule Evaluation
        ↓
Adjusted Dimension Scores
```

Keep the following concerns separate:

``` text
Pattern Detection
Penalty Application
```

A pattern is an organizational finding.

A penalty is a scoring consequence.

Penalty strategies are `FIXED`, `PROPORTIONAL`, `SEVERITY_BASED`,
`THRESHOLD_BASED`, `DIMENSION`, `OVERALL` and `CONDITIONAL`.
Candidate evaluation and aggregation are separate. Aggregation supports
`STACK`, `MAX`, `MAX_PER_CATEGORY`, `PRIORITY`, `CAPPED_STACK` and
`EXCLUSIVE_GROUP`. Every candidate must be recorded as APPLIED,
SUPPRESSED, INELIGIBLE or NOT_TRIGGERED. See the canonical catalog,
condition/evidence contract and suggested (uncalibrated) deduction caps
in `spec.md` Section 39. No score may become negative.

## 13. Pattern Architecture

``` text
Normalized Inputs
       +
Dimension Scores
       +
Detected Flags
       |
       v
Pattern Rules
       |
       v
Pattern Matches
```

Patterns are configuration-driven.

Use a reusable condition evaluator supporting:

``` text
EQ
NE
GT
GTE
LT
LTE
BETWEEN
EXISTS
NOT_EXISTS
CONTAINS
GAP_GT
GAP_GTE
```

Composite `AND` / `OR` / `NOT` conditions are required. Validate signal
identifiers, operand types, operator arity, missing-data semantics and
threshold boundaries. Pattern matches carry evidence, severity and
confidence; confidence must not be conflated with severity. The initial
pattern catalog is in `spec.md` Section 39.2.

## 14. Imbalance Architecture

``` text
Dimension Scores
      |
      v
Gap Calculation
      |
      v
Imbalance Rules
      |
      v
Imbalance Results
```

Example:

``` text
Delivery Stability = 85
Technical Risk     = 48
Gap                = 37
```

Threshold and severity are configuration.

## 15. Overall Score

``` text
Raw Dimension Scores
        ↓
Dimension Penalties
        ↓
Adjusted Dimension Scores
        ↓
Weighted Overall Score
        ↓
Overall Penalties
        ↓
Leadership Intelligence Score
```

Overall dimension weights are configuration and must sum to `1.0`.

Use `BigDecimal`.

Final LIS:

``` text
0..100
```

## 16. Explainability

Explainability is a projection of actual scoring facts:

``` text
Contributions
Penalties
Patterns
Imbalances
Dimension Scores
      |
      v
ExplainabilityService
      |
      v
ScoringExplanation
```

The explanation service must never recalculate the canonical score.

## 17. Persistence Architecture

Persistence is accessed through output ports.

``` java
public interface ScoringRunRepository {
    ScoringRun save(ScoringRun run);

    Optional<ScoringRun> findById(
        TenantId tenantId,
        ScoringRunId id
    );
}
```

Adapter:

``` text
ScoringRunRepository
        ^
        |
JpaScoringRunRepositoryAdapter
        |
        v
Spring Data JPA
        |
        v
PostgreSQL
```

JPA entities remain in infrastructure.

## 18. Persistence Model

Recommended tables:

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

Use JSONB for flexible configuration payloads.

Keep tenant IDs, identifiers, versions, status and timestamps
relational.

## 19. Scoring Snapshot

A scoring run must retain:

``` text
tenantId
assessmentId
engineVersion
configurationVersion
inputHash
createdAt
status
```

and:

``` text
raw dimension scores
adjusted dimension scores
contributions
penalties
patterns
imbalances
LIS
interpretation
```

This is required for historical reproducibility.

## 20. Idempotency

Recommended identity:

``` text
tenantId
+
assessmentId
+
inputHash
+
configurationVersion
+
engineVersion
```

Identical requests can reuse an existing run.

Explicit recalculation must be able to create a new run.

## 21. Transaction Boundary

The application service owns the transaction:

``` text
@Transactional
scoreAssessment()
    |
    +-- load assessment
    +-- load configuration
    +-- execute engine
    +-- persist scoring result
```

The domain engine is transaction-unaware.

## 22. Multi-Tenancy

Tenant context:

``` text
JWT
 ↓
SecurityContext
 ↓
TenantContext
 ↓
ScoringContext
```

Every repository operation must enforce tenant isolation.

Tenant A must never access Tenant B data or configuration.

## 23. Configuration Lifecycle

``` text
DRAFT
  ↓
VALIDATING
  ↓
ACTIVE
  ↓
PAUSED
  ↓
ARCHIVED
```

Active configurations are immutable.

A change creates a new configuration version.

## 24. Configuration Validation

Validate:

-   referenced questions;
-   referenced dimensions;
-   normalization ranges;
-   dimension weights;
-   overall weights;
-   penalties;
-   pattern conditions;
-   interpretation ranges;
-   rule references.

Invalid configuration cannot become active.

## 25. API Architecture

Primary endpoint:

``` http
POST /api/v1/assessments/{assessmentId}/score
```

Additional:

``` http
GET  /api/v1/assessments/{assessmentId}/score
GET  /api/v1/assessments/{assessmentId}/score/explanation
POST /api/v1/assessments/{assessmentId}/score/recalculate
```

Controllers are adapters and must not contain scoring logic.

## 26. Testing Architecture

### Unit tests

The domain must be testable without Spring.

Cover:

``` text
normalization
aggregation
dimension scoring
penalties
patterns
imbalances
interpretation
overall scoring
explainability
configuration validation
```

### Integration tests

Use:

``` text
Spring Boot + PostgreSQL Testcontainers
```

Verify:

-   configuration loading;
-   tenant isolation;
-   scoring persistence;
-   configuration versioning;
-   historical retrieval;
-   recalculation;
-   reproducibility.

### API tests

Use MockMvc or WebTestClient.

## 27. Mathematical Invariants

Always preserve:

``` text
0 <= normalizedValue <= 100
0 <= dimensionScore <= 100
0 <= LIS <= 100
```

For positive questions:

``` text
higher normalized input
→
must not reduce direct contribution
```

For penalties:

``` text
penalty >= 0
→
adjusted score <= raw score
```

## 28. Performance

Preferred execution model:

``` text
Load assessment
Load complete configuration
        ↓
Execute scoring in memory
        ↓
Persist one scoring snapshot
```

Do not perform one database query per question.

The application should be stateless and horizontally scalable.

## 29. Observability

Metrics:

``` text
scoring_requests_total
scoring_success_total
scoring_failure_total
scoring_duration_ms
scoring_configuration_load_ms
scoring_normalization_errors_total
scoring_patterns_detected_total
scoring_penalties_applied_total
```

Tracing:

``` text
score-assessment
load-configuration
normalize-inputs
calculate-dimensions
detect-imbalances
evaluate-penalties
detect-patterns
calculate-overall
persist-result
```

Do not log raw assessment text.

## 30. Events and Async Execution

Initial execution can be synchronous.

Future events:

``` text
AssessmentScored
ScoreRecalculated
ScoringConfigurationActivated
PatternDetected
HighRiskDetected
```

Future architecture:

``` text
API
 ↓
Queue
 ↓
Scoring Worker
 ↓
Scoring Engine
 ↓
PostgreSQL
 ↓
Event Bus
```

The domain engine should remain unchanged.

## 31. AI/ML Boundary

AI and ML are downstream:

``` text
Assessment
    ↓
Deterministic Scoring
    ↓
LIS + Patterns + Explainability
    ↓
Analytics / AI
```

AI can produce narratives, recommendations, summaries, and trend
explanations.

It must not silently modify the canonical LIS.

## 32. Benchmarking Boundary

Benchmarking consumes immutable scoring results:

``` text
Scoring Result
     |
     +----> Benchmarking
     +----> Analytics
     +----> Dashboard
```

Benchmarking should not modify canonical scores.

## 33. Deployment

Initial:

``` text
Internet
   |
   v
API Gateway / Load Balancer
   |
   v
Spring Boot Application
   |
   +--------+--------+
   |                 |
   v                 v
PostgreSQL        Cache
```

Future AWS:

``` text
Route 53
   |
API Gateway / ALB
   |
ECS/EKS
   |
+--+----------------+
|                   |
Aurora PostgreSQL  ElastiCache
```

Asynchronous:

``` text
API → SQS → Scoring Workers → Aurora → EventBridge/SNS
```

## 34. Architectural Decisions

### ADR-001 --- Deterministic canonical score

The canonical LIS is deterministic for auditability and reproducibility.

### ADR-002 --- Externalized scoring configuration

Business scoring rules are versioned configuration so the model can
evolve without rewriting the engine.

### ADR-003 --- Strategy-based normalization

Answer normalization uses strategies to isolate answer semantics and
allow new question types.

### ADR-004 --- Immutable active configuration

Active configuration versions are immutable to preserve historical
scoring.

### ADR-005 --- Persistence behind ports

JPA is an infrastructure adapter to preserve domain independence.

### ADR-006 --- AI downstream from canonical scoring

AI can enrich scoring but does not silently modify the canonical score.

## 35. Implementation Order

``` text
1. Domain enums/value objects
2. ScoringInput / NormalizedInput
3. Normalization configuration
4. Normalization strategies
5. NormalizationService
6. Dimension mappings
7. WeightedAverageStrategy
8. DimensionScoringService
9. Penalty model/service
10. Pattern model/service
11. Imbalance detection
12. OverallScoreService
13. InterpretationService
14. ExplainabilityService
15. ScoringOrchestrator
16. Configuration persistence
17. Scoring result persistence
18. REST API
19. Integration tests
20. Observability
```

## 36. Final Architecture

The Scoring Engine should remain a compact deterministic domain runtime:

``` text
                    +--------------------+
                    |  Scoring Engine    |
                    |                    |
Inputs ------------>| Normalize          |
Configuration ----->| Calculate          |
                    | Detect             |
                    | Penalize           |
                    | Aggregate          |
                    | Explain            |
                    +---------+----------+
                              |
                              v
                     ScoringResult
```

Everything else surrounds this core:

``` text
        REST / Security
              |
              v
       Service Layer
              |
              v
       +--------------+
       | Scoring Core |
       +--------------+
              |
       +------+------+------+
       |      |      |      |
       v      v      v      v
    Database Cache Events Analytics
                              |
                              v
                           AI / ML
```

The architecture allows the Leadership Intelligence model to evolve
without turning the scoring engine into a tightly coupled application
service.


------------------------------------------------------------------------

## 37. Pattern and Penalty Execution Architecture

### 37.1 Runtime sequence

The scoring orchestrator must preserve this order:

1. Normalize and calculate raw dimension scores.
2. Calculate cross-dimension indicators and detect imbalances.
3. Evaluate pattern definitions against raw scores and normalized inputs.
4. Build evidence and calculate confidence.
5. Evaluate penalty eligibility and candidate deductions.
6. Aggregate candidates using configured stacking policies.
7. Calculate adjusted dimensions and overall LIS.
8. Build explanations from the immutable trace and persist the snapshot.

Pattern detection must use raw dimension scores, not adjusted scores,
unless a separately versioned rule explicitly declares otherwise. This
prevents circular evaluation in which penalties create additional pattern
triggers.

### 37.2 Domain model additions

Add or adapt these domain concepts without duplicating existing models:

- `PatternDefinition`, `PatternMatch`, `PatternEvidence`,
  `PatternSeverity`, `PatternEvaluationStatus`;
- `ConditionGroup`, `ConditionExpression`, `ConditionResult`;
- `PenaltyDefinition`, `PenaltyEvaluation`, `PenaltyApplication`,
  `PenaltySuppression`, `PenaltyAggregationResult`;
- `PenaltyStrategyType`, `PenaltyStackingPolicy`, `PenaltyTarget`;
- `ImbalanceDefinition`, `ImbalanceResult`.

Definitions are immutable versioned inputs to a run. Match/evaluation/
application objects are run-scoped outputs. Keep persistence entities
outside the domain model.

### 37.3 Service contracts

The service layer loads one immutable scoring snapshot and passes it to the domain engine. The domain does
not query repositories or resolve tenant context itself.

Suggested contracts (adapt names to existing code):

```java
public interface PatternDetectionService {
    List<PatternMatch> detect(
        ScoringContext context,
        List<NormalizedInput> inputs,
        List<DimensionScore> rawDimensionScores,
        List<PatternDefinition> definitions
    );
}
```

```java
public interface PenaltyStrategy {
    boolean supports(PenaltyStrategyType type);
    PenaltyEvaluation evaluate(
        PatternMatch match,
        PenaltyDefinition definition,
        ScoringContext context
    );
}
```

```java
public interface PenaltyAggregationService {
    PenaltyAggregationResult aggregate(
        List<PenaltyEvaluation> evaluations,
        PenaltyAggregationConfiguration configuration
    );
}
```

`ConditionEvaluator` should be a pure domain service. Strategies must
not read application configuration, clocks, databases or network state.
All required values are explicit parameters. Deterministic tie-breaking
must be implemented in the aggregation domain service, never delegated
to database row order or hash-map iteration.

### 37.4 Configuration validation and activation

Validation must reject unknown signal/dimension/pattern references,
invalid operator arity, type-incompatible operands, negative deductions,
invalid score bases, missing caps, invalid confidence thresholds,
ambiguous threshold intervals, unresolved exclusive groups, and
cyclic pattern/penalty references. Activation should run representative
fixtures and produce a preview/diff. ACTIVE versions are immutable.

The pattern/penalty lifecycle is `DRAFT → VALIDATING → ACTIVE → PAUSED
→ ARCHIVED`. Only ACTIVE versions execute. Store version IDs and a
configuration hash in every scoring snapshot.

### 37.5 Persistence additions

Add the Section 39.10 entities/tables from `spec.md` only where they do
not already exist. Link all run outcomes to `scoring_run`; tenant-owned
configuration is tenant-scoped. Store normalized evidence references
and evaluation traces sufficient for reproduction and explanation.
Avoid storing redundant copies of values already represented by canonical
scoring contribution records; use foreign keys or stable evidence IDs.

### 37.6 Observability and operations

Emit counts and durations for evaluated/matched patterns, eligible and
suppressed penalties, configuration validation failures, and scoring
execution stages. Avoid logging raw assessment answers or sensitive
respondent content. Include tenant-safe correlation IDs and scoring run
IDs. Alert on invalid active configuration, unexpected zero/maximum
penalty saturation, and deterministic replay mismatches.

