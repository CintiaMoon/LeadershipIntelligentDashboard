Version: 3.0.0
Status: Consolidated technical specification
Domain: Leadership Intelligence Platform
Primary stack: Java 21, Spring Boot 3, PostgreSQL
Architecture: Clean Architecture / Hexagonal Architecture / Domain-Driven Design
Previous versions: v1.0 and v2.0
Purpose: Provide a single, internally consistent specification for implementing the deterministic Leadership Intelligence Scoring Engine.

1. Purpose
The Leadership Intelligence Scoring Engine transforms completed organizational leadership assessments into a deterministic, explainable Leadership Intelligence Score (LIS).

The engine evaluates organizational conditions across four dimensions:
1. Team Health
2. Delivery Stability
3. Technical Risk
4. Leadership Load
It must:
- Normalize heterogeneous assessment answers into a common numeric scale.
- Calculate dimension scores using configurable scoring rules.
- Detect organizational patterns and cross-dimensional imbalances.
- Evaluate independently configured scoring penalties.
- Calculate an overall Leadership Intelligence Score.
- Produce a complete explanation of every material calculation.
- Support multiple tenants and organizations.
- Preserve historical scoring results and reproducibility.
- Support configuration versioning and lifecycle management.
- Provide stable API contracts for dashboards and downstream analytics.
- Remain independent of HTTP, persistence, and infrastructure concerns.
The engine must be configuration-driven. Business scoring rules must not be hard-coded in Java when they can be represented as validated configuration.
1.1 Normative language
The terms below have specific meanings throughout this document:
- MUST / MUST NOT: Mandatory implementation requirement.
- SHOULD / SHOULD NOT: Recommended requirement; deviations must be justified.
- MAY: Optional capability.
- Illustrative: Example values or rules, not validated production defaults.
1.2 Core design principle
The canonical scoring engine must be deterministic and explainable.

Given the same:
- Immutable assessment snapshot.
- Effective scoring configuration snapshot.
- Engine version.
- Scoring algorithm version.
The engine MUST produce the same canonical scoring result.

External AI/ML services, asynchronous analytics, or benchmark calculations MUST NOT silently change the canonical LIS.

2. Goals and non-goals
2.1 Functional goals
The engine shall:
- Accept multiple assessment inputs in a single scoring operation.
- Resolve question configuration independently of the incoming answer.
- Normalize SCALE, MULTIPLE_CHOICE, BOOLEAN, NUMBER, and TEXT inputs.
- Support deterministic text-derived signals.
- Map normalized signals to one or more dimensions.
- Calculate raw dimension scores.
- Detect patterns and imbalances independently.
- Evaluate penalty eligibility.
- Calculate candidate and effective penalties.
- Aggregate dimension and overall scores.
- Determine score interpretation.
- Generate a complete explainability trace.
- Persist immutable scoring runs.
- Return stable JSON responses.
2.2 Non-functional goals
The engine shall be:
- Deterministic.
- Stateless at the domain-engine level.
- Tenant-aware.
- Configuration-driven.
- Versioned.
- Explainable.
- Testable.
- Extensible.
- Secure.
- Observable.
- Independently deployable.
- Resilient to malformed or unsupported input.
2.3 Non-goals
The initial implementation does not include:
- ML-generated canonical scores.
- Predictive employee performance scoring.
- Individual employee ranking.
- Automated HR decisions.
- Automated hiring, promotion, termination, or compensation decisions.
- Required LLM inference during scoring.
- Unverified external benchmark retrieval.
- Automatic recommendations based on unverified natural-language inference.
AI/ML may consume scoring results later, but the deterministic engine must operate without an LLM.
2.4 Intended use and limitations
The LIS is an organizational diagnostic signal.

It is not:
- An individual employee performance rating.
- A measurement of an individual's value.
- A substitute for professional organizational assessment.
- A sufficient basis for employment decisions.
Reports and APIs must communicate this distinction.

3. Canonical scoring dimensions
The engine MUST implement the following dimensions.
Identifier	Name	Meaning of 100	Meaning of 0
TEAM_HEALTH	Team Health	Healthy, sustainable team environment	Severe organizational/team health concerns
DELIVERY_STABILITY	Delivery Stability	Predictable, sustainable delivery	Highly unstable or unpredictable delivery
TECHNICAL_RISK	Technical Risk	Low technical risk and strong resilience	High technical risk and weak resilience
LEADERSHIP_LOAD	Leadership Load	Sustainable leadership capacity and low unhealthy bottleneck load	Unsustainable leadership dependency and overload

3.1 Universal directionality
All normalized values and dimension scores MUST use the same direction:
100 = desirable / healthy
  0 = undesirable / unhealthy


This is especially important for TECHNICAL_RISK and LEADERSHIP_LOAD.

For example:
- A high raw answer indicating frequent production incidents must normalize to a low TECHNICAL_RISK value.
- A high raw answer indicating excessive dependency on a single leader must normalize to a low LEADERSHIP_LOAD value.
No downstream scoring service should need to interpret whether a higher value represents greater risk.
3.2 Extensibility
Additional dimensions may be introduced in future engine versions.

Adding a dimension requires:
- A stable identifier.
- A semantic definition.
- Directionality.
- Configuration support.
- Validation rules.
- Interpretation rules.
- API representation.
- Persistence support.
- Test coverage.
- An explicit migration and versioning strategy.
The four initial dimensions remain the required baseline.

4. Core domain concepts
4.1 Assessment
An assessment is a completed instance of an assessment template for an organization.

It contains:
- Assessment ID.
- Tenant ID.
- Organization ID.
- Template ID and version.
- Assessment status.
- Respondent/context metadata.
- Answers.
- Completion timestamp.
- Assessment snapshot reference.
The assessment must be immutable for the purpose of a scoring run.

A change to assessment answers must create a new assessment snapshot or assessment revision.
4.2 Question
A question defines the information collected from a respondent.

Initial supported types:
public enum QuestionType {
    SCALE,
    MULTIPLE_CHOICE,
    BOOLEAN,
    NUMBER,
    TEXT
}


Additional types must be supported through extensible normalization strategies.
4.2.1 Multi-select (amendment D49)
MULTI_SELECT is a question type. Its answer is a set of option identifiers, supplied as a list. Each option has a normalized value in [0, 100]. The normalized value of an answer is the arithmetic mean of the values of the selected options. An empty selection is missing data (Section 8.10), never zero. An option the configuration does not define is handled by the question's unknown-option policy. The order in which options are listed does not matter, and listing an option twice counts it once.
4.3 Scoring input
A scoring input contains the answer to one question.
public record ScoringInput(
    String questionId,
    Object answer
) {}


The question type and normalization configuration MUST be resolved from the immutable assessment/template configuration, not trusted from an arbitrary request field.

If an API includes questionType for compatibility, the server must validate it against the configured question definition. It must not use the client-supplied value as the authoritative configuration.
4.4 Normalized input
A normalized input is the canonical representation of an answer or derived signal.

It should retain:
- Question ID.
- Question type.
- Normalized value.
- Derived signal identifiers.
- Source reference.
- Normalization strategy and version.
- Validation status.
- Missing-data status.
- Evidence metadata, where applicable.
4.5 Scoring contribution
A contribution represents the impact of a normalized input on a dimension.

It includes:
- Question ID.
- Signal ID.
- Dimension.
- Normalized value.
- Contribution weight.
- Directionality, if needed for audit.
- Calculated contribution.
- Applicability status.
4.6 Pattern
A pattern represents an evidence-backed organizational condition.

A pattern can be:
- Informational.
- Risk-signaling.
- Penalty-eligible.
Pattern detection MUST NOT directly modify dimension scores or LIS.
4.7 Penalty
A penalty is a separately configured scoring consequence.

A penalty requires:
- A trigger.
- Eligibility conditions.
- A target.
- A strategy.
- A maximum deduction.
- A stacking policy.
- An explanation.
A pattern match does not automatically apply a penalty.
4.8 Imbalance
An imbalance represents a configured relationship or gap between dimensions or signals.

Imbalances are diagnostic findings only.

They MUST NOT modify scores or trigger deductions implicitly.
4.9 Explainability trace
The trace is the structured record of the complete scoring process, including inputs, normalized values, contributions, patterns, imbalances, penalty candidates, suppression decisions, adjusted scores, and final LIS.

The explanation layer reads this trace. It must not independently recalculate scores.
4.10 Response scope (amendment D45)
Each answer carries a scope: MEMBER, TEAM or ORGANIZATION.
- A MEMBER answer represents one respondent. The respondent's identity is not part of the model and is never stored or returned.
- A TEAM or ORGANIZATION answer is a figure already combined by the caller. It may declare how many respondents it represents; a declared count is caller-supplied and is labelled "declared" in the trace. When no count is declared, participation is "unknown".
- A team is an opaque identifier within an organization. No team registry is defined.
An answer without a scope is an ORGANIZATION answer with unknown participation, so earlier requests keep their meaning, input hash and result.
4.11 Aggregation
All answers to one question within one scoring unit (the organization, or one team) are normalized individually and combined into one normalized value by the configured method, MEAN or MEDIAN (default MEDIAN, configurable per question). Aggregation happens after normalization so direction is preserved (Section 3.1).

A question may have answers of one scope only within a unit; otherwise the request is rejected with MIXED_RESPONSE_SCOPES. Two answers from one respondent are not distinguishable and are never deduplicated. A signal derived from text or flags is present when at least a configured share of the responses carry it (default 0.5, unvalidated).
4.12 Minimum responses and anonymity
The configuration defines a minimum number of responses (default 5, unvalidated). When a question has fewer responses it is treated as missing under its missing-data policy (Section 8.10) and never as zero. A unit whose coverage is then insufficient is INSUFFICIENT_DATA and has no score. No result, evidence or answer view may single out an individual respondent.
4.13 Results by scope
A calculation produces one result for the organization, computed from all responses pooled with the same rules, and one result for each team that has sufficient data. Results are compared only within the same scope, and a team is never ranked against another team by the engine.

