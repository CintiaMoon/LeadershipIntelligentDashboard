# Scoring Engine Specification

**Version:** 1.0\
**Status:** Draft for implementation\
**Domain:** Leadership Intelligence Platform\
**Primary stack:** Java 21, Spring Boot 3, PostgreSQL, JPA, Jackson,
JUnit 5, Testcontainers

## 1. Purpose

The Scoring Engine transforms an organization's completed leadership
assessment answers into a deterministic, explainable **Leadership
Intelligence Score (LIS)**.

The engine must:

-   normalize heterogeneous answer types into a common numeric scale;
-   calculate dimension scores;
-   apply configurable weights and penalties;
-   detect organizational imbalance and failure patterns;
-   produce qualitative risk flags;
-   generate an explainable score breakdown;
-   support tenant-specific scoring configuration;
-   preserve reproducibility through configuration/version snapshots;
-   remain stateless and independently testable.

The initial scoring dimensions are:

1.  **Team Health**
2.  **Delivery Stability**
3.  **Technical Risk**
4.  **Leadership Load**

Each dimension is scored from **0 to 100**.

The engine must not hard-code business scoring rules in Java when those
rules can be represented as configuration.

------------------------------------------------------------------------

## 2. Goals

### 2.1 Functional goals

The engine shall:

-   accept a list of assessment inputs;
-   identify the question type and configured normalization strategy;
-   normalize every valid answer to a numeric value;
-   map answers to one or more scoring dimensions;
-   calculate weighted dimension scores;
-   apply configurable penalties;
-   calculate the overall Leadership Intelligence Score;
-   detect cross-dimension imbalances;
-   evaluate configurable organizational failure patterns;
-   provide explanations for every material score adjustment;
-   return a stable JSON response suitable for APIs and dashboards.

### 2.2 Non-functional goals

The engine shall be:

-   deterministic;
-   stateless;
-   tenant-aware;
-   configurable;
-   versionable;
-   explainable;
-   testable;
-   extensible for future ML/analytics;
-   safe against malformed or unsupported input;
-   independent of HTTP/API concerns.

------------------------------------------------------------------------

## 3. Non-goals

The first implementation does not include:

-   machine-learning-generated scores;
-   predictive employee performance scoring;
-   individual employee ranking;
-   automated HR decisions;
-   external benchmark retrieval;
-   natural-language model inference as a required scoring dependency.

AI/ML may consume engine outputs later, but the core score must remain
reproducible without an LLM.

------------------------------------------------------------------------

## 4. Core Concepts

### 4.1 Assessment

An assessment is a completed instance of an assessment template for an
organization.

An assessment contains:

-   tenant;
-   organization;
-   template/version;
-   respondent/context metadata;
-   answers;
-   completion timestamp;
-   scoring configuration version.

### 4.2 Question

A question defines the information collected from the respondent.

Supported initial answer types:

-   `SCALE`
-   `MULTIPLE_CHOICE`
-   `TEXT`
-   `BOOLEAN`
-   `NUMBER`

The engine must support additional answer types through extensible
normalization strategies.

### 4.3 Dimension

A dimension represents a major leadership/system health area.

``` text
TEAM_HEALTH
DELIVERY_STABILITY
TECHNICAL_RISK
LEADERSHIP_LOAD
```

### 4.4 Normalized Input

A normalized input is the canonical numeric representation of an answer.

``` text
raw answer
    ↓
question type
    ↓
NormalizationStrategy
    ↓
normalized value [0..100]
```

The normalization layer must not calculate organizational scores.

### 4.5 Scoring Rule

A scoring rule determines how one or more normalized inputs contribute
to a dimension.

Rules may define:

-   weight;
-   dimension;
-   positive/negative direction;
-   aggregation method;
-   minimum/maximum contribution;
-   penalty behavior;
-   applicability conditions.

### 4.6 Pattern

A pattern identifies a meaningful organizational condition based on
combinations of inputs or dimension scores.

Examples:

-   high delivery pressure + high leadership load;
-   strong delivery + high technical risk;
-   low team health + high leadership load;
-   consistently low predictability.

Patterns produce qualitative findings and may be eligible to trigger
separately configured penalties. A pattern match never changes a score by
itself. Severity describes impact; confidence describes evidence quality.
These values must not be conflated.

------------------------------------------------------------------------

## 5. High-Level Architecture

``` text
Assessment API
      |
      v
Assessment Inputs
      |
      v
+-------------------------+
| Scoring Orchestrator    |
+-------------------------+
      |
      +--------------------+
      |                    |
      v                    v
NormalizationService   ConfigurationService
      |                    |
      v                    v
Normalized Inputs     Rules / Weights / Patterns
      |
      v
+-------------------------+
| Dimension Scoring       |
+-------------------------+
      |
      v
+-------------------------+
| Penalty Evaluation      |
+-------------------------+
      |
      v
+-------------------------+
| Pattern Detection       |
+-------------------------+
      |
      v
+-------------------------+
| LIS Aggregation         |
+-------------------------+
      |
      v
+-------------------------+
| Explainability          |
+-------------------------+
      |
      v
ScoringResult
```

------------------------------------------------------------------------

## 6. Processing Pipeline

The canonical pipeline is:

``` text
1. Validate request
2. Load scoring configuration
3. Resolve question configurations
4. Normalize all inputs
5. Validate normalized inputs
6. Group inputs by dimension
7. Calculate raw dimension scores
8. Calculate raw cross-dimension indicators
9. Detect imbalances
10. Detect organizational patterns and collect evidence
11. Evaluate penalty eligibility and calculate candidate penalties
12. Aggregate/suppress penalties according to stacking policies
13. Calculate adjusted dimension scores
14. Calculate overall LIS and apply overall penalties
15. Determine interpretation/risk levels
16. Build complete explanations, including suppressed/ineligible findings
17. Persist immutable scoring snapshot/result
18. Return ScoringResult
```

The pipeline must be deterministic for the same:

-   tenant;
-   assessment version;
-   inputs;
-   scoring configuration version.

------------------------------------------------------------------------

## 7. Input Contract

The scoring engine should receive a list of inputs rather than a single
input.