4.14 Questionnaires, runs and respondents (amendment D47, D48)
A questionnaire is a versioned set of questions with their scoring configuration, published as a template layer of one tenant (Section 23.2). A published version is immutable; changing a questionnaire publishes a new version and archives the previous one. A questionnaire may be published only if every dimension has at least one scored question and its generated configuration passes validation (Section 23.4). Questions that are collected but not scored (free text) are not mapped to a dimension.
An assessment run pins one published questionnaire version for one organization and a set of teams, and is open or closed. A participant is a person of the roster who is expected to answer; the participant record states whether the person has not started, is in progress or has finished, and never contains answers.
A respondent's unfinished answers (a draft) may be linked to the participant until submit, so the person can continue in another session. On submit the answers are stored with the team only: they carry no reference to the participant, no timestamp and no order, and the draft is deleted. Submitting is atomic.
Closing a run, which only an administrator may do, scores the stored answers once as member answers (Sections 4.10 to 4.13) with the pinned configuration version, so changing a questionnaire cannot change an open run. Answers are never returned one by one and never linked to a person. A question marked required must be answered before a respondent can submit. Free-text answers are collected and never scored.
Completion records show who has not finished; in a very small team that can let an administrator infer who is missing, which the minimum-response rule (Section 4.12) does not prevent and the interface must state.
5. High-level architecture
The system follows Clean Architecture / Hexagonal Architecture.
#chatgpt-mermaid-_r_14b_{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;fill:rgb(13, 13, 13);}@keyframes edge-animation-frame{from{stroke-dashoffset:0;}}@keyframes dash{to{stroke-dashoffset:0;}}#chatgpt-mermaid-_r_14b_ .edge-animation-slow{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 50s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-_r_14b_ .edge-animation-fast{stroke-dasharray:9,5!important;stroke-dashoffset:900;animation:dash 20s linear infinite;stroke-linecap:round;}#chatgpt-mermaid-_r_14b_ .error-icon{fill:rgb(243, 243, 243);}#chatgpt-mermaid-_r_14b_ .error-text{fill:rgb(13, 13, 13);stroke:rgb(13, 13, 13);}#chatgpt-mermaid-_r_14b_ .edge-thickness-normal{stroke-width:1px;}#chatgpt-mermaid-_r_14b_ .edge-thickness-thick{stroke-width:3.5px;}#chatgpt-mermaid-_r_14b_ .edge-pattern-solid{stroke-dasharray:0;}#chatgpt-mermaid-_r_14b_ .edge-thickness-invisible{stroke-width:0;fill:none;}#chatgpt-mermaid-_r_14b_ .edge-pattern-dashed{stroke-dasharray:3;}#chatgpt-mermaid-_r_14b_ .edge-pattern-dotted{stroke-dasharray:2;}#chatgpt-mermaid-_r_14b_ .marker{fill:rgb(143, 143, 143);stroke:rgb(143, 143, 143);}#chatgpt-mermaid-_r_14b_ .marker.cross{stroke:rgb(143, 143, 143);}#chatgpt-mermaid-_r_14b_ svg{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:16px;}#chatgpt-mermaid-_r_14b_ p{margin:0;}#chatgpt-mermaid-_r_14b_ .label{font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";color:rgb(13, 13, 13);}#chatgpt-mermaid-_r_14b_ .cluster-label text{fill:rgb(13, 13, 13);}#chatgpt-mermaid-_r_14b_ .cluster-label span{color:rgb(13, 13, 13);}#chatgpt-mermaid-_r_14b_ .cluster-label span p{background-color:transparent;}#chatgpt-mermaid-_r_14b_ .label text,#chatgpt-mermaid-_r_14b_ span{fill:rgb(13, 13, 13);color:rgb(13, 13, 13);}#chatgpt-mermaid-_r_14b_ .node rect,#chatgpt-mermaid-_r_14b_ .node circle,#chatgpt-mermaid-_r_14b_ .node ellipse,#chatgpt-mermaid-_r_14b_ .node polygon,#chatgpt-mermaid-_r_14b_ .node path{fill:rgb(222, 234, 251);stroke:rgb(83, 154, 248);stroke-width:1px;}#chatgpt-mermaid-_r_14b_ .rough-node .label text,#chatgpt-mermaid-_r_14b_ .node .label text,#chatgpt-mermaid-_r_14b_ .image-shape .label,#chatgpt-mermaid-_r_14b_ .icon-shape .label{text-anchor:middle;}#chatgpt-mermaid-_r_14b_ .node .katex path{fill:#000;stroke:#000;stroke-width:1px;}#chatgpt-mermaid-_r_14b_ .rough-node .label,#chatgpt-mermaid-_r_14b_ .node .label,#chatgpt-mermaid-_r_14b_ .image-shape .label,#chatgpt-mermaid-_r_14b_ .icon-shape .label{text-align:center;}#chatgpt-mermaid-_r_14b_ .node.clickable{cursor:pointer;}#chatgpt-mermaid-_r_14b_ .root .anchor path{fill:rgb(143, 143, 143)!important;stroke-width:0;stroke:rgb(143, 143, 143);}#chatgpt-mermaid-_r_14b_ .arrowheadPath{fill:rgb(143, 143, 143);}#chatgpt-mermaid-_r_14b_ .edgePath .path{stroke:rgb(143, 143, 143);stroke-width:1px;}#chatgpt-mermaid-_r_14b_ .flowchart-link{stroke:rgb(143, 143, 143);fill:none;}#chatgpt-mermaid-_r_14b_ .edgeLabel{background-color:rgb(252, 252, 252);text-align:center;}#chatgpt-mermaid-_r_14b_ .edgeLabel p{background-color:rgb(252, 252, 252);}#chatgpt-mermaid-_r_14b_ .edgeLabel rect{opacity:0.5;background-color:rgb(252, 252, 252);fill:rgb(252, 252, 252);}#chatgpt-mermaid-_r_14b_ .labelBkg{background-color:rgba(252, 252, 252, 0.5);}#chatgpt-mermaid-_r_14b_ .cluster rect{fill:rgb(243, 243, 243);stroke:rgba(0, 0, 0, 0.1);stroke-width:1px;}#chatgpt-mermaid-_r_14b_ .cluster text{fill:rgb(13, 13, 13);}#chatgpt-mermaid-_r_14b_ .cluster span{color:rgb(13, 13, 13);}#chatgpt-mermaid-_r_14b_ div.mermaidTooltip{position:absolute;text-align:center;max-width:200px;padding:2px;font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";font-size:12px;background:rgb(243, 243, 243);border:1px solid rgba(0, 0, 0, 0.1);border-radius:2px;pointer-events:none;z-index:100;}#chatgpt-mermaid-_r_14b_ .flowchartTitleText{text-anchor:middle;font-size:18px;fill:rgb(13, 13, 13);}#chatgpt-mermaid-_r_14b_ rect.text{fill:none;stroke-width:0;}#chatgpt-mermaid-_r_14b_ .icon-shape,#chatgpt-mermaid-_r_14b_ .image-shape{background-color:rgb(252, 252, 252);text-align:center;}#chatgpt-mermaid-_r_14b_ .icon-shape p,#chatgpt-mermaid-_r_14b_ .image-shape p{background-color:rgb(252, 252, 252);padding:2px;}#chatgpt-mermaid-_r_14b_ .icon-shape .label rect,#chatgpt-mermaid-_r_14b_ .image-shape .label rect{opacity:0.5;background-color:rgb(252, 252, 252);fill:rgb(252, 252, 252);}#chatgpt-mermaid-_r_14b_ .label-icon{display:inline-block;height:1em;overflow:visible;vertical-align:-0.125em;}#chatgpt-mermaid-_r_14b_ .node .label-icon path{fill:currentColor;stroke:revert;stroke-width:revert;}#chatgpt-mermaid-_r_14b_ .node .neo-node{stroke:rgb(83, 154, 248);}#chatgpt-mermaid-_r_14b_ [data-look="neo"].node rect,#chatgpt-mermaid-_r_14b_ [data-look="neo"].cluster rect,#chatgpt-mermaid-_r_14b_ [data-look="neo"].node polygon{stroke:url(#chatgpt-mermaid-_r_14b_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-_r_14b_ [data-look="neo"].swimlane.cluster rect{filter:none;}#chatgpt-mermaid-_r_14b_ [data-look="neo"].node path{stroke:url(#chatgpt-mermaid-_r_14b_-gradient);stroke-width:1px;}#chatgpt-mermaid-_r_14b_ [data-look="neo"].node .outer-path{filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-_r_14b_ [data-look="neo"].node .neo-line path{stroke:rgb(83, 154, 248);filter:none;}#chatgpt-mermaid-_r_14b_ [data-look="neo"].node circle{stroke:url(#chatgpt-mermaid-_r_14b_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-_r_14b_ [data-look="neo"].node circle .state-start{fill:#000000;}#chatgpt-mermaid-_r_14b_ [data-look="neo"].icon-shape .icon{fill:url(#chatgpt-mermaid-_r_14b_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-_r_14b_ [data-look="neo"].icon-shape .icon-neo path{stroke:url(#chatgpt-mermaid-_r_14b_-gradient);filter:drop-shadow( 1px 2px 2px rgba(185,185,185,1));}#chatgpt-mermaid-_r_14b_ .node text{font-size:14px;font-weight:600;letter-spacing:normal;fill:rgb(0, 79, 153);}#chatgpt-mermaid-_r_14b_ .edgeLabels text{font-size:13px;font-weight:600;letter-spacing:-0.08px;fill:rgb(0, 79, 153);}#chatgpt-mermaid-_r_14b_ .node tspan[font-weight="normal"],#chatgpt-mermaid-_r_14b_ .edgeLabels tspan[font-weight="normal"]{font-weight:600;}#chatgpt-mermaid-_r_14b_ .edgeLabel .label rect{opacity:1;rx:13px;ry:13px;fill:rgb(245, 250, 255);stroke:rgb(206, 219, 229);stroke-width:1px;}#chatgpt-mermaid-_r_14b_ .node rect,#chatgpt-mermaid-_r_14b_ .node circle,#chatgpt-mermaid-_r_14b_ .node ellipse,#chatgpt-mermaid-_r_14b_ .node polygon,#chatgpt-mermaid-_r_14b_ .node path{fill:rgb(229, 243, 255);stroke:rgba(0, 0, 0, 0.1);stroke-width:1px;}#chatgpt-mermaid-_r_14b_ .node rect{rx:16px;ry:16px;}#chatgpt-mermaid-_r_14b_ .mermaid-decision .label-container{fill:rgb(245, 250, 255);stroke:rgb(206, 219, 229);stroke-dasharray:2,2;}#chatgpt-mermaid-_r_14b_ .edgePaths .flowchart-link{stroke:rgb(143, 143, 143);stroke-width:1px;stroke-linecap:round;stroke-linejoin:round;}#chatgpt-mermaid-_r_14b_ .marker{fill:rgb(143, 143, 143);stroke:rgb(143, 143, 143);}#chatgpt-mermaid-_r_14b_ :root{--mermaid-font-family:-apple-system-body,ui-sans-serif,-apple-system,system-ui,"Segoe UI",Helvetica,"Apple Color Emoji",Arial,sans-serif,"Segoe UI Emoji","Segoe UI Symbol";}Assessment APIApplication LayerScoring OrchestratorConfiguration SnapshotProviderInput ValidationNormalization ServiceSignal DerivationDimension ScoringImbalance DetectionPattern DetectionPenalty EligibilityPenalty EvaluationPenalty AggregationAdjusted DimensionsOverall LISInterpretationExplainabilityImmutable Scoring RunAPI Response





5.1 Architectural boundaries
Domain layer

Contains:
- Scoring models.
- Value objects.
- Domain rules.
- Scoring strategies.
- Condition evaluation.
- Penalty calculation.
- Dimension aggregation.
- Domain invariants.
It MUST NOT depend on:
- Spring.
- JPA.
- PostgreSQL.
- HTTP.
- Jackson.
- AWS.
- Redis.
- External APIs.
Application layer

Coordinates use cases:
- Score assessment.
- Recalculate assessment.
- Retrieve scoring run.
- Retrieve explanation.
- Validate configuration.
- Activate configuration.
It owns transaction boundaries.

Infrastructure layer

Implements:
- Repositories.
- Persistence.
- Configuration loading.
- Caching.
- Messaging.
- External integrations.
- Database transactions.
- Security integration.
API layer

Implements:
- REST controllers.
- Request/response DTOs.
- Validation.
- Mapping.
- API error handling.
- OpenAPI documentation.

6. Canonical processing pipeline
The pipeline MUST execute in the following logical order.
1. Resolve authenticated tenant context.
2. Validate assessment access and completion status.
3. Load the immutable assessment snapshot.
4. Resolve the effective scoring configuration.
5. Materialize and validate the configuration snapshot.
6. Resolve question definitions and applicable scoring rules.
7. Validate assessment inputs.
8. Normalize all valid answers.
9. Derive deterministic signals, including configured text flags.
10. Validate minimum data coverage.
11. Calculate raw dimension scores.
12. Detect imbalances using configured score basis.
13. Detect organizational patterns.
14. Evaluate pattern evidence and confidence.
15. Evaluate penalty eligibility.
16. Calculate candidate penalties.
17. Aggregate penalties and record suppression.
18. Calculate adjusted dimension scores.
19. Calculate weighted adjusted score.
20. Apply overall penalties once.
21. Calculate final LIS.
22. Determine interpretation or insufficient-data status.
23. Build the complete explainability trace.
24. Persist the immutable scoring run.
25. Return the scoring result.
6.1 Pipeline invariants
- The effective configuration MUST be loaded once per scoring operation.
- All stages MUST use the same immutable assessment and configuration snapshots.
- Pattern detection MUST NOT mutate scores.
- Imbalance detection MUST NOT mutate scores.
- Penalty calculation MUST NOT happen implicitly inside pattern detection.
- Dimension penalties MUST be applied before overall weighting.
- Overall penalties MUST be applied after weighted dimension aggregation.
- Penalties MUST NOT be applied twice.
- Explainability MUST consume the completed trace.
- Persistence MUST preserve the exact scoring inputs and configuration identity needed for reproducibility.
6.2 Evaluation basis
The default evaluation basis for pattern and penalty conditions is:
- Normalized signals.
- Derived deterministic signals.
- Raw dimension scores.
- Explicitly available historical signals, if the assessment snapshot includes them.
Adjusted dimension scores MUST NOT be used as inputs to the same run's pattern evaluation unless a configuration explicitly defines a separate, versioned second-stage evaluation. The initial implementation MUST use raw dimension scores to avoid circular dependencies.

7. Input contract
7.1 Internal scoring request
The application service receives a context and a list of scoring inputs.
public record ScoringCommand(
    UUID assessmentId,
    UUID assessmentSnapshotId,
    List<ScoringInput> inputs
) {}


Tenant and organization identity must be resolved from trusted server-side context and the assessment record.

They must not be accepted as authoritative values from a client request.
7.2 API request example
{
  "assessmentId": "assessment-001"
}


The server loads the immutable assessment snapshot and its answers.

If a separate internal API is required for batch scoring, it may accept inputs, but it must validate them against the persisted assessment and its template version.
7.3 Example input set
{
  "inputs": [
    {
      "questionId": "Q01",
      "answer": 4
    },
    {
      "questionId": "Q02",
      "answer": "OFTEN"
    },
    {
      "questionId": "Q03",
      "answer": true
    },
    {
      "questionId": "Q04",
      "answer": "We frequently depend on one senior engineer."
    }
  ]
}


7.4 Input validation
The engine MUST validate:
- Question exists in the assessment template version.
- Question belongs to the assessment.
- Answer type is compatible with the question.
- Duplicate question answers follow an explicit template policy.
- Required answers are present.
- Answer values meet configured constraints.
- No unknown question IDs are accepted.
- No client-provided configuration is trusted.
- No untrusted tenant or organization ID overrides server context.
7.5 Request idempotency
The API SHOULD support an idempotency key for scoring requests.

A repeated request with the same key and same effective input must not create duplicate scoring runs.

The server must reject reuse of an idempotency key with a materially different request.

8. Normalization
8.1 Principle
All numeric scoring signals must be represented on a canonical scale:
\[
0 \leq normalizedValue \leq 100
\]

The semantic direction is always:
100 = desirable
  0 = undesirable


Normalization must not calculate dimension scores, penalties, or LIS.
8.2 Service contract
Normalization receives a list of inputs and an immutable configuration snapshot.
public interface NormalizationService {

    List<NormalizedInput> normalize(
        List<ScoringInput> inputs,
        ScoringConfigurationSnapshot configuration
    );
}


The request MUST NOT contain normalization configuration.
8.3 Configuration resolution
questionId
    |
    v
question definition
    |
    v
question type
    |
    v
question normalization mapping
    |
    v
normalization configuration
    |
    v
NormalizationStrategy
    |
    v
NormalizedInput


Question-specific configuration overrides type-level defaults only according to the documented configuration merge rules.
8.4 Normalization strategies
The initial implementation must include:
public interface NormalizationStrategy {

    QuestionType supportedType();

    NormalizationResult normalize(
        ScoringInput input,
        QuestionDefinition question,
        NormalizationConfig configuration
    );
}


Required implementations:
- ScaleNormalizationStrategy
- MultipleChoiceNormalizationStrategy
- BooleanNormalizationStrategy
- NumberNormalizationStrategy
- TextNormalizationStrategy
8.5 SCALE normalization
For positive scales:
\[
N = \frac{x-min}{max-min} \times 100
\]

For negative scales:
\[
N = 100 - \left(\frac{x-min}{max-min} \times 100\right)
\]

Example:
{
  "type": "SCALE",
  "min": 1,
  "max": 5,
  "direction": "POSITIVE",
  "outOfRangePolicy": "REJECT"
}


For a negative-direction question:
{
  "type": "SCALE",
  "min": 1,
  "max": 5,
  "direction": "NEGATIVE",
  "outOfRangePolicy": "REJECT"
}


Required rules:
- min MUST be lower than max.
- Values outside the configured range MUST follow an explicit policy.
- The engine MUST NOT silently clamp unless the configuration explicitly allows it.
- Decimal values must be supported where the question definition permits them.
8.6 MULTIPLE_CHOICE normalization
Each answer option must map to an explicit normalized value.
{
  "type": "MULTIPLE_CHOICE",
  "values": {
    "NEVER": 0,
    "RARELY": 25,
    "SOMETIMES": 50,
    "OFTEN": 75,
    "ALWAYS": 100
  }
}


The configuration must define behavior for unknown options.

Unknown options MUST NOT default to zero.
8.7 BOOLEAN normalization
{
  "type": "BOOLEAN",
  "trueValue": 100,
  "falseValue": 0
}


The configuration may reverse the values for negatively directed questions.

Null is missing data, not false.
8.8 NUMBER normalization
Numeric normalization must support:
- Minimum and maximum.
- Linear transformation.
- Positive or negative direction.
- Optional piecewise mappings.
- Decimal precision.
- Out-of-range policy.
Example:
{
  "type": "NUMBER",
  "min": 0,
  "max": 100,
  "direction": "POSITIVE",
  "outOfRangePolicy": "REJECT"
}


8.9 TEXT normalization
Text answers MUST NOT be converted directly into arbitrary numeric scores.

The initial implementation uses deterministic, configured text flags.

Example:
{
  "type": "TEXT",
  "signals": [
    {
      "id": "SINGLE_POINT_OF_FAILURE",
      "matchMode": "KEYWORD_ANY",
      "keywords": [
        "only",
        "single",
        "one person",
        "key person"
      ]
    }
  ]
}


Text processing requirements:
- Matching rules must be deterministic.
- Matching must be versioned.
- Text-derived signals must preserve their source question.
- Matching results must be included in explainability.
- Keyword matching must not claim semantic certainty.
- A missing text answer must not be interpreted as absence of a risk.
- Raw text retention and exposure must follow privacy policy.
LLM-based semantic classification is a future extension. If introduced, it must be treated as a separate signal-generation process with its own model/version, provenance, confidence, and reproducibility controls. It must not silently become part of canonical deterministic scoring.
8.10 Missing values
Missing values MUST NOT silently become zero.

A question configuration must define one of these policies:
- IGNORE: exclude the input from applicable calculations.
- NEUTRAL: use an explicitly configured neutral value.
- RISK_SIGNAL: derive a configured missing-data signal.
- REQUIRED: invalidate scoring when absent.
The policy must be explicit and explainable.

NEUTRAL must not be assumed to mean 50. The neutral value must be configured for the specific question or signal.
8.11 Normalization result
A normalized result should contain:
public record NormalizedInput(
    String questionId,
    QuestionType questionType,
    BigDecimal normalizedValue,
    Set<String> detectedSignals,
    NormalizationStatus status,
    String strategyId,
    String strategyVersion
) {}


The implementation may use additional fields for provenance, evidence, or missing-data status.

9. Signal derivation and provenance
Normalization and signal derivation are related but distinct.

A normalized answer is a numeric representation of an answer.

A signal is a named, typed observation used by scoring, pattern, imbalance, and penalty rules.

Examples:
- TEAM_SUSTAINABILITY
- WORKLOAD_PRESSURE
- WORK_IN_PROGRESS
- PRODUCTION_INCIDENT_FREQUENCY
- SINGLE_POINT_OF_FAILURE
- DECISION_LATENCY
9.1 Signal contract
Each signal must have:
- Stable identifier.
- Data type.
- Source.
- Value.
- Observation period.
- Unit, if applicable.
- Freshness.
- Reliability metadata.
- Independence/correlation group.
- Provenance reference.
9.2 Derived signals
Derived signals must use deterministic, versioned rules.

For example, a signal derived from multiple normalized questions must retain references to every source input and the derivation rule version.
9.3 Signal registry
A signal registry must define:
- Signal identifier.
- Data type.
- Allowed operators.
- Unit.
- Valid range, if numeric.
- Source eligibility.
- Dimension relationship.
- Whether the signal is available for patterns.
- Whether the signal is available for penalties.
- Privacy classification.
Unknown signals must fail configuration validation.

10. Dimension scoring
10.1 Dimensions
public enum ScoringDimension {
    TEAM_HEALTH,
    DELIVERY_STABILITY,
    TECHNICAL_RISK,
    LEADERSHIP_LOAD
}


10.2 Question-to-dimension mapping
Each applicable question or signal must be mapped through configuration.

Example:
{
  "questionId": "Q15",
  "contributions": [
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


A question may contribute to multiple dimensions only when explicitly configured.

Multiple-dimension contributions must be reviewed for double-counting and correlation.

The system must retain enough metadata to explain why a signal contributes to each dimension.
10.3 Weighted average
The initial aggregation strategy is WeightedAverageStrategy.

For dimension \(d\):
\[
D_{raw}[d] =
\frac{
\sum_i(normalizedValue_i \times contributionWeight_i)
}{
\sum_i contributionWeight_i
}
\]

Only valid, applicable contributions are included in both numerator and denominator.

The denominator MUST NOT include:
- Missing answers excluded by policy.
- Invalid answers.
- Inapplicable questions.
- Zero-weight contributions.
- Signals rejected by validation.
10.4 Zero denominator
If no valid contribution exists, the dimension status is:
INSUFFICIENT_DATA


It must not default to zero or 50.
10.5 Aggregation strategies
public interface AggregationStrategy {

    AggregationResult aggregate(
        List<WeightedContribution> contributions,
        AggregationConfiguration configuration
    );
}


Initial implementation:

- WeightedAverageStrategy

Future strategies may include:
- Median.
- Weighted median.
- Configured percentile.
- Explicit composite formulas.
A new strategy requires an algorithm version and tests.
10.6 Coverage
Dimension coverage must be calculated and retained.

At minimum, record:
- Applicable question count.
- Answered question count.
- Valid contribution count.
- Expected contribution weight.
- Observed contribution weight.
- Coverage percentage.
- Required coverage threshold.
- Coverage status.
Coverage thresholds are configurable.

A dimension with insufficient coverage must not be presented as a fully reliable score.
10.7 Double-counting controls
The configuration must support:
- Signal correlation groups.
- Explicit multiple-dimension mapping.
- Maximum contribution count per signal.
- Duplicate source detection.
- Optional correlated-contribution policies.
No automatic correlation coefficient or statistical independence should be assumed without a defined algorithm and data.

11. Dimension directionality
All dimension scores use the same direction.
100 = healthy/desirable
  0 = unhealthy/undesirable


Example:

Question:
How frequently are releases delayed?

If a high raw answer indicates more delays, its normalization direction must be negative.
Raw answer: 5 (very frequently)
Normalized answer: 0


The dimension-scoring service must not reverse direction a second time.

Directionality belongs to normalization or explicitly defined signal transformation.

12. Imbalance detection
Imbalance detection identifies meaningful differences between dimensions or signals.

It is a diagnostic operation, not a scoring operation.
12.1 Example
TEAM_HEALTH         = 82
DELIVERY_STABILITY  = 79
TECHNICAL_RISK      = 34
LEADERSHIP_LOAD     = 38


Possible finding:
Delivery performance is strong relative to technical resilience.

12.2 Configuration
{
  "imbalanceRules": [
    {
      "id": "DELIVERY_TECHNICAL_GAP",
      "leftDimension": "DELIVERY_STABILITY",
      "rightDimension": "TECHNICAL_RISK",
      "gapOperator": "GTE",
      "threshold": 30,
      "severity": "HIGH",
      "evaluationBasis": "RAW_DIMENSION_SCORE"
    }
  ]
}


12.3 Result
An imbalance result must include:
- Rule ID and version.
- Detection status.
- Involved dimensions/signals.
- Left and right values.
- Gap calculation.
- Gap direction.
- Threshold.
- Severity.
- Evaluation basis.
- Explanation key.
- Evidence references.
12.4 Imbalance invariants
- Imbalance detection MUST NOT modify scores.
- Missing dimension scores MUST NOT be treated as zero.
- Threshold boundaries must be explicit.
- Gap direction must be defined.
- Only valid configured dimensions/signals may be referenced.
The default evaluation basis is raw dimension scores.

13. Organizational failure patterns
Patterns identify organizational conditions that cannot be adequately described by a single score.

They may use:
- Normalized inputs.
- Derived signals.
- Raw dimension scores.
- Dimension gaps.
- Configured text flags.
- Historical metrics included in the scoring context.
13.1 Pattern definition
A pattern definition must include:
- Stable pattern ID and code.
- Name and description.
- Category.
- Severity.
- Version.
- Lifecycle status.
- Enabled flag.
- Effective dates.
- Condition expression.
- Evidence requirements.
- Confidence requirements.
- Penalty eligibility.
- Optional penalty references.
- Explanation keys.
- Recommended action keys.
13.2 Pattern categories
Allowed initial categories:
- TEAM_HEALTH
- DELIVERY_STABILITY
- TECHNICAL_RISK
- LEADERSHIP_LOAD
- CROSS_DIMENSIONAL
13.3 Initial pattern catalog
The catalog defines stable identifiers and a taxonomy. A catalog entry is not executable until its conditions, signals, evidence requirements, and thresholds have been configured and validated.
Team Health
ID	Pattern
TH-001	Team Burnout Risk
TH-002	Low Psychological Safety
TH-003	Collaboration Breakdown
TH-004	Low Team Autonomy
TH-005	Knowledge Silos
TH-006	Team Disengagement
TH-007	Excessive Context Switching
TH-008	Role Ambiguity
TH-009	Organizational Friction
TH-010	Unsustainable Workload

Delivery Stability
ID	Pattern
DS-001	Chronic Delivery Unpredictability
DS-002	Planning Instability
DS-003	Excessive Work in Progress
DS-004	Dependency Bottleneck
DS-005	Delivery Heroics
DS-006	Reactive Development
DS-007	Weak Delivery Ownership
DS-008	Estimation Dysfunction
DS-009	Release Instability
DS-010	Process Overhead

Technical Risk
ID	Pattern
TR-001	Critical Technical Debt
TR-002	Architectural Fragility
TR-003	Single Point of Failure
TR-004	Weak Test Automation
TR-005	Production Instability
TR-006	Poor Observability
TR-007	Security Exposure
TR-008	Scalability Constraints
TR-009	Deployment Bottleneck
TR-010	Technical Knowledge Concentration

Leadership Load
ID	Pattern
LL-001	Leadership Bottleneck
LL-002	Micromanagement
LL-003	Delegation Deficit
LL-004	Decision Latency
LL-005	Excessive Span of Control
LL-006	Leadership Overload
LL-007	Lack of Strategic Alignment
LL-008	Weak Succession Coverage
LL-009	Escalation Dependency
LL-010	Leadership Context Switching

Cross-dimensional
ID	Pattern
XD-001	Unsustainable Delivery Model
XD-002	Leadership-Driven Bottleneck
XD-003	Technical Debt Acceleration
XD-004	Organizational Dependency Trap
XD-005	Hidden Organizational Fragility
XD-006	Leadership Capacity Crisis
XD-007	Growth Without Engineering Maturity
XD-008	Hero Culture

Each catalog entry must be explicitly configured before activation. The system MUST NOT infer missing conditions from the pattern name.
13.4 Condition expression model
Conditions are represented as recursive expression trees.

Supported group operators:
- AND
- OR
- NOT
Supported leaf operators:
- EQ
- NE
- GT
- GTE
- LT
- LTE
- BETWEEN
- IN
- NOT_IN
- EXISTS
- NOT_EXISTS
- CONTAINS
- GAP_GT
- GAP_GTE
NOT must have exactly one child.

GAP_* operators must define both operands and gap direction.

Example:
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
      {
        "signal": "WORKLOAD_PRESSURE",
        "operator": "GTE",
        "value": 75
      },
      {
        "signal": "TEAM_SUSTAINABILITY",
        "operator": "LTE",
        "value": 35
      }
    ]
  },
  "evidenceRequirements": {
    "minimumSignals": 2,
    "minimumConfidence": 0.70
  },
  "penaltyEligible": true,
  "recommendedActions": [
    "REVIEW_TEAM_CAPACITY",
    "REVIEW_WORKLOAD_DISTRIBUTION"
  ]
}