``` json
{
  "tenantId": "tenant-001",
  "organizationId": "org-001",
  "assessmentId": "assessment-001",
  "templateVersion": 3,
  "inputs": [
    {
      "questionId": "Q01",
      "questionType": "SCALE",
      "answer": 4
    },
    {
      "questionId": "Q02",
      "questionType": "MULTIPLE_CHOICE",
      "answer": "OFTEN"
    },
    {
      "questionId": "Q03",
      "questionType": "TEXT",
      "answer": "We frequently depend on one senior engineer."
    }
  ]
}
```

The engine must not require normalization configuration in the request.

Normalization configuration is loaded from the configured
tenant/template scoring configuration.

------------------------------------------------------------------------

## 8. Normalization

### 8.1 Principle

All answer types are converted to a canonical score:

``` text
0 <= normalizedValue <= 100
```

The normalization service receives a **list of inputs** and resolves the
appropriate configuration for each question.

``` java
List<NormalizedInput> normalize(List<ScoringInput> inputs)
```

### 8.2 Configuration source

Normalization configuration must be loaded from JSON/DB configuration.

The request must not contain:

``` json
{
  "normalizationConfig": {}
}
```

Instead:

``` text
questionId
   ↓
question configuration
   ↓
question type
   ↓
normalization configuration
   ↓
NormalizationStrategy
```

### 8.3 Example configuration

``` json
{
  "normalization": {
    "SCALE": {
      "min": 1,
      "max": 5,
      "positiveScale": true,
      "negativeScale": false
    },
    "MULTIPLE_CHOICE": {
      "values": {
        "NEVER": 0,
        "RARELY": 25,
        "SOMETIMES": 50,
        "OFTEN": 75,
        "ALWAYS": 100
      }
    },
    "BOOLEAN": {
      "trueValue": 100,
      "falseValue": 0
    }
  }
}
```

### 8.4 Scale normalization

For a positive scale:

``` text
normalized =
    ((value - min) / (max - min)) * 100
```

For a negative scale:

``` text
normalized =
    100 - (((value - min) / (max - min)) * 100)
```

Values must be clamped or rejected according to configuration.

### 8.5 Text normalization

Text answers must not be treated as arbitrary numeric input.

Initial text processing may use configured deterministic flags:

``` json
{
  "TEXT": {
    "flags": [
      {
        "id": "SINGLE_POINT_OF_FAILURE",
        "keywords": ["only", "single", "one person", "key person"],
        "impact": -15
      }
    ]
  }
}
```

Text-derived signals must be included in the explanation.

LLM-based semantic classification is an optional future extension.

### 8.6 Missing inputs

Configuration determines whether a missing answer is:

-   ignored;
-   treated as neutral;
-   treated as a risk;
-   invalidates the assessment.

Missing answers must never silently become zero.

------------------------------------------------------------------------

## 9. Dimension Scoring

### 9.1 Dimensions

``` java
enum ScoringDimension {
    TEAM_HEALTH,
    DELIVERY_STABILITY,
    TECHNICAL_RISK,
    LEADERSHIP_LOAD
}
```

### 9.2 Weighted average

The default aggregation strategy is weighted average:

``` text
rawDimensionScore =
    Σ(normalizedValue × weight)
    --------------------------------
             Σ(weight)
```

Scores are constrained to:

``` text
0..100
```

### 9.3 Multiple dimensions

A question may contribute to multiple dimensions.

Example:

``` json
{
  "questionId": "Q15",
  "dimensions": [
    {
      "dimension": "DELIVERY_STABILITY",
      "weight": 0.7
    },
    {
      "dimension": "LEADERSHIP_LOAD",
      "weight": 0.3
    }
  ]
}
```

### 9.4 Contribution

Each input should retain its contribution:

``` json
{
  "questionId": "Q15",
  "dimension": "DELIVERY_STABILITY",
  "normalizedValue": 80,
  "weight": 0.7,
  "contribution": 56
}
```

------------------------------------------------------------------------

## 10. Directionality

A normalized score always means:

``` text
100 = desirable state
0   = undesirable state
```

Therefore, questions where a higher raw answer represents greater risk
must use negative direction during normalization.

Example:

``` text
Question:
"How frequently are releases delayed?"

Raw:
5 = very frequently

Normalized:
0 = high risk
```

This allows all downstream scoring algorithms to use the same semantic
direction.

------------------------------------------------------------------------

## 11. Penalties

Penalties represent systemic conditions that should reduce a dimension
or overall score beyond the simple weighted average.

Example:

``` json
{
  "id": "HIGH_LEADERSHIP_LOAD",
  "condition": {
    "dimension": "LEADERSHIP_LOAD",
    "operator": "LT",
    "threshold": 40
  },
  "penalty": {
    "type": "DIMENSION",
    "dimension": "LEADERSHIP_LOAD",
    "value": 10
  }
}
```

Penalty rules must support FIXED, PROPORTIONAL, SEVERITY_BASED,
THRESHOLD_BASED, DIMENSION, OVERALL and CONDITIONAL strategies.
They must also define a target, trigger/evidence requirements, maximum
individual deduction, category, optional exclusive group, priority,
stacking policy and maximum cumulative deduction. Pattern detection and
penalty evaluation are separate operations: a detected pattern is not
implicitly a penalty.

### 11.1 Penalty ordering

Default:

``` text
raw dimension score
        ↓
dimension penalties
        ↓
adjusted dimension score
        ↓
overall score
        ↓
overall penalties
```

Penalties must never reduce a score below zero.

------------------------------------------------------------------------

## 12. Imbalance Detection

The engine must detect situations where dimensions materially diverge.

Example:

``` text
Team Health       = 82
Delivery Stability = 79
Technical Risk     = 34
Leadership Load    = 38
```

Potential finding:

``` text
"Delivery is strong relative to technical resilience."
```

### 12.1 Configuration

``` json
{
  "imbalanceRules": [
    {
      "id": "DELIVERY_TECHNICAL_GAP",
      "dimensions": [
        "DELIVERY_STABILITY",
        "TECHNICAL_RISK"
      ],
      "minimumGap": 30,
      "severity": "HIGH"
    }
  ]
}
```

The engine should return:

-   detected/not detected;
-   gap;
-   involved dimensions;
-   severity;
-   explanation;
-   optional recommendation key.

------------------------------------------------------------------------

## 13. Organizational Failure Patterns

Patterns are configurable combinations of:

-   normalized inputs;
-   dimension scores;
-   dimension gaps;
-   thresholds;
-   text flags;
-   previous assessment metrics when historical analytics are enabled.

Example:

``` json
{
  "id": "HERO_LEADER_PATTERN",
  "name": "Hero Leader Dependency",
  "conditions": [
    {
      "metric": "LEADERSHIP_LOAD",
      "operator": "LT",
      "value": 45
    },
    {
      "flag": "SINGLE_POINT_OF_FAILURE",
      "operator": "EXISTS"
    }
  ],
  "severity": "HIGH",
  "penalty": 8
}
```

Pattern evaluation must be isolated from the core scoring algorithm.
The initial catalog, condition model, confidence/evidence requirements,
and penalty relationship are defined in Section 39. The catalog is a
versioned starting taxonomy, not a claim that every pattern or threshold
is scientifically calibrated. Thresholds must remain configurable and
must pass validation before activation.

------------------------------------------------------------------------

## 14. Leadership Intelligence Score

The overall LIS is derived from adjusted dimension scores.

Default formula:

``` text
LIS =
    TeamHealth × W1
  + DeliveryStability × W2
  + TechnicalRisk × W3
  + LeadershipLoad × W4
  - OverallPenalties
```

Default dimension weights must be configuration, not constants.

Example:

``` json
{
  "overallWeights": {
    "TEAM_HEALTH": 0.25,
    "DELIVERY_STABILITY": 0.25,
    "TECHNICAL_RISK": 0.25,
    "LEADERSHIP_LOAD": 0.25
  }
}
```

The weights must sum to 1.0 after validation.

------------------------------------------------------------------------

## 15. Interpretation

The engine returns a numeric score plus an interpretation.

Example configuration:

``` json
{
  "ranges": [
    {
      "min": 0,
      "max": 39,
      "label": "CRITICAL"
    },
    {
      "min": 40,
      "max": 59,
      "label": "AT_RISK"
    },
    {
      "min": 60,
      "max": 79,
      "label": "HEALTHY"
    },
    {
      "min": 80,
      "max": 100,
      "label": "STRONG"
    }
  ]
}
```

Labels are configuration and may evolve independently from scoring
algorithms.

------------------------------------------------------------------------

## 16. Explainability

Every score must be explainable.

The result should identify:

-   normalized answer;
-   question contribution;
-   dimension contribution;
-   penalties;
-   patterns;
-   final calculation.

Example:

``` json
{
  "dimension": "TECHNICAL_RISK",
  "score": 48,
  "explanation": {
    "positiveDrivers": [
      "Strong automated testing practices"
    ],
    "negativeDrivers": [
      "High dependency on a single technical owner"
    ],
    "penalties": [
      {
        "ruleId": "SINGLE_POINT_OF_FAILURE",
        "value": 8
      }
    ]
  }
}
```

The explanation must be generated from structured scoring facts rather
than hard-coded narrative strings.

------------------------------------------------------------------------

## 17. Result Contract

Example response:

``` json
{
  "assessmentId": "assessment-001",
  "scoringVersion": "2.0.0",
  "configurationVersion": "2026-09-30",
  "leadershipIntelligenceScore": 72,
  "interpretation": "HEALTHY",
  "dimensions": {
    "TEAM_HEALTH": {
      "score": 81,
      "rawScore": 84,
      "penalty": 3
    },
    "DELIVERY_STABILITY": {
      "score": 76,
      "rawScore": 76,
      "penalty": 0
    },
    "TECHNICAL_RISK": {
      "score": 58,
      "rawScore": 66,
      "penalty": 8
    },
    "LEADERSHIP_LOAD": {
      "score": 63,
      "rawScore": 70,
      "penalty": 7
    }
  },
  "imbalances": [
    {
      "ruleId": "DELIVERY_TECHNICAL_GAP",
      "severity": "MEDIUM",
      "gap": 18
    }
  ],
  "patterns": [
    {
      "patternId": "HERO_LEADER_PATTERN",
      "severity": "HIGH"
    }
  ],
  "explanations": []
}
```

------------------------------------------------------------------------

## 18. Domain Model

Recommended domain objects:

``` text
ScoringInput
NormalizedInput
DimensionScore
DimensionContribution
PenaltyResult
PatternMatch
ImbalanceResult
ScoreInterpretation
ScoringExplanation
ScoringResult
ScoringContext
```

### 18.1 ScoringInput

``` java
class ScoringInput {
    String questionId;
    QuestionType questionType;
    Object answer;
}
```

### 18.2 NormalizedInput

``` java
class NormalizedInput {
    String questionId;
    QuestionType questionType;
    BigDecimal normalizedValue;
    Set<String> detectedFlags;
}
```

### 18.3 DimensionScore

``` java
class DimensionScore {
    ScoringDimension dimension;
    BigDecimal rawScore;
    BigDecimal adjustedScore;
    BigDecimal penalty;
    List<DimensionContribution> contributions;
}
```

------------------------------------------------------------------------

## 19. Service Architecture

Recommended services:

``` text
ScoringOrchestrator
NormalizationService
NormalizationConfigService
DimensionScoringService
ImbalanceDetectionService
ConditionEvaluator
PatternDetectionService
PatternConfidenceService
PenaltyEvaluationService
PenaltyAggregationService
OverallScoreService
InterpretationService
ExplainabilityService
ScoringConfigurationService
ScoringPersistenceService
```

### 19.1 Strategy interfaces

``` java
interface NormalizationStrategy {
    boolean supports(QuestionType questionType);
    NormalizedInput normalize(ScoringInput input,
                               NormalizationConfig config);
}
```

Examples:

``` text
ScaleNormalizationStrategy
MultipleChoiceNormalizationStrategy
BooleanNormalizationStrategy
NumberNormalizationStrategy
TextNormalizationStrategy
```

### 19.2 Aggregation strategy

``` java
interface AggregationStrategy {
    BigDecimal aggregate(List<WeightedValue> values);
}
```

Initial implementation:

``` text
WeightedAverageStrategy
```

------------------------------------------------------------------------

## 20. Configuration

Configuration should be externalized.

Recommended structure:

``` text
scoring-config/
    normalization
    dimensions
    question-mappings
    weights
    penalties
    patterns
    imbalance-rules
    interpretation-ranges
```

Configuration may be stored in PostgreSQL JSONB.