13.5 Condition validation
The evaluator must reject:
- Unknown signal IDs.
- Unknown dimensions.
- Invalid operator/data-type combinations.
- Missing required operands.
- Invalid expression arity.
- Invalid threshold ranges.
- Unsupported historical references.
- Invalid gap definitions.
- Invalid null/missing-data semantics.
The evaluator must not silently coerce missing values to zero.
13.6 Pattern statuses
Each evaluated pattern must have exactly one status:
MATCHED
NOT_MATCHED
INELIGIBLE
NOT_EVALUATED


Definitions:
- MATCHED: Conditions satisfied.
- NOT_MATCHED: Conditions evaluated and not satisfied.
- INELIGIBLE: A match or potential match exists, but evidence or eligibility requirements prevent an eligible finding.
- NOT_EVALUATED: The engine could not evaluate the pattern due to missing required context, inactive configuration, or another documented reason.
The result must include a reason code.

14. Evidence and confidence
14.1 Evidence
Every pattern match must retain evidence sufficient to explain the finding.

Evidence records should include:
- Source question/signal ID.
- Source reference.
- Raw value, if permitted.
- Normalized value, if applicable.
- Observation period.
- Timestamp.
- Condition result.
- Reliability.
- Freshness.
- Independence/correlation group.
- Confidence contribution.
14.2 Confidence
Confidence measures evidence quality and sufficiency.

It is not automatically a probability that a pattern is true.

Confidence must be a deterministic, versioned calculation.

The algorithm must define:
- Evidence reliability.
- Evidence freshness.
- Source independence.
- Missing evidence behavior.
- Aggregation method.
- Output range.
- Rounding.
Confidence is represented in:
\[
0 \leq confidence \leq 1
\]

14.2.1 Participation factor (amendment D45)
When the responses behind the evidence carry participation, confidence includes one more multiplicative factor, participation = min(1, respondents / fullConfidenceResponses), where fullConfidenceResponses is configurable (default 10, unvalidated). It uses the lowest respondent count among the questions that support the pattern's evidence. Participation that is unknown gives a factor of 1 and is labelled unknown. Runs without participation keep confidence algorithm version 1; runs with participation use version 2.
14.3 Provisional confidence bands
The following bands are illustrative and must remain configurable:
Range	Label
0.00–0.39	INSUFFICIENT
0.40–0.59	WEAK
0.60–0.79	MODERATE
0.80–1.00	STRONG

These bands are not scientifically calibrated thresholds.
14.4 Evidence independence
Multiple questions measuring the same underlying phenomenon must not automatically count as independent corroboration.

The configuration must support:
- Correlation groups.
- Evidence source groups.
- Minimum independent signal count.
- Minimum confidence.
- Source reliability.
- Evidence freshness.
14.5 Severity versus confidence
Severity and confidence are independent.
- Severity represents the configured impact classification.
- Confidence represents evidence quality.
A high-severity pattern with insufficient confidence may be retained as an ineligible finding, but it must not trigger a penalty.

15. Penalty model
Penalties are separately configured scoring deductions.

Pattern detection and penalty evaluation MUST remain separate.
15.1 Penalty targets
Allowed targets:
TEAM_HEALTH
DELIVERY_STABILITY
TECHNICAL_RISK
LEADERSHIP_LOAD
OVERALL


15.2 Penalty definition
A penalty definition includes:
- Penalty ID.
- Stable code.
- Name and description.
- Version.
- Trigger pattern or condition.
- Target.
- Strategy.
- Strategy configuration.
- Maximum individual deduction.
- Category.
- Exclusive group.
- Priority.
- Stacking policy.
- Maximum cumulative deduction.
- Evidence requirements.
- Confidence requirements.
- Enabled status.
- Effective dates.
- Explanation keys.
15.3 Supported strategies
FIXED
Deduct a configured number of points.
candidateDeduction = configuredValue


PROPORTIONAL
Calculate a deduction from an explicitly named score base.
candidateDeduction = scoreBase * configuredRate


The score base MUST be explicit.

It must not be inferred whether the base is:
- Raw dimension score.
- Dimension gap.
- Adjusted dimension score.
- Weighted LIS.
SEVERITY_BASED
Calculate a deduction using a severity multiplier.
candidateDeduction =
    baseDeduction * severityMultiplier


THRESHOLD_BASED
Calculate a deduction using a piecewise threshold schedule.

Every boundary must define whether it is inclusive or exclusive.
CONDITIONAL
Evaluate an explicit condition and delegate to a configured strategy.

The target and strategy must remain separate configuration properties.
15.4 Penalty configuration example
{
  "penaltyId": "TH-P01",
  "name": "Burnout Risk Penalty",
  "target": "TEAM_HEALTH",
  "strategy": "SEVERITY_BASED",
  "triggerPatternId": "TH-001",
  "severityMultipliers": {
    "LOW": 0,
    "MEDIUM": 0.4,
    "HIGH": 0.7,
    "CRITICAL": 1.0
  },
  "baseDeduction": 12,
  "maximumDeduction": 12,
  "category": "SUSTAINABILITY",
  "stackingPolicy": "MAX_PER_CATEGORY",
  "enabled": true
}


The example is illustrative, not a calibrated production rule.
15.5 Initial penalty catalog
The following values are suggested maximum deductions, not automatic deductions.
Team Health
ID	Trigger pattern	Target	Suggested max
TH-P01	TH-001 Burnout Risk	TEAM_HEALTH	12
TH-P02	TH-002 Psychological Safety Deficit	TEAM_HEALTH	8
TH-P03	TH-003 Collaboration Breakdown	TEAM_HEALTH	8
TH-P04	TH-004 Low Autonomy	TEAM_HEALTH	6
TH-P05	TH-005 Knowledge Silos	TEAM_HEALTH	8
TH-P06	TH-006 Disengagement	TEAM_HEALTH	8
TH-P07	TH-007 Context Switching	TEAM_HEALTH	5
TH-P08	TH-008 Role Ambiguity	TEAM_HEALTH	5

Delivery Stability
ID	Trigger pattern	Target	Suggested max
DS-P01	DS-001 Delivery Unpredictability	DELIVERY_STABILITY	10
DS-P02	DS-002 Planning Instability	DELIVERY_STABILITY	8
DS-P03	DS-003 Excessive WIP	DELIVERY_STABILITY	6
DS-P04	DS-004 Dependency Bottleneck	DELIVERY_STABILITY	8
DS-P05	DS-005 Delivery Heroics	DELIVERY_STABILITY	12
DS-P06	DS-006 Reactive Development	DELIVERY_STABILITY	8
DS-P07	DS-007 Weak Ownership	DELIVERY_STABILITY	5
DS-P08	DS-008 Estimation Dysfunction	DELIVERY_STABILITY	5
DS-P09	DS-009 Release Instability	DELIVERY_STABILITY	10

Technical Risk
ID	Trigger pattern	Target	Suggested max
TR-P01	TR-001 Critical Technical Debt	TECHNICAL_RISK	10
TR-P02	TR-002 Architectural Fragility	TECHNICAL_RISK	10
TR-P03	TR-003 Single Point of Failure	TECHNICAL_RISK	12
TR-P04	TR-004 Weak Test Automation	TECHNICAL_RISK	8
TR-P05	TR-005 Production Instability	TECHNICAL_RISK	15
TR-P06	TR-006 Poor Observability	TECHNICAL_RISK	6
TR-P07	TR-007 Security Exposure	TECHNICAL_RISK	15
TR-P08	TR-008 Scalability Constraints	TECHNICAL_RISK	8
TR-P09	TR-009 Deployment Bottleneck	TECHNICAL_RISK	5

Leadership Load
ID	Trigger pattern	Target	Suggested max
LL-P01	LL-001 Leadership Bottleneck	LEADERSHIP_LOAD	10
LL-P02	LL-002 Micromanagement	LEADERSHIP_LOAD	8
LL-P03	LL-003 Delegation Deficit	LEADERSHIP_LOAD	8
LL-P04	LL-004 Decision Latency	LEADERSHIP_LOAD	8
LL-P05	LL-005 Excessive Span of Control	LEADERSHIP_LOAD	6
LL-P06	LL-006 Leadership Overload	LEADERSHIP_LOAD	12
LL-P07	LL-007 Strategic Misalignment	LEADERSHIP_LOAD	8
LL-P08	LL-008 Weak Succession Coverage	LEADERSHIP_LOAD	5
LL-P09	LL-009 Escalation Dependency	LEADERSHIP_LOAD	8

Cross-dimensional
ID	Trigger pattern	Target	Suggested max
XD-P01	XD-001 Unsustainable Operating Model	OVERALL	8
XD-P02	XD-002 Leadership Bottleneck Impact	OVERALL	6
XD-P03	XD-003 Technical Delivery Fragility	OVERALL	6
XD-P04	XD-004 Organizational Dependency	OVERALL	6
XD-P05	XD-005 Hidden Organizational Fragility	OVERALL	10
XD-P06	XD-006 Leadership Capacity Crisis	OVERALL	8
XD-P07	XD-007 Engineering Maturity Gap	OVERALL	6
XD-P08	XD-008 Hero Culture	OVERALL	10

The catalog intentionally does not assign a penalty to every pattern. Informational patterns and patterns without defensible measurement may remain penalty-ineligible.

The implementation MUST NOT create missing penalties simply to make the catalog symmetrical.

16. Penalty stacking and overlap
16.1 Supported stacking policies
Policy	Behavior
STACK	Sum all eligible deductions
MAX	Keep only the largest deduction in the group
MAX_PER_CATEGORY	Keep the largest deduction per category
PRIORITY	Select by configured priority
CAPPED_STACK	Sum deductions up to a configured maximum
EXCLUSIVE_GROUP	Select one eligible member of a mutually exclusive group

16.2 Recommended initial policies
These are initial design recommendations and must be validated against representative assessment data.
- Within a dimension: CAPPED_STACK.
- Related categories: MAX_PER_CATEGORY.
- Mutually exclusive findings: EXCLUSIVE_GROUP.
- Overall penalties: CAPPED_STACK.
Each policy must be explicitly configured.
16.3 Deterministic tie-breaking
Never rely on collection iteration order.

Recommended tie-break sequence:
1. Explicit priority.
2. Configured rule order.
3. Penalty ID lexical order.
The tie-break algorithm must be versioned.
16.4 Candidate statuses
Every evaluated penalty candidate must have exactly one terminal status:
APPLIED
SUPPRESSED
INELIGIBLE
NOT_TRIGGERED


Each status must have a reason code.
16.5 Suppression trace
A suppressed penalty must retain:
- Candidate deduction.
- Winning penalty.
- Suppression policy.
- Suppression group.
- Suppressed amount.
- Reason.
- Evidence references.
16.6 Double-counting prevention
The system must support:
- Explicit overlap categories.
- Exclusive groups.
- Shared evidence identifiers.
- Correlation groups.
- Conflict metadata.
- Explicit target-level caps.
Overlapping findings must not be silently removed. They must remain available for explanation even if their deductions are suppressed.

A penalty must not be applied both to a dimension and overall merely because two different rules reference the same evidence. Any intentional dual application requires an explicit, reviewed configuration with a documented rationale.

17. Leadership Intelligence Score
The LIS is calculated from adjusted dimension scores.
17.1 Dimension adjustment
For each dimension:
\[
D_{adjusted}[d] =
\max(0,\ D_{raw}[d] - P_{dimension}[d])
\]

Where:
- \(D_{raw}\) is the raw dimension score.
- \(P_{dimension}\) is the effective aggregated dimension penalty.
17.2 Weighted adjusted score
\[
LIS_{weighted} =
\sum_d(D_{adjusted}[d] \times W[d])
\]

Where all weights are non-negative and sum to 1.0 within the configured decimal tolerance.
17.3 Overall penalties
\[
LIS =
CLAMP(
LIS_{weighted} - P_{overall},
0,
100
)
\]

The overall penalty is applied exactly once, after weighted adjusted dimension scores have been calculated.
17.4 Weight configuration
Example:
{
  "overallWeights": {
    "TEAM_HEALTH": 0.25,
    "DELIVERY_STABILITY": 0.25,
    "TECHNICAL_RISK": 0.25,
    "LEADERSHIP_LOAD": 0.25
  }
}


Weights are configuration, not hard-coded constants.
17.5 Insufficient data
If a required dimension has insufficient data, the default behavior is:
LIS status = INSUFFICIENT_DATA


The engine must not calculate a normal LIS using an incomplete set of required dimensions.

Partial scoring is permitted only when an explicit, versioned policy defines:
- Which dimensions may be omitted.
- Minimum coverage.
- Weight redistribution.
- Result status.
- Interpretation restrictions.
- API labeling.
Partial scoring must be clearly distinguished from a complete LIS.
17.6 Example calculation
Equal dimension weights:
TEAM_HEALTH         = 0.25
DELIVERY_STABILITY  = 0.25
TECHNICAL_RISK      = 0.25
LEADERSHIP_LOAD     = 0.25


Raw scores:
Dimension	Raw
Team Health	84
Delivery Stability	76
Technical Risk	66
Leadership Load	70

Effective dimension penalties:
Dimension	Penalty
Team Health	3
Delivery Stability	0
Technical Risk	8
Leadership Load	7

Adjusted scores:
Dimension	Adjusted
Team Health	81
Delivery Stability	76
Technical Risk	58
Leadership Load	63

Calculation:
\[
LIS = (81 \times 0.25) +
(76 \times 0.25) +
(58 \times 0.25) +
(63 \times 0.25)
\]

\[
LIS = 69.50
\]

With no overall penalty:
\[
LIS_{final}=69.50
\]

The raw Delivery Stability / Technical Risk gap is:
\[
76 - 66 = 10
\]

That gap may be reported as an imbalance if it satisfies a configured rule. It does not itself change the LIS.

18. Interpretation
The engine returns a numeric result and a configured interpretation.

Example:
{
  "ranges": [
    {
      "min": 0,
      "max": 39.99,
      "label": "CRITICAL"
    },
    {
      "min": 40,
      "max": 59.99,
      "label": "AT_RISK"
    },
    {
      "min": 60,
      "max": 79.99,
      "label": "HEALTHY"
    },
    {
      "min": 80,
      "max": 100,
      "label": "STRONG"
    }
  ]
}


These thresholds are illustrative, not empirically calibrated.
18.1 Interpretation requirements
- Ranges must not overlap.
- Ranges must cover the configured score interval.
- Boundary inclusivity must be explicit.
- Labels must be versioned.
- Dimension and LIS interpretation may use separate configurations.
- INSUFFICIENT_DATA must not be interpreted as a numeric risk category.
The interpretation service must not alter the numeric score.

19. Explainability
Explainability is a core system capability, not a presentation-only feature.

Every scoring run must provide a complete trace from source data to final result.
19.1 Required trace stages
Assessment snapshot
        |
        v
Source answer
        |
        v
Normalization
        |
        v
Derived signal
        |
        v
Dimension contribution
        |
        v
Raw dimension score
        |
        +-------------------+
        |                   |
        v                   v
Imbalance detection    Pattern detection
                            |
                            v
                    Evidence evaluation
                            |
                            v
                    Penalty eligibility
                            |
                            v
                    Candidate deduction
                            |
                            v
                    Aggregation/suppression
                            |
                            v
                    Effective penalty
                            |
                            v
                    Adjusted dimension
                            |
                            v
                    Weighted LIS
                            |
                            v
                    Overall penalty
                            |
                            v
                    Final LIS


19.2 Required explanation data
For every dimension:
- Raw score.
- Adjusted score.
- Coverage.
- Contribution list.
- Positive drivers.
- Negative drivers.
- Effective penalties.
- Calculation details.
For every pattern:
- Pattern ID/code/version.
- Evaluation status.
- Condition tree result.
- Evidence references.
- Severity.
- Confidence.
- Eligibility reason.
- Recommended action keys.
For every penalty:
- Penalty ID/code/version.
- Target.
- Strategy.
- Score base.
- Candidate deduction.
- Individual cap.
- Effective deduction.
- Stacking policy.
- Aggregation group.
- Suppression reason.
- Winning penalty, where applicable.
For LIS:
- Dimension weights.
- Adjusted dimension scores.
- Weighted contributions.
- Overall penalties.
- LIS before overall penalty.
- Final LIS.
- Interpretation.
19.3 Narrative generation
Explanations must be generated from structured scoring facts.

Narrative strings must not contain independently calculated values that can diverge from the scoring trace.

Narrative templates and recommendation text should use stable keys and versioned content.
19.4 No recalculation
The explanation service MUST NOT:
- Recalculate scores.
- Re-evaluate pattern rules.
- Recalculate penalties.
- Infer missing evidence.
- Change interpretation.
- Load a newer configuration in place of the historical configuration.
It must serialize the persisted trace for the selected scoring run.
19.5 Privacy
Raw answers and free-text responses may contain sensitive organizational information.

The API must apply role-based authorization and data minimization.

Explanation responses should expose only the evidence necessary for the caller's authorized use.

20. Result contract
20.1 Canonical scoring response
{
  "scoringRunId": "run-001",
  "assessmentId": "assessment-001",
  "assessmentSnapshotId": "snapshot-001",
  "tenantId": "tenant-001",
  "organizationId": "org-001",
  "engineVersion": "3.0.0",
  "algorithmVersion": "1.0.0",
  "configurationVersion": "config-v12",
  "configurationHash": "sha256:...",
  "inputHash": "sha256:...",
  "status": "COMPLETED",
  "scoreStatus": "VALID",
  "leadershipIntelligenceScore": 69.50,
  "interpretation": "HEALTHY",
  "dimensions": {
    "TEAM_HEALTH": {
      "rawScore": 84,
      "effectivePenalty": 3,
      "adjustedScore": 81,
      "coverage": 1.0,
      "status": "VALID"
    },
    "DELIVERY_STABILITY": {
      "rawScore": 76,
      "effectivePenalty": 0,
      "adjustedScore": 76,
      "coverage": 1.0,
      "status": "VALID"
    },
    "TECHNICAL_RISK": {
      "rawScore": 66,
      "effectivePenalty": 8,
      "adjustedScore": 58,
      "coverage": 1.0,
      "status": "VALID"
    },
    "LEADERSHIP_LOAD": {
      "rawScore": 70,
      "effectivePenalty": 7,
      "adjustedScore": 63,
      "coverage": 1.0,
      "status": "VALID"
    }
  },
  "overall": {
    "weightedAdjustedScore": 69.50,
    "effectiveOverallPenalty": 0,
    "finalScore": 69.50
  },
  "imbalances": [],
  "patterns": [],
  "penalties": [],
  "explanationReference": "run-001"
}


This is an illustrative response. Production values must be derived from the persisted scoring run.
20.2 Status definitions
Scoring run status:
PENDING
RUNNING
COMPLETED
FAILED


Score status:
VALID
INSUFFICIENT_DATA
PARTIAL


PARTIAL is allowed only when a partial-scoring policy has explicitly been enabled.
20.3 Stable contract requirements
- Numeric scores must use a documented precision.
- Missing values must be represented explicitly.
- Invalid dimensions must not appear as fabricated numeric scores.
- The response must identify engine and configuration versions.
- Pattern and penalty statuses must be explicit.
- The API must distinguish scoring execution status from score validity.

21. Domain model
Recommended domain objects:
AssessmentSnapshot
ScoringContext
ScoringInput
QuestionDefinition
NormalizedInput
NormalizationResult
DerivedSignal
SignalDefinition
DimensionContribution
DimensionScore
DimensionCoverage
PatternDefinition
PatternEvaluation
PatternEvidence
ImbalanceDefinition
ImbalanceResult
PenaltyDefinition
PenaltyEvaluation
PenaltyApplication
PenaltySuppression
PenaltyAggregation
ScoreInterpretation
ScoringExplanation
ScoringTrace
ScoringResult
ScoringRun
ScoringConfigurationSnapshot


21.1 Important value objects
Use value objects for:
- TenantId
- OrganizationId
- AssessmentId
- AssessmentSnapshotId
- ScoringRunId
- ConfigurationVersion
- DimensionScoreValue
- NormalizedValue
- PenaltyAmount
- ConfidenceValue
- ScoreWeight
- ScoreRange
Value objects should enforce their own invariants.

For example:
- A score value must be between 0 and 100.
- A confidence value must be between 0 and 1.
- A penalty amount must be non-negative.
- A weight must be non-negative.
21.2 Immutability
Objects representing historical scoring results must be immutable.

Mutable configuration editing must be isolated from active configuration snapshots and scoring runs.

22. Service architecture
Recommended application/domain services:
Service	Responsibility
ScoringOrchestrator	Coordinates the scoring pipeline
AssessmentSnapshotService	Retrieves immutable assessment snapshots
ScoringConfigurationService	Resolves effective configuration
ConfigurationValidationService	Validates configuration
NormalizationService	Normalizes input lists
SignalDerivationService	Derives deterministic signals
DimensionScoringService	Calculates raw dimension scores
CoverageValidationService	Validates dimension coverage
ConditionEvaluator	Evaluates condition trees
ImbalanceDetectionService	Detects dimension/signal gaps
PatternDetectionService	Evaluates patterns
PatternConfidenceService	Calculates evidence confidence
PenaltyEligibilityService	Determines penalty eligibility
PenaltyEvaluationService	Calculates candidate penalties
PenaltyAggregationService	Applies stacking and suppression
OverallScoreService	Calculates LIS
InterpretationService	Resolves score labels
ExplainabilityService	Builds explanation from trace
ScoringPersistenceService	Persists immutable runs

22.1 Responsibilities
Each service must have one clearly defined responsibility.

The orchestration service coordinates services; it must not contain the business rules implemented by those services.

The domain engine must not:
- Start transactions.
- Call repositories directly.
- Perform HTTP calls.
- Read files.
- Load configuration from the environment.
- Access tenant context through framework-specific static state.
22.2 Strategy interfaces
Normalization:
public interface NormalizationStrategy {