Recommended entities:

``` text
scoring_configuration
normalization_configuration
scoring_rule
penalty_rule
pattern_rule
imbalance_rule
interpretation_range
```

Configuration must be versioned.

------------------------------------------------------------------------

## 21. Multi-Tenancy

Every scoring request is associated with a tenant.

Tenant isolation must apply to:

-   assessment data;
-   scoring configuration;
-   scoring results;
-   custom rules;
-   patterns;
-   weights.

A tenant may override platform defaults.

Resolution order:

``` text
Tenant configuration
       ↓
Organization configuration
       ↓
Template configuration
       ↓
Platform default
```

The exact precedence must be explicit and tested.

------------------------------------------------------------------------

## 22. Configuration Caching

Scoring configuration should be cached because scoring is read-heavy.

Requirements:

-   cache by tenant + configuration version;
-   invalidate when configuration changes;
-   never share tenant-specific configuration across tenants;
-   expose configuration version in every scoring result.

A future implementation may use Redis, but the first version may use an
in-memory cache.

------------------------------------------------------------------------

## 23. Persistence

Minimum persisted entities:

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
```

### 23.1 Scoring run

A scoring run must preserve:

-   assessment ID;
-   tenant ID;
-   scoring engine version;
-   configuration version;
-   execution timestamp;
-   input hash;
-   result;
-   status;
-   error information.

This enables historical reproducibility.

------------------------------------------------------------------------

## 24. Idempotency and Reproducibility

For the same assessment snapshot and configuration version:

``` text
same inputs
+
same configuration
+
same engine version
=
same result
```

A deterministic input hash should be generated.

If an assessment is rescored with a different configuration version, the
new run must not overwrite the historical scoring result.

------------------------------------------------------------------------

## 25. REST API

### 25.1 Score assessment

``` http
POST /api/v1/assessments/{assessmentId}/score
```

Request:

``` json
{
  "inputs": [
    {
      "questionId": "Q01",
      "questionType": "SCALE",
      "answer": 4
    }
  ]
}
```

Response:

``` http
200 OK
```

Returns `ScoringResult`.

### 25.2 Retrieve score

``` http
GET /api/v1/assessments/{assessmentId}/score
```

### 25.3 Retrieve score explanation

``` http
GET /api/v1/assessments/{assessmentId}/score/explanation
```

### 25.4 Recalculate

``` http
POST /api/v1/assessments/{assessmentId}/score/recalculate
```

Recalculation creates a new scoring run.

------------------------------------------------------------------------

## 26. Error Handling

Use explicit domain errors.

Examples:

``` text
QUESTION_NOT_CONFIGURED
UNSUPPORTED_QUESTION_TYPE
INVALID_SCALE_VALUE
MISSING_REQUIRED_INPUT
INVALID_NORMALIZATION_CONFIGURATION
INVALID_WEIGHT_CONFIGURATION
INVALID_PENALTY_CONFIGURATION
INVALID_PATTERN_CONFIGURATION
TENANT_CONFIGURATION_NOT_FOUND
SCORING_CONFIGURATION_VERSION_NOT_FOUND
```

API errors should contain:

``` json
{
  "code": "QUESTION_NOT_CONFIGURED",
  "message": "Question Q15 has no scoring configuration.",
  "details": {
    "questionId": "Q15"
  }
}
```

------------------------------------------------------------------------

## 27. Validation Rules

Configuration validation must verify:

-   scale min \< max;
-   valid normalization ranges;
-   dimension weights \>= 0;
-   total overall weights = 1;
-   question weights \>= 0;
-   penalties \>= 0;
-   penalty limits are valid;
-   interpretation ranges do not overlap;
-   interpretation ranges cover 0..100;
-   pattern conditions are valid;
-   all referenced dimensions exist;
-   all referenced question types exist.

Invalid configuration must fail fast before it can be activated.

------------------------------------------------------------------------

## 28. Unit Testing

Each service must have isolated unit tests.

### NormalizationService

Test:

-   SCALE positive;
-   SCALE negative;
-   min/max boundaries;
-   decimal values;
-   MULTIPLE_CHOICE;
-   BOOLEAN;
-   NUMBER;
-   TEXT flags;
-   missing configuration;
-   unsupported type;
-   mixed question types in one list;
-   multiple inputs using different normalization strategies;
-   invalid values;
-   null answers.

### DimensionScoringService

Test:

-   single contribution;
-   multiple weighted contributions;
-   multiple dimensions;
-   zero weights;
-   mixed answer types;
-   score boundaries;
-   empty input;
-   decimal precision.

### PenaltyEvaluationService and PenaltyAggregationService

Test each configured strategy, eligibility gate, confidence/evidence
requirement, threshold boundary, individual cap, stacking policy,
exclusive group, priority tie-break, suppression reason, dimension/overall
target, deterministic rounding and no-double-application invariant.

### PatternDetectionService

Test:

-   pattern not matched;
-   single-condition match;
-   multi-condition match;
-   threshold boundaries;
-   multiple patterns;
-   evidence and confidence;
-   insufficient evidence is penalty-ineligible;
-   severity is independent from confidence;
-   detection does not mutate scores;
-   overlapping pattern matches remain explainable.

### ImbalanceDetectionService

Test:

-   no imbalance;
-   exact threshold;
-   threshold exceeded;
-   multiple dimension gaps;
-   multiple imbalance rules.

### OverallScoreService

Test:

-   default weights;
-   custom weights;
-   invalid weights;
-   penalty application;
-   score boundaries;
-   deterministic output.

### ExplainabilityService

Test:

-   contribution explanation;
-   penalty explanation;
-   pattern explanation;
-   imbalance explanation;
-   complete explanation graph.

------------------------------------------------------------------------

## 29. Integration Testing

Use Testcontainers with PostgreSQL.

Integration tests must verify:

1.  configuration loading;
2.  tenant isolation;
3.  JSON configuration deserialization;
4.  scoring execution;
5.  persistence of scoring runs;
6.  retrieval of historical scores;
7.  configuration versioning;
8.  recalculation;
9.  reproducibility;
10. transaction behavior.

------------------------------------------------------------------------

## 30. Example End-to-End Calculation

Assume:

``` text
Team Health        = 82
Delivery Stability = 76
Technical Risk     = 64
Leadership Load    = 58
```

Overall weights:

``` text
25% each
```

Raw LIS:

``` text
82 × .25 = 20.50
76 × .25 = 19.00
64 × .25 = 16.00
58 × .25 = 14.50