    QuestionType supportedType();

    NormalizationResult normalize(
        ScoringInput input,
        QuestionDefinition question,
        NormalizationConfig config
    );
}


Aggregation:
public interface AggregationStrategy {

    AggregationResult aggregate(
        List<WeightedContribution> contributions,
        AggregationConfiguration configuration
    );
}


Penalty:
public interface PenaltyStrategy {

    PenaltyStrategyType supportedType();

    PenaltyCalculation calculate(
        PenaltyContext context,
        PenaltyConfiguration configuration
    );
}


All strategies must be deterministic and independently testable.

23. Configuration architecture
Configuration must be externalized, versioned, validated, and immutable when active.
23.1 Configuration domains
scoring-config/
    normalization/
    signals/
    dimensions/
    question-mappings/
    aggregation/
    coverage/
    patterns/
    imbalances/
    penalties/
    interpretation/
    explainability/


Configuration may initially be stored in PostgreSQL JSONB.
23.2 Configuration precedence
The canonical precedence is:
Platform defaults
        |
        v
Assessment template/version
        |
        v
Organization override
        |
        v
Tenant override


The precedence is applied from lowest to highest priority.

A tenant override has the highest priority.

This precedence supersedes the older v1 order.
23.3 Merge semantics
Configuration resolution must define field-level behavior.

Rules:
- Scalar fields: higher-priority value replaces lower-priority value.
- Object fields: recursively merge unless the schema marks the object as atomic.
- Arrays: use explicit array semantics; never assume generic merge behavior.
- Rules and catalogs: merge by stable ID only when the configuration schema explicitly permits it.
- Deletions: use explicit tombstones or disable markers.
- Unknown fields: reject unless the schema explicitly allows extension fields.
Replacement (amendment D47): an object {"$replace": X} in a layer replaces the value of that key in every lower layer with X instead of merging with it. It is the counterpart of the deletion tombstone and is the only way a layer replaces an object section; arrays are already replaced as a whole.
The effective result must be materialized as a complete immutable configuration snapshot.
23.4 Configuration validation
Validation must include:
- Schema validation.
- Referential integrity.
- Valid dimension IDs.
- Valid question IDs.
- Valid signal IDs.
- Valid question types.
- Valid normalization ranges.
- Valid weights.
- Valid penalty strategies.
- Valid penalty targets.
- Valid stacking policies.
- Valid threshold boundaries.
- Valid condition expression trees.
- Valid confidence requirements.
- Valid interpretation ranges.
- Valid lifecycle transitions.
- No ambiguous or unsupported references.
A configuration that fails validation MUST NOT be activated.
23.5 Configuration lifecycle
DRAFT
  |
  v
VALIDATING
  |
  v
ACTIVE
  |
  v
PAUSED
  |
  v
ARCHIVED


Lifecycle requirements:
- DRAFT: editable.
- VALIDATING: validation and preview process.
- ACTIVE: available for new scoring runs; immutable.
- PAUSED: not available for new scoring runs.
- ARCHIVED: retained for historical reference.
Editing an active configuration creates a new version.

Active configuration versions MUST NOT be mutated.
23.6 Activation
Activation requires:
- Schema validation.
- Reference validation.
- Threshold validation.
- Conflict/overlap checks.
- Representative test fixtures.
- Preview results.
- Approval metadata.
- Effective date.
- Version identifier.
- Configuration hash.
A failed activation must retain validation errors.

24. Configuration caching
Scoring configuration is read-heavy and may be cached.

Requirements:
- Cache key includes tenant and effective configuration identity.
- Cache must not share tenant-specific configuration between tenants.
- Active configuration snapshots are immutable.
- Cache invalidation occurs when configuration activation, pause, or effective-date changes require it.
- Cache entries must include configuration version and hash.
- A scoring run must use one resolved snapshot, even if configuration changes during execution.
The initial implementation may use Spring Cache with Caffeine.

Redis is a future option for distributed caching.

Caching must not affect determinism.

25. Multi-tenancy and authorization
25.1 Tenant context
Tenant identity must come from the authenticated security context.

The engine must not trust arbitrary tenant IDs supplied by the request body.
25.2 Tenant isolation
Tenant isolation applies to:
- Assessments.
- Assessment snapshots.
- Answers.
- Configurations.
- Configuration versions.
- Scoring runs.
- Dimension results.
- Patterns.
- Penalties.
- Imbalances.
- Explanations.
- Benchmarking cohorts.
- Cache entries.
25.3 Repository isolation
All tenant-owned repository operations must include tenant scope.

A repository query must not retrieve data across tenants unless the operation is an explicitly authorized platform-level operation.
25.4 Defense in depth
PostgreSQL Row-Level Security may be implemented as an additional protection layer.

RLS does not replace application-level tenant authorization.
25.5 Authorization
The application must enforce roles and permissions for:
- Scoring execution.
- Scoring retrieval.
- Explanation retrieval.
- Configuration creation.
- Configuration validation.
- Configuration activation.
- Configuration pause/archive.
- Benchmark access.
The exact role matrix belongs to the platform security specification, but the scoring module must expose authorization-ready application boundaries.
25.6 Tenant isolation tests
Tests must verify that a tenant cannot:
- Score another tenant's assessment.
- Retrieve another tenant's result.
- Access another tenant's configuration.
- Read another tenant's explanation.
- Use another tenant's cache entry.

26. Persistence model
The persistence model must preserve both configuration definitions and immutable scoring outcomes.
26.1 Core tables
Recommended tables:
tenant
organization
assessment
assessment_snapshot
assessment_answer
assessment_template
assessment_template_version


26.2 Configuration tables
scoring_configuration
scoring_configuration_version
scoring_configuration_activation
scoring_configuration_audit


Configuration JSONB may contain versioned configuration payloads.

Stable identity, tenant, version, status, timestamps, and lifecycle relationships should remain relational columns.
26.3 Scoring tables
scoring_run
scoring_dimension_result
scoring_contribution
scoring_signal_result
scoring_explanation


26.4 Pattern tables
scoring_pattern_definition
scoring_pattern_rule_version
scoring_pattern_evaluation
scoring_pattern_evidence


26.5 Imbalance tables
scoring_imbalance_definition
scoring_imbalance_result


26.6 Penalty tables
scoring_penalty_definition
scoring_penalty_rule_version
scoring_penalty_evaluation
scoring_penalty_application
scoring_penalty_suppression
scoring_penalty_aggregation


26.7 Scoring run persistence
Every scoring run must preserve:
- Scoring run ID.
- Tenant ID.
- Organization ID.
- Assessment ID.
- Assessment snapshot ID.
- Engine version.
- Algorithm version.
- Configuration version.
- Configuration snapshot or immutable reference.
- Configuration hash.
- Input hash.
- Execution timestamp.
- Completion timestamp.
- Status.
- Score status.
- Result.
- Trace.
- Error information, if failed.
26.8 Persistence principles
- Historical scoring runs must be immutable.
- Configuration versions must be immutable after activation.
- Foreign keys must preserve referential integrity.
- Tenant-owned records must include tenant scope.
- JSONB must not replace relational identifiers or authorization fields.
- Duplicate representations of the same scoring fact must be avoided.
- Indexes must support tenant-scoped retrieval.
- Database migrations must use Flyway.
26.9 Transaction boundaries
A scoring use case should execute within an application-managed transaction where practical.

The implementation must define how it handles:
- Scoring failures.
- Partial writes.
- Retriable infrastructure errors.
- Idempotency conflicts.
- Concurrent recalculation.
- Duplicate requests.
A failed scoring run must not be exposed as a completed valid result.

27. Idempotency and reproducibility
27.1 Determinism
The same immutable inputs and configuration must produce the same canonical result.

Canonical output excludes explicitly noncanonical metadata such as:
- Generated database IDs.
- Execution timestamps.
- Request correlation IDs.
- Operational telemetry.
27.2 Input hash
The input hash must be based on canonical serialization of:
- Assessment snapshot identity.
- Answer values.
- Relevant assessment context.
- Relevant derived inputs.
27.3 Configuration hash
The configuration hash must be based on canonical serialization of the complete effective configuration snapshot.
27.4 Idempotency identity
The scoring identity is:
tenantId
+ assessmentSnapshotId
+ inputHash
+ configurationHash
+ engineVersion
+ algorithmVersion


27.5 Recalculation
If an assessment is rescored with:
- A changed assessment snapshot.
- A different effective configuration.
- A different engine version.
- A different algorithm version.
A new immutable scoring run must be created.

The previous run must never be overwritten.
27.6 Historical reproduction
Historical runs must be reproducible after:
- Configuration changes.
- Pattern catalog changes.
- Penalty changes.
- Interpretation changes.
- Application deployment changes.
This requires preserving the immutable assessment snapshot, effective configuration snapshot, engine/algorithm versions, and relevant evaluation trace.

If the historical algorithm implementation is no longer executable, the persisted trace and canonical result must still support historical audit and explanation.

28. REST API
Base path:
/api/v1


All endpoints must enforce authentication, tenant scope, and authorization.
28.1 Score assessment
POST /api/v1/assessments/{assessmentId}/score


Purpose:
- Score a completed assessment.
- Resolve its immutable snapshot.
- Resolve effective configuration.
- Execute the scoring pipeline.
- Persist a new scoring run or return an existing idempotent result.
Example response:
200 OK


Returns ScoringResult.
28.2 Retrieve latest score
GET /api/v1/assessments/{assessmentId}/score


Returns the latest authorized scoring result.

If no scoring run exists, return a documented not-found response.
28.3 Retrieve scoring run
GET /api/v1/scoring-runs/{scoringRunId}


Returns the immutable scoring result for that run.
28.4 Retrieve explanation
GET /api/v1/scoring-runs/{scoringRunId}/explanation


Returns the persisted explanation trace.

This endpoint MUST NOT recalculate scores.
28.5 Recalculate
POST /api/v1/assessments/{assessmentId}/score/recalculate


Creates a new scoring run using the effective configuration and assessment snapshot resolved for that request.

The request must support explicit selection of a historical or specified configuration only when the caller has the necessary permission and the configuration is eligible for the requested operation.
28.6 Retrieve dimensions
GET /api/v1/scoring-runs/{scoringRunId}/dimensions


Returns raw and adjusted dimension scores, coverage, contributions, and statuses.
28.7 Retrieve patterns
GET /api/v1/scoring-runs/{scoringRunId}/patterns


Returns pattern evaluations and evidence permitted to the caller.
28.8 Retrieve penalties
GET /api/v1/scoring-runs/{scoringRunId}/penalties


Returns candidate penalties, applied deductions, suppressed deductions, and eligibility reasons.
28.9 Retrieve imbalances
GET /api/v1/scoring-runs/{scoringRunId}/imbalances


Returns configured imbalance findings.
28.10 API error format
{
  "code": "QUESTION_NOT_CONFIGURED",
  "message": "Question Q15 has no scoring configuration.",
  "details": {
    "questionId": "Q15"
  },
  "correlationId": "request-123"
}


28.11 Error codes
Initial error catalog:
ASSESSMENT_NOT_FOUND
ASSESSMENT_NOT_COMPLETED
ASSESSMENT_SNAPSHOT_NOT_FOUND
ASSESSMENT_SNAPSHOT_INVALID
TENANT_CONTEXT_MISSING
TENANT_ACCESS_DENIED
ORGANIZATION_ACCESS_DENIED
QUESTION_NOT_CONFIGURED
QUESTION_NOT_FOUND
DUPLICATE_QUESTION_INPUT
UNSUPPORTED_QUESTION_TYPE
INVALID_ANSWER_TYPE
INVALID_SCALE_VALUE
INVALID_NUMBER_VALUE
INVALID_MULTIPLE_CHOICE_VALUE
MISSING_REQUIRED_INPUT
INSUFFICIENT_DATA
INVALID_NORMALIZATION_CONFIGURATION
INVALID_SIGNAL_CONFIGURATION
INVALID_WEIGHT_CONFIGURATION
INVALID_COVERAGE_CONFIGURATION
INVALID_IMBALANCE_CONFIGURATION
INVALID_PATTERN_CONFIGURATION
INVALID_PENALTY_CONFIGURATION
INVALID_CONDITION_EXPRESSION
INVALID_CONFIGURATION_VERSION
CONFIGURATION_NOT_ACTIVE
CONFIGURATION_VERSION_NOT_FOUND
CONFIGURATION_SNAPSHOT_INVALID
CONFIGURATION_ACTIVATION_FAILED
IDEMPOTENCY_CONFLICT
SCORING_RUN_NOT_FOUND
SCORING_EXECUTION_FAILED
PERSISTENCE_FAILURE


API error codes must be stable and documented.

29. Error handling and failure behavior
29.1 Domain errors
Domain errors must be explicit and independent of HTTP status codes.

Examples:
- Invalid normalization range.
- Unsupported question type.
- Missing required answer.
- Unknown signal.
- Invalid pattern condition.
- Invalid penalty configuration.
- Insufficient coverage.
- Invalid overall weights.
29.2 Application errors
Application services translate infrastructure and domain failures into application-level outcomes.
29.3 API errors
The API layer maps application errors to HTTP status codes.

Examples:
Condition	HTTP status
Invalid request	400
Unauthorized	401
Forbidden	403
Assessment/configuration not found	404
Idempotency conflict	409
Validation failure	422
Unexpected execution failure	500

The exact mapping must be consistent and documented in OpenAPI.
29.4 Failure behavior
- Invalid configuration must fail before scoring.
- Invalid answers must not be silently ignored unless configured.
- A scoring failure must not be returned as a valid LIS.
- Partial persistence must be controlled by transaction boundaries.
- Retriable failures must not create duplicate scoring runs.
- Internal exception details must not leak through API responses.

30. Validation rules
30.1 Normalization
- Minimum must be less than maximum.
- Values must satisfy configured ranges.
- Unknown multiple-choice options must be rejected or explicitly handled.
- Normalized outputs must be in [0,100].
- Null must follow explicit missing-data policy.
30.2 Dimension scoring
- Contribution weights must be non-negative.
- All referenced dimensions must exist.
- Only valid applicable contributions enter the denominator.
- Zero denominator produces insufficient data.
- Overall weights must sum to 1.0 within configured tolerance.
- Multiple-dimension mappings must be explicit.
30.3 Pattern validation
- All referenced signals and dimensions must exist.
- Operators must match operand types.
- Expression trees must have valid arity.
- Thresholds must be valid.
- NOT must have exactly one child.
- Evidence requirements must be valid.
- Pattern versions must be immutable when active.
30.4 Penalty validation
- Penalties must be non-negative.
- Maximum deductions must be non-negative.
- Strategy configuration must be complete.
- Proportional score base must be explicit.
- Severity multipliers must be valid.
- Threshold schedules must not overlap ambiguously.
- Targets must exist.
- Stacking policy must be supported.
- Cumulative caps must be valid.
- Exclusive groups must be explicitly defined.
30.5 Interpretation
- Ranges must not overlap.
- Range boundaries must be explicit.
- All valid scores must map to a range.
- Interpretation must not modify the score.
30.6 Configuration activation
Invalid configuration must fail fast before activation.

The system must return all useful validation errors rather than silently ignoring invalid sections.

31. Precision and rounding
Use BigDecimal for all scoring calculations.

Do not use binary floating-point arithmetic for canonical scoring.
31.1 Precision requirements
- Normalization must use a configured scale.
- Dimension aggregation must use a documented precision.
- Penalty calculations must use the same numeric policy.
- Intermediate calculations must not be rounded unnecessarily.
- Rounding must occur at defined stages.
- Final dimension and LIS values must follow one explicit policy.
31.2 Rounding policy
The initial implementation should use:
RoundingMode.HALF_UP


The configured decimal scale must be explicit and versioned.

For example:
- Intermediate calculations: retain sufficient precision.
- Persisted dimension score: 2 decimal places.
- Persisted LIS: 2 decimal places.
The precise scale is a product-level scoring decision and must be confirmed before production calibration.
31.3 Clamping
Dimension scores and LIS must be constrained to [0,100].

Penalty deductions must be non-negative.

32. Technology stack and dependencies
Required baseline:
Area	Technology
Language	Java 21
Framework	Spring Boot 3
REST	Spring Web
Persistence	Spring Data JPA
Database	PostgreSQL
Database migration	Flyway
Serialization	Jackson
Validation	Jakarta Validation
Security	Spring Security / JWT
Testing	JUnit 5
Mocking	Mockito
Integration testing	Testcontainers
Observability	Micrometer
API documentation	OpenAPI
Build	Gradle or Maven
Initial cache	Caffeine / Spring Cache
Future distributed cache	Redis

Dependency versions must be pinned through the build configuration and updated through a controlled process.
32.1 Database
PostgreSQL is the initial persistence platform.

Use:
- Relational columns for stable identifiers and relationships.
- JSONB for flexible, versioned configuration and trace payloads where appropriate.
- Foreign keys.
- Unique constraints.
- Tenant-scoped indexes.
- Flyway migrations.

33. Security and privacy
33.1 Authentication
The API must use the platform authentication mechanism.

The scoring engine must not implement a separate authentication system.
33.2 Authorization
Every operation must verify tenant ownership and permission.
33.3 Data protection
- Minimize stored raw assessment data.
- Restrict free-text access.
- Avoid logging raw answers.
- Avoid exposing sensitive evidence in unauthorized explanations.
- Protect scoring results from cross-tenant access.
- Define retention and deletion behavior with the platform data-governance policy.
33.4 Audit
Audit events should record:
- Configuration creation.
- Configuration updates.
- Validation.
- Activation.
- Pause/archive.
- Scoring execution.
- Recalculation.
- Access to restricted explanations where required.
Audit events must not expose unnecessary raw assessment content.
33.5 AI governance
Any future AI/ML functionality must be designed with:
- Provenance.
- Model/version identification.
- Confidence and uncertainty.
- Human review where appropriate.
- Privacy controls.
- Auditability.
- A clear boundary from canonical LIS calculation.

34. Observability
The application must expose operational telemetry without exposing sensitive assessment content.
34.1 Metrics
Recommended metrics:
scoring_requests_total
scoring_success_total
scoring_failures_total
scoring_duration_seconds
normalization_duration_seconds
dimension_scoring_duration_seconds
pattern_evaluation_duration_seconds
penalty_evaluation_duration_seconds
explainability_duration_seconds
configuration_cache_hits_total
configuration_cache_misses_total
insufficient_data_total


34.2 Logging
Structured logs should include:
- Correlation ID.
- Scoring run ID.
- Tenant ID, subject to logging policy.
- Assessment ID.
- Engine version.
- Configuration version.
- Execution status.
- Failure category.
- Duration.
Raw answers and free-text responses must not be logged by default.
34.3 Tracing
Distributed tracing should connect:
- API request.
- Application use case.
- Configuration loading.
- Scoring execution.
- Persistence.
Operational telemetry must not influence scoring output.

35. Performance and scalability
The initial implementation must be stateless at the scoring-engine level.

The engine should support horizontal application scaling.
35.1 Performance requirements
Performance targets must be established through representative load tests.

The implementation must measure:
- Assessment size.
- Number of questions.
- Number of derived signals.
- Number of active patterns.
- Number of penalty rules.
- Configuration size.
- Explanation trace size.
- Database persistence latency.
No production latency target is assumed in this specification without a defined workload.
35.2 Optimization
Permitted optimizations include:
- Immutable configuration caching.
- Reuse of prevalidated condition structures.
- Efficient signal indexing.
- Batch persistence.
- Avoiding repeated configuration resolution.
- Avoiding unnecessary serialization.
Optimizations must preserve deterministic behavior.
35.3 Future asynchronous processing
For large-scale deployments, scoring may be executed asynchronously.

If asynchronous processing is introduced:
- The API must expose execution status.
- Jobs must be idempotent.
- Tenant context must be preserved.
- Retries must not duplicate scoring runs.
- Results must remain immutable.
- Event ordering and failure handling must be documented.

36. Benchmarking and analytics
Benchmarking and analytics are downstream consumers of canonical scoring results.

They MUST NOT modify the canonical LIS.
36.1 Benchmarking responsibilities
Benchmarking may compare:
- Organizations.
- Cohorts.
- Industry segments.
- Organization size ranges.
- Assessment periods.
- Dimension distributions.
- Pattern prevalence.
- Delivery and leadership maturity indicators.
36.2 Cohort eligibility
Benchmarking must define:
- Cohort membership criteria.
- Minimum cohort size.
- Data freshness.
- Inclusion/exclusion criteria.
- Privacy controls.
- Aggregation methodology.
- Benchmark methodology version.
- Calculation timestamp.
A benchmark must not expose identifiable tenant or organization information without explicit authorization.
36.3 Historical analysis
Historical analytics may compare immutable scoring runs over time.

Comparisons must account for:
- Assessment template version.
- Configuration version.
- Engine version.
- Scoring methodology changes.
- Organizational context changes.
A change in LIS must not automatically be interpreted as organizational improvement or deterioration if the scoring methodology changed.
36.4 AI/ML integration
AI/ML may consume:
- Dimension scores.
- Pattern results.
- Imbalances.
- Scoring trends.
- Benchmarking data.
- Explanation traces.
AI/ML must not silently rewrite:
- Normalized values.
- Dimension scores.
- Penalties.
- LIS.
- Historical results.
Any AI-generated interpretation must be identified as such and remain separate from deterministic scoring.

37. Package structure
Recommended Java package structure:
com.company.leadershipintelligence
│
├── api
│   ├── controller
│   ├── request
│   ├── response
│   ├── mapper
│   └── exception
│
├── application
│   ├── service
│   ├── command
│   ├── query
│   └── mapper
│
├── domain
│   ├── model
│   ├── valueobject
│   ├── enums
│   ├── service
│   ├── strategy
│   ├── rule
│   └── exception
│
├── ports
│   ├── in
│   └── out
│
├── infrastructure
│   ├── persistence
│   │   ├── entity
│   │   ├── repository
│   │   └── mapper
│   ├── configuration
│   ├── cache
│   ├── messaging
│   └── security
│
└── config
    ├── beans
    ├── properties
    └── security


37.1 Package rules
- Domain classes must not import infrastructure classes.
- Controllers must not execute scoring logic.
- Application services coordinate domain operations.
- Repository interfaces belong to the appropriate port boundary.
- Infrastructure implements persistence and external dependencies.
- DTOs must not be used as domain objects.
- JPA entities must not be exposed directly through APIs.