Raw LIS = 70.00
```

Assume:

``` text
Technical Risk penalty = 5
Leadership Load penalty = 4
```

Adjusted dimensions:

``` text
Technical Risk   = 59
Leadership Load  = 54
```

Adjusted LIS:

``` text
82 × .25 = 20.50
76 × .25 = 19.00
59 × .25 = 14.75
54 × .25 = 13.50

LIS = 67.75
```

The engine should preserve both raw and adjusted values.

------------------------------------------------------------------------

## 31. Precision and Rounding

Use `BigDecimal` for scoring calculations.

Recommended:

``` text
internal precision: at least 4 decimal places
API presentation: 0–2 decimal places
```

Rounding must happen only at the presentation/result boundary, not
during intermediate calculations.

The rounding mode must be explicitly configured, with `HALF_UP` as the
initial default.

------------------------------------------------------------------------

## 32. Security

The engine must:

-   validate tenant context;
-   prevent cross-tenant configuration access;
-   avoid exposing internal scoring configuration to unauthorized roles;
-   validate assessment ownership;
-   sanitize text inputs;
-   avoid logging sensitive assessment answers;
-   avoid logging complete free-text answers by default.

------------------------------------------------------------------------

## 33. Observability

Metrics should include:

``` text
scoring.requests.total
scoring.requests.success
scoring.requests.failure
scoring.duration
scoring.normalization.errors
scoring.configuration.errors
scoring.patterns.detected
scoring.penalties.applied
```

Logs should include:

-   tenant ID;
-   assessment ID;
-   scoring run ID;
-   configuration version;
-   engine version;
-   duration;
-   outcome.

Do not log sensitive raw answers.

------------------------------------------------------------------------

## 34. Performance

The initial synchronous implementation should target:

-   deterministic execution;
-   no external service dependency;
-   O(n) normalization/scoring for normal assessment sizes;
-   cached configuration;
-   bounded text processing.

The architecture should allow future asynchronous execution for large
assessments.

------------------------------------------------------------------------

## 35. Extensibility

The engine must support future:

-   new question types;
-   new normalization strategies;
-   new scoring dimensions;
-   new aggregation strategies;
-   new penalties;
-   new pattern types;
-   historical trend inputs;
-   benchmarking;
-   anomaly detection;
-   ML-derived signals;
-   AI-generated explanations.

New behavior should preferably be implemented by adding
strategies/configuration rather than modifying the orchestration
pipeline.

------------------------------------------------------------------------

## 36. Package Structure

Recommended package structure:

``` text
com.company.leadership.scoring
├── api
│   ├── ScoringController
│   ├── request
│   └── response
├── service
│   ├── ScoringOrchestrator
│   ├── NormalizationService
│   ├── DimensionScoringService
│   ├── ImbalanceDetectionService
│   ├── ConditionEvaluator
│   ├── PatternDetectionService
│   ├── PatternConfidenceService
│   ├── PenaltyEvaluationService
│   ├── PenaltyAggregationService
│   ├── OverallScoreService
│   └── ExplainabilityService
├── engine
│   ├── normalization
│   ├── aggregation
│   ├── penalties
│   ├── patterns
│   └── interpretation
├── domain
│   ├── model
│   ├── enums
│   └── exception
├── repository
├── config
│   ├── scoring
│   └── normalization
└── persistence
```

------------------------------------------------------------------------

## 37. Definition of Done

The scoring engine is considered complete when:

-   [ ] all four dimensions are supported;
-   [ ] normalization accepts a list of inputs;
-   [ ] normalization configuration is externalized;
-   [ ] no normalization configuration is required in the request;
-   [ ] SCALE, MULTIPLE_CHOICE, BOOLEAN, NUMBER and TEXT are supported;
-   [ ] weighted dimension scoring is implemented;
-   [ ] configurable penalties are implemented;
-   [ ] imbalance detection is implemented;
-   [ ] configurable organizational patterns are implemented;
-   [ ] overall LIS is calculated;
-   [ ] interpretation ranges are configurable;
-   [ ] complete explainability is returned;
-   [ ] tenant isolation is enforced;
-   [ ] configuration versions are persisted;
-   [ ] scoring runs are reproducible;
-   [ ] REST endpoints are implemented;
-   [ ] unit tests cover all services;
-   [ ] mixed answer-type scenarios are tested;
-   [ ] PostgreSQL integration tests use Testcontainers;
-   [ ] invalid configuration is rejected before activation;
-   [ ] observability is implemented;
-   [ ] API documentation/examples are available.

------------------------------------------------------------------------

## 38. Architectural Principle

The most important architectural rule is:

> **The scoring engine is a deterministic execution engine; business
> scoring behavior belongs in versioned configuration, not in hard-coded
> business logic.**

This separation allows the Leadership Intelligence model to evolve
without repeatedly rewriting the engine and makes scores reproducible,
explainable, tenant-specific, testable, and suitable for future
benchmarking and analytics.


------------------------------------------------------------------------

## 39. Pattern and Penalty Model — Catalog and Execution Contract

This section is normative for the first implementation. Catalog entries
are candidate rules; numeric thresholds and deduction caps are defaults
for calibration, not empirically validated constants. Only ACTIVE,
validated versions may participate in a scoring run.

### 39.1 Pattern semantics

A **pattern** is a named, evidence-backed organizational condition. It
may be informational, risk-signaling, or penalty-eligible. Detection
must not mutate dimension scores or LIS. Pattern definitions must carry:

- `patternId`, stable `code`, `name`, `description`, `category`;
- `severity` (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`);
- version, lifecycle status, enabled flag and effective dates;
- a composable condition expression;
- evidence requirements (`minimumSignals`, `minimumConfidence`,
  optional source/recency/independence requirements);
- `penaltyEligible` and optional penalty references;
- recommendation/action keys and explanatory text keys.

Pattern categories are the four dimensions or `CROSS_DIMENSIONAL`.
Severity and confidence are independent. A high-severity match with
insufficient evidence is retained as insufficient/ineligible evidence
when configured, but must not trigger a penalty.

### 39.2 Initial pattern catalog

The following catalog establishes stable identifiers and coverage. A
pattern is enabled only after its conditions, evidence sources, and
thresholds have been validated against representative assessment data.

| ID | Pattern | Category |
|---|---|---|
| TH-001 | Team Burnout Risk | TEAM_HEALTH |
| TH-002 | Low Psychological Safety | TEAM_HEALTH |
| TH-003 | Collaboration Breakdown | TEAM_HEALTH |
| TH-004 | Low Team Autonomy | TEAM_HEALTH |
| TH-005 | Knowledge Silos | TEAM_HEALTH |
| TH-006 | Team Disengagement | TEAM_HEALTH |
| TH-007 | Excessive Context Switching | TEAM_HEALTH |
| TH-008 | Role Ambiguity | TEAM_HEALTH |
| TH-009 | Organizational Friction | TEAM_HEALTH |
| TH-010 | Unsustainable Workload | TEAM_HEALTH |
| DS-001 | Chronic Delivery Unpredictability | DELIVERY_STABILITY |
| DS-002 | Planning Instability | DELIVERY_STABILITY |
| DS-003 | Excessive Work in Progress | DELIVERY_STABILITY |
| DS-004 | Dependency Bottleneck | DELIVERY_STABILITY |
| DS-005 | Delivery Heroics | DELIVERY_STABILITY |
| DS-006 | Reactive Development | DELIVERY_STABILITY |
| DS-007 | Weak Delivery Ownership | DELIVERY_STABILITY |
| DS-008 | Estimation Dysfunction | DELIVERY_STABILITY |
| DS-009 | Release Instability | DELIVERY_STABILITY |
| DS-010 | Process Overhead | DELIVERY_STABILITY |
| TR-001 | Critical Technical Debt | TECHNICAL_RISK |
| TR-002 | Architectural Fragility | TECHNICAL_RISK |
| TR-003 | Single Point of Failure | TECHNICAL_RISK |
| TR-004 | Weak Test Automation | TECHNICAL_RISK |
| TR-005 | Production Instability | TECHNICAL_RISK |
| TR-006 | Poor Observability | TECHNICAL_RISK |
| TR-007 | Security Exposure | TECHNICAL_RISK |
| TR-008 | Scalability Constraints | TECHNICAL_RISK |
| TR-009 | Deployment Bottleneck | TECHNICAL_RISK |
| TR-010 | Technical Knowledge Concentration | TECHNICAL_RISK |
| LL-001 | Leadership Bottleneck | LEADERSHIP_LOAD |
| LL-002 | Micromanagement | LEADERSHIP_LOAD |
| LL-003 | Delegation Deficit | LEADERSHIP_LOAD |
| LL-004 | Decision Latency | LEADERSHIP_LOAD |
| LL-005 | Excessive Span of Control | LEADERSHIP_LOAD |
| LL-006 | Leadership Overload | LEADERSHIP_LOAD |
| LL-007 | Lack of Strategic Alignment | LEADERSHIP_LOAD |
| LL-008 | Weak Succession Coverage | LEADERSHIP_LOAD |
| LL-009 | Escalation Dependency | LEADERSHIP_LOAD |
| LL-010 | Leadership Context Switching | LEADERSHIP_LOAD |
| XD-001 | Unsustainable Delivery Model | CROSS_DIMENSIONAL |
| XD-002 | Leadership-Driven Bottleneck | CROSS_DIMENSIONAL |
| XD-003 | Technical Debt Acceleration | CROSS_DIMENSIONAL |
| XD-004 | Organizational Dependency Trap | CROSS_DIMENSIONAL |
| XD-005 | Hidden Organizational Fragility | CROSS_DIMENSIONAL |
| XD-006 | Leadership Capacity Crisis | CROSS_DIMENSIONAL |
| XD-007 | Growth Without Engineering Maturity | CROSS_DIMENSIONAL |
| XD-008 | Hero Culture | CROSS_DIMENSIONAL |

Each catalog entry must be completed in configuration with measurable
signals and conditions. The catalog name alone is not executable logic.
Do not infer a condition or claim evidence that is not represented by
configured assessment signals.

### 39.3 Condition expression model

Conditions are a recursive expression tree. A group has `operator`
`AND`, `OR`, or `NOT` and child expressions; a leaf references exactly
one configured signal or metric and an operator/value. Support:

`EQ`, `NE`, `GT`, `GTE`, `LT`, `LTE`, `BETWEEN`, `IN`, `NOT_IN`,
`EXISTS`, `NOT_EXISTS`, `CONTAINS`, `GAP_GT`, `GAP_GTE`.

The evaluator must validate operand types, reject unknown signal IDs and
invalid arity, use explicit null/missing-data semantics, and never
silently coerce missing data to zero. `NOT` must have exactly one child.
`GAP_*` must identify both dimensions/signals and the direction of the
gap. Historical metrics are unavailable unless explicitly included in
the scoring context and snapshot.

Example configuration (illustrative signal names and thresholds):

```json
{
  "patternId": "TH-001",
  "code": "TEAM_BURNOUT_RISK",
  "version": 1,
  "category": "TEAM_HEALTH",
  "severity": "CRITICAL",
  "enabled": true,
  "conditions": {
    "operator": "AND",
    "conditions": [
      {"signal": "WORKLOAD_PRESSURE", "operator": "GTE", "value": 75},
      {"signal": "TEAM_SUSTAINABILITY", "operator": "LTE", "value": 35}
    ]
  },
  "evidenceRequirements": {"minimumSignals": 2, "minimumConfidence": 0.70},
  "penaltyEligible": true,
  "recommendedActions": ["REVIEW_TEAM_CAPACITY", "REVIEW_WORKLOAD_DISTRIBUTION"]
}
```

### 39.4 Evidence and confidence

Every match records the exact evidence: signal/question ID, source,
raw/normalized value where applicable, timestamp or assessment period,
condition result, and contribution to confidence. Confidence is a value
in `[0,1]`; its calculation must be deterministic and configured or
implemented as a documented versioned algorithm. Do not treat the
following proposed bands as calibrated truth: 0.00–0.39 insufficient,
0.40–0.59 weak, 0.60–0.79 moderate, 0.80–1.00 strong. They are initial
labels only and must be configurable. Evidence must be sufficiently
independent to avoid counting multiple questions that measure the same
underlying signal as independent corroboration.