38. Testing strategy
Testing must cover domain correctness, configuration validation, reproducibility, tenant isolation, and persistence.
38.1 Unit testing
Every domain service and strategy must have isolated unit tests.
NormalizationService
Test:
- Positive SCALE.
- Negative SCALE.
- Minimum/maximum boundaries.
- Decimal values.
- MULTIPLE_CHOICE.
- BOOLEAN.
- NUMBER.
- TEXT flags.
- Mixed question types in one list.
- Different normalization strategies in one list.
- Missing configuration.
- Unsupported question type.
- Invalid values.
- Null answers.
- Out-of-range policies.
- Missing-data policies.
- Deterministic text matching.
- Provenance preservation.
SignalDerivationService
Test:
- Valid signal derivation.
- Multiple source inputs.
- Missing source signals.
- Invalid source types.
- Correlation group preservation.
- Deterministic output.
- Versioned derivation behavior.
DimensionScoringService
Test:
- Single contribution.
- Multiple weighted contributions.
- Multiple dimensions.
- Zero weights.
- Mixed answer types.
- Score boundaries.
- Empty input.
- Zero denominator.
- Insufficient coverage.
- Decimal precision.
- Correlated signals.
- Explicit multi-dimension contributions.
CoverageValidationService
Test:
- Complete coverage.
- Minimum threshold boundary.
- Insufficient coverage.
- Missing required dimension.
- Excluded questions.
- Invalid contribution.
- Partial scoring policy.
- No silent zero imputation.
ImbalanceDetectionService
Test:
- No imbalance.
- Exact threshold.
- Threshold exceeded.
- Multiple dimension gaps.
- Multiple rules.
- Missing dimension values.
- Gap direction.
- Raw versus explicitly configured evaluation basis.
- No score mutation.
ConditionEvaluator
Test:
- EQ, NE, GT, GTE, LT, LTE.
- BETWEEN.
- IN and NOT_IN.
- EXISTS and NOT_EXISTS.
- CONTAINS.
- GAP_GT and GAP_GTE.
- AND, OR, NOT.
- Nested expressions.
- Invalid arity.
- Unknown signals.
- Missing values.
- Type mismatch.
- Invalid operands.
- Deterministic evaluation.
PatternDetectionService
Test:
- Pattern not matched.
- Single-condition match.
- Multi-condition match.
- Threshold boundaries.
- Multiple patterns.
- Evidence collection.
- Confidence calculation.
- Insufficient evidence.
- Confidence below threshold.
- Severity independent of confidence.
- Inactive versions.
- Overlapping patterns.
- No score mutation.
PenaltyEvaluationService
Test:
- FIXED.
- PROPORTIONAL.
- SEVERITY_BASED.
- THRESHOLD_BASED.
- CONDITIONAL.
- Exact threshold boundaries.
- Severity multipliers.
- Missing score base.
- Evidence ineligibility.
- Confidence ineligibility.
- Inactive penalty.
- No trigger.
- Individual caps.
- Deterministic rounding.
PenaltyAggregationService
Test all six stacking policies:
- STACK.
- MAX.
- MAX_PER_CATEGORY.
- PRIORITY.
- CAPPED_STACK.
- EXCLUSIVE_GROUP.
Also test:
- Multiple categories.
- Exclusive groups.
- Priority ties.
- Stable tie-break.
- Cumulative caps.
- Overlap.
- Suppression trace.
- Dimension versus overall targets.
- No double application.
OverallScoreService
Test:
- Equal weights.
- Custom weights.
- Invalid weights.
- Dimension penalties.
- Overall penalties.
- Score boundaries.
- Insufficient dimension.
- Partial policy.
- Deterministic output.
- Penalties applied exactly once.
InterpretationService
Test:
- Every interpretation range.
- Exact boundaries.
- Overlapping ranges rejected.
- Missing ranges rejected.
- Insufficient data.
- Configuration version.
ExplainabilityService
Test:
- Contribution explanation.
- Penalty explanation.
- Suppression explanation.
- Pattern explanation.
- Evidence references.
- Imbalance explanation.
- Complete trace.
- Historical trace retrieval.
- No score recalculation.
ConfigurationValidationService
Test:
- Valid configuration.
- Invalid normalization.
- Invalid mappings.
- Invalid weights.
- Invalid penalties.
- Invalid pattern conditions.
- Unknown references.
- Invalid lifecycle transition.
- Invalid merge semantics.
- Active version immutability.
Tenant isolation
Test:
- Cross-tenant assessment access.
- Cross-tenant configuration access.
- Cross-tenant scoring-run retrieval.
- Cross-tenant explanation retrieval.
- Cache isolation.
- Tenant context missing.
- Tenant mismatch.
38.2 Integration testing
Use PostgreSQL Testcontainers.

Verify:
1. Configuration loading.
2. JSONB configuration deserialization.
3. Configuration precedence.
4. Configuration merge semantics.
5. Tenant isolation.
6. Scoring execution.
7. Dimension persistence.
8. Pattern persistence.
9. Penalty persistence.
10. Imbalance persistence.
11. Explanation persistence.
12. Historical retrieval.
13. Configuration versioning.
14. Configuration immutability.
15. Recalculation.
16. Idempotency.
17. Transaction behavior.
18. Failure recovery.
19. Concurrent scoring requests.
20. Flyway migration correctness.
38.3 End-to-end testing
A complete scenario must verify:
- Assessment snapshot retrieval.
- Effective configuration resolution.
- Normalization.
- Signal derivation.
- Coverage.
- Dimension scoring.
- Imbalance detection.
- Pattern detection.
- Evidence and confidence.
- Penalty eligibility.
- Penalty calculation.
- Penalty aggregation.
- Adjusted dimensions.
- LIS.
- Interpretation.
- Explainability.
- Persistence.
- API retrieval.

39. Global invariants
The following invariants apply to every scoring run.
1. All normalized values are in [0,100].
2. All valid dimension scores are in [0,100].
3. LIS is in [0,100].
4. Penalty deductions are non-negative.
5. A penalty cannot increase a score.
6. An adjusted dimension score cannot exceed its raw score.
7. Pattern detection alone never changes a score.
8. Imbalance detection never changes a score.
9. Missing evidence is never silently interpreted as zero.
10. Missing answers follow an explicit configured policy.
11. Every penalty candidate has exactly one terminal status.
12. Every suppression has an explanation.
13. Dimension penalties are applied once.
14. Overall penalties are applied once.
15. Overall weights satisfy the configured sum constraint.
16. Configuration snapshots are immutable during scoring.
17. Active configuration versions are immutable.
18. Tenant data cannot leak across tenants.
19. The same canonical inputs and versions produce the same canonical result.
20. Historical scoring results remain reproducible after configuration changes.
21. Explanation retrieval does not recalculate scores.
22. Benchmarking does not modify canonical scores.
23. AI/ML outputs do not silently modify canonical scores.
24. An insufficient dimension is not silently converted to a numeric score.
25. A partial LIS is clearly identified as partial.

40. Definition of Done
The initial scoring engine is considered complete only when all mandatory requirements below are satisfied.
40.1 Domain and scoring
- [ ] Four canonical dimensions implemented.
- [ ] Universal directionality enforced.
- [ ] All five normalization strategies implemented.
- [ ] List-based normalization supported.
- [ ] Question configuration resolved internally.
- [ ] Deterministic signal derivation implemented.
- [ ] Dimension aggregation implemented.
- [ ] Coverage calculation implemented.
- [ ] Insufficient-data behavior implemented.
- [ ] Imbalance detection implemented.
- [ ] Pattern evaluation implemented.
- [ ] Evidence and confidence implemented.
- [ ] All penalty strategies implemented.
- [ ] All stacking policies implemented.
- [ ] Penalty suppression trace implemented.
- [ ] LIS aggregation implemented.
- [ ] Interpretation implemented.
- [ ] Explainability implemented.
40.2 Configuration
- [ ] Configuration stored externally.
- [ ] Configuration versioning implemented.
- [ ] Configuration precedence implemented.
- [ ] Merge semantics implemented.
- [ ] Configuration validation implemented.
- [ ] Lifecycle implemented.
- [ ] Active configuration immutability enforced.
- [ ] Configuration snapshots and hashes persisted.
- [ ] Configuration caching tenant-safe.
40.3 Persistence and reproducibility
- [ ] Assessment snapshots supported.
- [ ] Scoring runs persisted.
- [ ] Dimension results persisted.
- [ ] Contributions persisted.
- [ ] Patterns and evidence persisted.
- [ ] Imbalances persisted.
- [ ] Penalty evaluations persisted.
- [ ] Penalty suppressions persisted.
- [ ] Explainability trace persisted.
- [ ] Idempotency implemented.
- [ ] Historical recalculation creates new runs.
- [ ] Historical runs remain retrievable.
40.4 API and security
- [ ] Score endpoint implemented.
- [ ] Latest-score endpoint implemented.
- [ ] Scoring-run endpoint implemented.
- [ ] Explanation endpoint implemented.
- [ ] Recalculation endpoint implemented.
- [ ] Dimension endpoint implemented.
- [ ] Pattern endpoint implemented.
- [ ] Penalty endpoint implemented.
- [ ] Imbalance endpoint implemented.
- [ ] Tenant context enforced.
- [ ] Role-based authorization enforced.
- [ ] Stable API errors implemented.
- [ ] OpenAPI documentation published.
40.5 Quality and operations
- [ ] Unit tests pass.
- [ ] Integration tests pass with PostgreSQL Testcontainers.
- [ ] End-to-end scoring tests pass.
- [ ] Tenant isolation tests pass.
- [ ] Determinism tests pass.
- [ ] Reproducibility tests pass.
- [ ] Flyway migrations are validated.
- [ ] Structured logging implemented.
- [ ] Micrometer metrics implemented.
- [ ] Sensitive data is not logged.
- [ ] Performance baseline measured.
- [ ] Security review completed.

41. Architectural principles and implementation constraints
The following principles are mandatory.
41.1 Separation of concerns
Normalization, dimension scoring, pattern detection, imbalance detection, penalty evaluation, penalty aggregation, LIS calculation, interpretation, and explainability must remain separate responsibilities.
41.2 Configuration over code
Scoring thresholds, weights, mappings, patterns, penalties, and interpretation ranges must be configurable.

Algorithm implementations remain code, but business parameters must be configuration-driven.
41.3 Determinism over inference
Canonical scoring must not depend on nondeterministic external inference.
41.4 Explainability over opaque scoring
Every material scoring result must be traceable to its sources and calculations.
41.5 Immutability over historical mutation
Historical assessment snapshots, scoring runs, and active configuration versions must remain immutable.
41.6 Evidence over assumptions
The engine must not infer evidence that is absent from the assessment or configured signal sources.
41.7 Diagnostic separation
Patterns and imbalances are diagnostic outputs. Penalties are scoring consequences. The system must preserve this distinction.
41.8 Privacy by design
Tenant isolation, evidence access, free-text handling, and explanation visibility must be enforced throughout the architecture.

42. Calibration, governance, and release readiness
The scoring model is a configurable measurement system. Its numerical thresholds and penalty values must be treated as hypotheses until validated.
42.1 Calibration requirements
Before enabling scoring rules in production, the team must define:
- Representative assessment datasets.
- Expected score distributions.
- Threshold calibration methodology.
- Pattern precision/false-positive review.
- Evidence reliability criteria.
- Penalty impact analysis.
- Sensitivity analysis.
- Regression test datasets.
- Versioned approval records.
The suggested pattern and penalty catalogs in this specification are a starting taxonomy, not validated empirical claims.
42.2 Calibration artifacts
Each production scoring configuration should retain:
- Calibration dataset reference.
- Calibration methodology version.
- Calibration date.
- Reviewer/approval metadata.
- Validation results.
- Known limitations.
- Configuration version.
- Effective date.
42.3 Release gates
A scoring configuration should not become active unless:
1. Structural validation succeeds.
2. All referenced signals exist.
3. All referenced questions and dimensions exist.
4. Thresholds are unambiguous.
5. Penalty interactions are reviewed.
6. Representative test cases pass.
7. Score distribution preview is reviewed.
8. Configuration version and hash are recorded.
9. Activation is audited.

43. Reference frameworks and measurement boundaries
The engine may use established engineering and organizational measurement frameworks as reference material when designing signals and interpretation.

These frameworks are not interchangeable with LIS and must not be presented as direct validation of the LIS model.

Relevant references include:
- DORA Metrics — software delivery performance measurement.
- DORA Research — research on software delivery and organizational performance.
- Google SRE: Service Level Objectives — reliability objectives and measurement.
- Google SRE: Error Budget Policy — operational reliability and delivery trade-offs.
- SPACE Framework — multidimensional developer productivity measurement.
- NIST AI Risk Management Framework — governance of future AI-assisted capabilities.
- NIST AI RMF Playbook — practical AI risk management guidance.
The implementation must distinguish:
- Directly measured assessment signals.
- Derived scoring signals.
- Configured organizational patterns.
- External benchmark data.
- AI-generated interpretations.
These categories must not be blended into an unexplained numeric score.

44. Decisions and items requiring explicit product approval
This specification closes the architectural and behavioral gaps between v1 and v2. It does not claim that every product calibration choice has been empirically established.

The following decisions must be explicitly approved before production scoring is enabled.
Decision	Current specification	Required action
Canonical dimensions	Four defined dimensions	Confirm product definitions
Directionality	100 always desirable	No ambiguity permitted
Overall weights	Configurable; illustrative equal weights	Approve initial weights
Dimension aggregation	Weighted average	Approve initial algorithm
Minimum coverage	Configurable	Define thresholds per dimension
Missing answers	Explicit policy; no zero imputation	Define policy per question
Partial LIS	Disabled by default	Approve only if needed
Normalization precision	BigDecimal; explicit policy	Confirm decimal scale
Final rounding	HALF_UP proposed	Approve scoring precision
Interpretation ranges	Illustrative	Calibrate using representative data
Pattern thresholds	Configurable, uncalibrated	Validate before activation
Confidence bands	Provisional	Calibrate or retain as labels only
Penalty maximums	Suggested catalog values	Validate impact and approve
Penalty stacking	Explicit policy; recommended defaults	Approve category/group rules
Configuration precedence	Platform → Template → Organization → Tenant	Adopt as canonical
Historical scoring	Immutable runs and snapshots	Implement retention policy
Benchmarking	Downstream, privacy-controlled	Define cohort methodology
AI/ML	Cannot change canonical LIS	Govern separately
Performance targets	Not yet specified	Define workload and SLOs
Retention/deletion	Must follow platform policy	Define platform-level policy

These are not reasons to leave the engine architecture incomplete. They are configuration, governance, or operational decisions that must be resolved before the corresponding production capability is enabled.