A pattern can be detected but penalty-ineligible because evidence is
missing, confidence is below threshold, the pattern is informational,
or the associated penalty is inactive. Explain the exact reason.

### 39.5 Initial penalty catalog and suggested maximum deductions

All values below are *suggested maximum points*, not default deductions
that should automatically be applied. A penalty must have a validated
trigger, target, strategy, evidence policy, and stacking configuration.

| ID | Trigger pattern | Target | Suggested max |
|---|---|---|---:|
| TH-P01 | TH-001 Burnout Risk | TEAM_HEALTH | 12 |
| TH-P02 | TH-002 Psychological Safety Deficit | TEAM_HEALTH | 8 |
| TH-P03 | TH-003 Collaboration Breakdown | TEAM_HEALTH | 8 |
| TH-P04 | TH-004 Low Autonomy | TEAM_HEALTH | 6 |
| TH-P05 | TH-005 Knowledge Silos | TEAM_HEALTH | 8 |
| TH-P06 | TH-006 Disengagement | TEAM_HEALTH | 8 |
| TH-P07 | TH-007 Context Switching | TEAM_HEALTH | 5 |
| TH-P08 | TH-008 Role Ambiguity | TEAM_HEALTH | 5 |
| DS-P01 | DS-001 Delivery Unpredictability | DELIVERY_STABILITY | 10 |
| DS-P02 | DS-002 Planning Instability | DELIVERY_STABILITY | 8 |
| DS-P03 | DS-003 Excessive WIP | DELIVERY_STABILITY | 6 |
| DS-P04 | DS-004 Dependency Bottleneck | DELIVERY_STABILITY | 8 |
| DS-P05 | DS-005 Delivery Heroics | DELIVERY_STABILITY | 12 |
| DS-P06 | DS-006 Reactive Development | DELIVERY_STABILITY | 8 |
| DS-P07 | DS-007 Weak Ownership | DELIVERY_STABILITY | 5 |
| DS-P08 | DS-008 Estimation Dysfunction | DELIVERY_STABILITY | 5 |
| DS-P09 | DS-009 Release Instability | DELIVERY_STABILITY | 10 |
| TR-P01 | TR-001 Critical Technical Debt | TECHNICAL_RISK | 10 |
| TR-P02 | TR-002 Architectural Fragility | TECHNICAL_RISK | 10 |
| TR-P03 | TR-003 Single Point of Failure | TECHNICAL_RISK | 12 |
| TR-P04 | TR-004 Weak Test Automation | TECHNICAL_RISK | 8 |
| TR-P05 | TR-005 Production Instability | TECHNICAL_RISK | 15 |
| TR-P06 | TR-006 Poor Observability | TECHNICAL_RISK | 6 |
| TR-P07 | TR-007 Security Exposure | TECHNICAL_RISK | 15 |
| TR-P08 | TR-008 Scalability Constraints | TECHNICAL_RISK | 8 |
| TR-P09 | TR-009 Deployment Bottleneck | TECHNICAL_RISK | 5 |
| LL-P01 | LL-001 Leadership Bottleneck | LEADERSHIP_LOAD | 10 |
| LL-P02 | LL-002 Micromanagement | LEADERSHIP_LOAD | 8 |
| LL-P03 | LL-003 Delegation Deficit | LEADERSHIP_LOAD | 8 |
| LL-P04 | LL-004 Decision Latency | LEADERSHIP_LOAD | 8 |
| LL-P05 | LL-005 Excessive Span of Control | LEADERSHIP_LOAD | 6 |
| LL-P06 | LL-006 Leadership Overload | LEADERSHIP_LOAD | 12 |
| LL-P07 | LL-007 Strategic Misalignment | LEADERSHIP_LOAD | 8 |
| LL-P08 | LL-008 Weak Succession Coverage | LEADERSHIP_LOAD | 5 |
| LL-P09 | LL-009 Escalation Dependency | LEADERSHIP_LOAD | 8 |
| XD-P01 | XD-001 Unsustainable Operating Model | OVERALL | 8 |
| XD-P02 | XD-002 Leadership Bottleneck Impact | OVERALL | 6 |
| XD-P03 | XD-003 Technical Delivery Fragility | OVERALL | 6 |
| XD-P04 | XD-004 Organizational Dependency | OVERALL | 6 |
| XD-P05 | XD-005 Hidden Organizational Fragility | OVERALL | 10 |
| XD-P06 | XD-006 Leadership Capacity Crisis | OVERALL | 8 |
| XD-P07 | XD-007 Engineering Maturity Gap | OVERALL | 6 |
| XD-P08 | XD-008 Hero Culture | OVERALL | 10 |

The catalog intentionally does not assign penalties to every pattern.
For example, informational patterns and patterns without defensible
measurement may remain penalty-ineligible. Do not create missing
penalties merely to make the catalog appear symmetrical.

### 39.6 Penalty definition and strategies

A penalty definition includes `penaltyId`, stable code, version, trigger
pattern/condition, target (`TEAM_HEALTH`, `DELIVERY_STABILITY`,
`TECHNICAL_RISK`, `LEADERSHIP_LOAD`, `OVERALL`), strategy, severity
multipliers or thresholds, maximum individual deduction, category,
exclusive group, priority, stacking policy, maximum cumulative deduction,
evidence/confidence eligibility, enabled status and explanation keys.

Supported strategies:

- `FIXED`: configured point deduction, bounded by its maximum.
- `PROPORTIONAL`: configured rate multiplied by the explicitly named
  score base; never infer whether the base is raw dimension, gap, or LIS.
- `SEVERITY_BASED`: configured severity-to-multiplier mapping.
- `THRESHOLD_BASED`: explicit piecewise threshold schedule; boundaries
  and inclusivity must be validated and tested.
- `DIMENSION`: apply to one named dimension.
- `OVERALL`: apply after weighted adjusted dimensions.
- `CONDITIONAL`: evaluate an explicit condition and delegate to a
  configured strategy.

Example:

```json
{
  "penaltyId": "TH-P01",
  "name": "Burnout Risk Penalty",
  "target": "TEAM_HEALTH",
  "strategy": "THRESHOLD_BASED",
  "triggerPatternId": "TH-001",
  "severityMultipliers": {"LOW": 0, "MEDIUM": 0.4, "HIGH": 0.7, "CRITICAL": 1.0},
  "maximumDeduction": 12,
  "category": "SUSTAINABILITY",
  "stackingPolicy": "MAX_PER_CATEGORY",
  "enabled": true
}
```

The example is a schema illustration, not a calibrated production rule.
For each strategy, define the score base, units, rounding stage, bounds,
and behavior for missing values. Penalties are non-negative deductions;
invalid negative values or non-finite inputs must be rejected.

### 39.7 Stacking, overlap and suppression

Supported policies:

- `STACK`: sum all eligible deductions.
- `MAX`: retain only the largest eligible deduction in the group.
- `MAX_PER_CATEGORY`: retain the largest deduction per category.
- `PRIORITY`: select according to explicit priority and tie-break rules.
- `CAPPED_STACK`: sum, then cap at a configured maximum.
- `EXCLUSIVE_GROUP`: select one eligible member of an explicit
  mutually-exclusive group using deterministic priority/tie-break rules.

Initial recommended defaults (subject to calibration): `CAPPED_STACK`
within a dimension; `MAX_PER_CATEGORY` for related penalty categories;
`EXCLUSIVE_GROUP` for mutually exclusive explanations; and
`CAPPED_STACK` for overall penalties. Every policy must be explicit in
configuration; never rely on collection iteration order. Stable tie-break:
priority, then configured rule order, then penalty ID lexical order.

Each evaluated candidate must result in exactly one auditable state:
`APPLIED`, `SUPPRESSED`, `INELIGIBLE`, or `NOT_TRIGGERED`. Suppression
records identify the winning penalty/policy and the amount excluded.
Prevent double counting through overlap categories, exclusive groups,
shared evidence identifiers, and explicit conflict metadata. Do not
silently remove overlapping findings; retain them for explanation.

### 39.8 Calculation order and equations

For each dimension, aggregate raw question contributions first. Evaluate
all patterns and penalties against the immutable raw dimension scores
and evidence context. Aggregate eligible dimension penalties, then:

`adjustedDimension = max(0, rawDimension - effectiveDimensionPenalty)`

Then calculate:

`weightedAdjusted = Σ(adjustedDimension[d] × weight[d])`

`LIS = clamp(weightedAdjusted - effectiveOverallPenalty, 0, 100)`

Weights must sum to `1.0` within configured decimal tolerance. Penalties
must not be applied twice (e.g. as both a dimension deduction and an
overall deduction) unless an explicitly reviewed configuration declares
that behavior and its rationale. Preserve raw, candidate, effective,
suppressed and adjusted values separately. Use `BigDecimal` and the
single rounding policy defined by Section 31.

### 39.9 Explainability contract

For every pattern and penalty, return/persist:

- rule ID/code/version and evaluation status;
- matched condition tree and failed conditions where useful;
- evidence references and signal values (subject to privacy controls);
- severity, confidence and evidence eligibility;
- candidate deduction, cap, effective deduction and score base;
- stacking policy, category/group, winning rule and suppression reason;
- raw/adjusted dimension values and LIS before/after overall deductions;
- stable explanation and recommended-action keys.

The explanation layer reads the completed calculation trace. It must not
recalculate scores or infer missing evidence.

### 39.10 Lifecycle, persistence and reproducibility

Pattern and penalty definitions use `DRAFT → VALIDATING → ACTIVE →
PAUSED → ARCHIVED`. Activation requires schema validation, reference
integrity, no ambiguous thresholds, conflict/overlap checks, test fixtures,
preview results and audit metadata. ACTIVE versions are immutable; edits
create a new version. A scoring run stores exact pattern/penalty versions,
configuration snapshot/hash, engine version, input hash, and evaluation
trace so that historical runs remain reproducible after catalog changes.

Persist definitions/versions separately from run outcomes. Recommended
entities/tables (adapt to existing naming conventions and avoid duplicate
representations): `pattern_definition`, `pattern_rule_version`,
`pattern_match`, `pattern_evidence`, `penalty_definition`,
`penalty_rule_version`, `penalty_application`, `penalty_suppression`,
`penalty_aggregation`, `imbalance_definition`, `imbalance_result`.
All tenant-owned records must include tenant scope. JSONB may hold
versioned condition/config payloads; stable identifiers, tenant, version,
status, timestamps and relationships remain relational columns.

### 39.11 Required domain services

Keep these responsibilities independently testable:

- `ConditionEvaluator`: validates/evaluates expression trees.
- `PatternDetectionService`: evaluates active definitions and returns
  matches with evidence; has no score-mutating behavior.
- `PatternConfidenceService`: calculates deterministic confidence.
- `PenaltyEvaluationService`: evaluates eligibility and candidate
  deductions from matched patterns/conditions.
- `PenaltyStrategy` implementations: calculate strategy-specific
  candidate values.
- `PenaltyAggregationService`: applies stacking, caps, suppression and
  deterministic tie-breaking.
- `ImbalanceDetectionService`: detects configured cross-dimension gaps.
- `ExplainabilityService`: serializes the trace without recalculation.

### 39.12 Required tests and invariants

Pattern tests: all conditions match; one leaf fails; nested AND/OR/NOT;
unknown signals; missing/null values; type mismatch; insufficient
signals; confidence below threshold; cross-dimensional conditions;
overlapping patterns; inactive versions; deterministic evaluation.

Penalty tests: each strategy; exact threshold boundaries; severity
multipliers; caps; zero and maximum values; invalid negative values;
confidence/evidence ineligibility; inactive rule; no trigger; deterministic
rounding.

Aggregation tests: all six stacking policies; multiple categories;
exclusive groups; priority ties; capped totals; overlap/suppression
trace; dimension versus overall targets; no double application.

Global invariants:

- normalized inputs, dimension scores and LIS remain in `[0,100]`;
- penalty deductions are non-negative and cannot increase a score;
- adjusted dimension score is never greater than raw dimension score;
- pattern detection alone never changes any score;
- the same inputs, engine version and configuration snapshot produce
  byte-equivalent canonical results (excluding explicitly noncanonical
  metadata such as generated timestamps/IDs);
- missing evidence is never silently interpreted as a zero-valued signal;
- every candidate penalty has an explicit terminal evaluation status;
- historical results remain reproducible after rule changes.

