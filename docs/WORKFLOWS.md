# Workflows

Every workflow the product offers today, drawn from the code (controllers, services, lifecycle enums, security rules and
frontend routes) as of 2026-11-04. The diagrams are [Mermaid](https://mermaid.js.org/); GitHub renders them in the browser.

Where a diagram and a specification disagree, the specification wins (`specs/specv3.md` > `specs/architect.md` >
`specs/frontend-spec.md`). Endpoint details are in `docs/implementation/API_CONTRACT.md`.

| # | Workflow | Who |
|---|----------|-----|
| 1 | [Actors and what each may reach](#1-actors-and-what-each-may-reach) | everyone |
| 2 | [Sign-in and landing page](#2-sign-in-and-landing-page) | everyone |
| 3 | [Platform administration: create a tenant](#3-platform-administration-create-a-tenant) | platform administrator |
| 4 | [Scoring configuration: layers and precedence](#4-scoring-configuration-layers-and-precedence) | system |
| 5 | [Configuration version lifecycle](#5-configuration-version-lifecycle) | owner, administrator, analyst (read) |
| 6 | [Configuration change: draft to active](#6-configuration-change-draft-to-active) | owner, administrator |
| 7 | [Questionnaire builder](#7-questionnaire-builder) | owner, administrator |
| 8 | [Roster: teams and people](#8-roster-teams-and-people) | owner, administrator |
| 9 | [Assessment run, end to end](#9-assessment-run-end-to-end) | managers and respondents |
| 10 | [Respondent flow](#10-respondent-flow) | respondent |
| 11 | [Scoring through the API](#11-scoring-through-the-api) | owner, administrator, engineering leader |
| 12 | [The scoring pipeline](#12-the-scoring-pipeline) | system |
| 13 | [Reading results](#13-reading-results) | every tenant role |
| 14 | [Reports](#14-reports) | every tenant role |
| 15 | [Audit and observability](#15-audit-and-observability) | system |
| 16 | [Role and endpoint matrix](#16-role-and-endpoint-matrix) | reference |

---

## 1. Actors and what each may reach

Five tenant roles come from the `roles` claim of the token; the platform administrator and the respondent are deliberately
not in the `Role` enum. The tenant and the organizations always come from the token, never from a request.

```mermaid
flowchart LR
    subgraph Actors
        PA([Platform administrator])
        OA([Owner / Administrator])
        EL([Engineering leader])
        AN([Analyst])
        VW([Viewer])
        RS([Respondent])
    end

    subgraph Areas
        T[Tenants<br/>/platform/**]
        PC[Platform default configuration<br/>/platform/configuration]
        Q[Questionnaires, People, Runs<br/>manage assessments]
        CFG[Configuration<br/>edit, approve]
        CFGR[Configuration<br/>read only]
        SC[Scoring writes<br/>calculate, score, recalculate]
        RD[Dashboard, Compare, Explanation,<br/>Reports]
        ANS[Source answers<br/>audited]
        MET[Metrics<br/>/actuator/prometheus]
        RP[My assessments<br/>/respond/**]
    end

    PA --> T
    PA --> PC
    OA --> Q
    OA --> CFG
    OA --> SC
    OA --> RD
    OA --> ANS
    OA --> MET
    EL --> SC
    EL --> RD
    AN --> CFGR
    AN --> RD
    AN --> ANS
    VW --> RD
    RS --> RP
```

Notes:

- The platform administrator belongs to no tenant and reaches nothing tenant-scoped (a tenant-scoped read returns 403).
- Viewers read results with values withheld ("redacted"); owners, administrators and analysts see the full values.
- Respondents reach only `/api/v1/respond/**`.

---

## 2. Sign-in and landing page

The web UI signs in with the OpenID Connect authorization-code flow (with PKCE). Locally the identity provider is the mock
server in `docker-compose.yml`; the login page offers one button per sample persona. Tokens are kept in `sessionStorage`
(cleared with the tab). The backend is a resource server: it validates the signature against the provider's key set and
fails closed when no key set is configured.

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant W as Web UI
    participant I as Identity provider
    participant A as API

    U->>W: open any page
    W->>W: no session, remember the page to return to
    W->>U: show /login
    U->>W: choose a persona
    W->>I: authorization request (code and PKCE challenge)
    I->>U: sign-in
    U->>I: credentials
    I->>W: /auth/callback with a code
    W->>I: exchange the code for tokens
    I-->>W: access token (tenant_id, org_ids, roles, email)
    W->>W: build the principal, go back to the remembered page
    W->>A: API calls with Authorization: Bearer
    A->>A: validate signature and expiry, map roles to authorities
    A-->>W: data, or 401 / 403
```

Where each principal lands (`frontend/src/app/layouts/nav.ts`):

```mermaid
flowchart TD
    P{{Principal}} -->|platform administrator| N1[Tenants<br/>Configuration]
    P -->|respondent| N2[My assessments]
    P -->|tenant role| N3[Dashboard<br/>Compare<br/>Reports]
    N3 -->|owner or administrator| N4[Questionnaires<br/>Assessment runs<br/>People]
    N3 -->|owner, administrator or analyst| N5[Configuration]
```

---

## 3. Platform administration: create a tenant

A tenant is an id (permanent) and a name only. Creating and listing tenants are audited.

```mermaid
sequenceDiagram
    autonumber
    actor P as Platform administrator
    participant W as Web UI
    participant A as API
    participant D as Database

    P->>W: Tenants, New tenant
    W->>W: validate id and name
    W->>A: POST /api/v1/platform/tenants
    A->>A: require PLATFORM_ADMIN
    A->>D: insert tenant
    A->>D: platform audit row
    A-->>W: 201 tenant
    P->>W: Tenants
    W->>A: GET /api/v1/platform/tenants
    A->>D: list, audited
    A-->>W: tenants
```

---

## 4. Scoring configuration: layers and precedence

All scoring rules are versioned configuration, never code. A run resolves one effective configuration, once, in this order
(later layers override earlier ones):

```mermaid
flowchart LR
    S[Startup loader<br/>initial-platform-default.json<br/>mode SYNC] -->|creates a version when the file changes| PL
    PL[Platform default] --> TP[Template layer<br/>one per published questionnaire] --> OR[Organization layer] --> TN[Tenant layer]
    TN --> EF[[Effective configuration<br/>pinned to a run with its version ids and hash]]
```

- The platform default is `src/main/resources/config/initial-platform-default.json`: an initial, **unvalidated** proposal.
  An invalid file stops startup. The `dev` profile loads a separate illustrative sample instead.
- An active version is never edited. A change is a new version.
- Every run stores the engine version, algorithm version, configuration version and hash, input hash and rounding policy
  version, so it can be explained and replayed later.

---

## 5. Configuration version lifecycle

`ConfigurationStatus` (decision D33). Only `DRAFT` is editable and only `ACTIVE` versions score new runs.

```mermaid
stateDiagram-v2
    [*] --> DRAFT: create, or copy of a version
    DRAFT --> VALIDATING: submit for approval
    VALIDATING --> DRAFT: validation fails, or approval rejected
    VALIDATING --> ACTIVE: approved by a second person
    ACTIVE --> PAUSED: pause
    PAUSED --> ACTIVE: resume
    ACTIVE --> ARCHIVED: archive
    PAUSED --> ARCHIVED: archive
    ARCHIVED --> [*]
```

Activating a version supersedes the previously active version of the same layer (one active version per layer).

---

## 6. Configuration change: draft to active

The Configuration screens (decisions D52 to D58). The approver must be a different person from anyone who created, edited or
submitted the version (switchable with `lis.configuration.approval.require-second-person`), approval needs a written note,
and a failing fixture blocks approval.

```mermaid
sequenceDiagram
    autonumber
    actor E as Editor (owner or administrator)
    actor R as Approver (a different person)
    participant W as Web UI
    participant A as API
    participant V as Validator and preview
    participant F as Fixtures

    E->>W: Configuration, choose a layer
    W->>A: GET /configuration/layers
    E->>W: New draft (or copy of the active version)
    W->>A: POST /configuration/versions (or /{id}/copy)
    loop editing
        E->>W: change numbers, texts, rules
        W->>A: PUT /configuration/versions/{id}
        E->>W: Check
        W->>A: POST /{id}/validate
        A->>V: validate every rule and reference
        V-->>W: every problem, or clean
        E->>W: Preview
        W->>A: POST /{id}/preview
        A->>V: score saved sample answers with the draft
        V-->>W: before and after
    end
    E->>W: save or edit fixtures
    W->>A: /layers/{id}/fixtures (GET, POST, PUT, DELETE)
    E->>W: Submit
    W->>A: POST /{id}/submit
    A->>V: validate, move to VALIDATING
    alt validation fails
        A-->>W: back to DRAFT with the problems
    else awaiting approval
        R->>W: open the version
        R->>W: Approve with a note, an effective date, a calibration reference
        W->>A: POST /{id}/approve
        A->>A: refuse if the approver worked on the version
        A->>F: run the layer's fixtures with the draft applied
        alt a fixture fails
            A-->>W: refused, CONFIGURATION_FIXTURES_FAILED
        else all pass
            A->>A: activate, supersede the old version, record the review
            A-->>W: ACTIVE
        end
    else rejected
        R->>W: Reject with a comment
        W->>A: POST /{id}/reject
        A-->>W: back to DRAFT
    end
```

After activation an owner or administrator can pause, resume or archive the version. The platform administrator manages the
platform default layer through `/api/v1/platform/configuration`; tenants manage theirs through `/api/v1/configuration`.
Template layers are generated by the questionnaire builder and are not edited here.

---

## 7. Questionnaire builder

Owners and administrators author questionnaires in the builder. Publishing generates a validated TEMPLATE layer through the
configuration lifecycle in one transaction; the questionnaire id is the template id. Free-text questions are collected and
never scored.

```mermaid
stateDiagram-v2
    [*] --> DRAFT: create, from the initial configuration, or duplicate
    DRAFT --> PUBLISHED: publish (checks pass)
    DRAFT --> DRAFT: save draft (may be incomplete)
    PUBLISHED --> PUBLISHED_WITH_DRAFT: edit (opens the next draft)
    PUBLISHED_WITH_DRAFT --> PUBLISHED_WITH_DRAFT: save draft
    PUBLISHED_WITH_DRAFT --> PUBLISHED: publish (supersedes the old version)
    DRAFT --> ARCHIVED: archive
    PUBLISHED --> ARCHIVED: archive
    PUBLISHED_WITH_DRAFT --> ARCHIVED: archive
    ARCHIVED --> [*]
```

```mermaid
sequenceDiagram
    autonumber
    actor O as Owner or administrator
    participant W as Web UI
    participant Q as QuestionnaireService
    participant C as ConfigurationService
    participant D as Database

    O->>W: Questionnaires, New
    W->>Q: POST /questionnaires (or /from-initial)
    loop build
        O->>W: add questions, scales, dimensions, weights, texts
        W->>Q: PUT /questionnaires/{id}
        Q->>D: save as the open draft, report publish problems
    end
    O->>W: Publish
    W->>Q: POST /questionnaires/{id}/publish
    Q->>Q: check the document is publishable
    Q->>Q: generate the TEMPLATE layer
    Q->>C: create a draft, validate, activate (one transaction)
    C-->>Q: active version id
    Q->>D: mark the questionnaire version PUBLISHED
    Q-->>W: published, version number
```

Archiving hides a questionnaire from new use. Its published layer stays active so past assessments can still be explained and
recalculated.

---

## 8. Roster: teams and people

Teams and people form the roster that runs draw on. People are matched to respondents by their `email` claim. Nobody is ever
listed with scores; members are an input scope only.

```mermaid
flowchart TD
    A[Owner or administrator] --> T[People screen]
    T --> T1[Create a team<br/>POST /teams]
    T --> T2[Rename or deactivate a team<br/>PUT /teams/id]
    T --> P1[Add a person with an email and a team<br/>POST /people]
    T --> P2[Move, edit or deactivate a person<br/>PUT /people/id]
    T1 & T2 & P1 & P2 --> R[(Roster)]
    R -->|active people of the chosen teams| RUN[Start an assessment run]
```

---

## 9. Assessment run, end to end

An assessment run pins a published questionnaire version and its configuration version, and snapshots one participant per
active person of the chosen teams. Closing the run scores the submitted answers once.

```mermaid
stateDiagram-v2
    [*] --> OPEN: start run
    OPEN --> OPEN: respondents save drafts and submit
    OPEN --> CLOSED: close (at least one submission)
    CLOSED --> [*]
```

```mermaid
sequenceDiagram
    autonumber
    actor M as Owner or administrator
    actor P as Respondent
    participant W as Web UI
    participant A as API
    participant S as Scoring
    participant D as Database

    M->>W: Assessment runs, Start
    W->>A: POST /api/v1/runs (name, organization, questionnaire, teams, due date)
    A->>A: checks: published version, teams in the organization, active people, due date not past
    A->>D: store the run, its teams and one participant per person
    A->>D: audit RUN_STARTED
    A-->>W: run OPEN
    Note over P,D: respondents answer, see workflow 10
    P->>A: submit
    A->>D: answers stored with team and question only
    M->>W: Watch the completion list (no answers)
    W->>A: GET /runs/{id}
    M->>W: Close the run
    W->>A: POST /runs/{id}/close
    A->>A: refuse if already closed, or nobody has submitted
    A->>S: score the answers as MEMBER answers with the pinned configuration
    S->>D: one ORGANIZATION run and one TEAM run per team
    A->>D: audit RUN_CLOSED
    A-->>W: run CLOSED with the scoring run id
    M->>W: open Dashboard, Explanation, Reports
```

A scoring failure rolls everything back and leaves the run open.

---

## 10. Respondent flow

Answers are stored with the team and the question only: no person, no time, a random id. The draft is linked to the
participant so it can be resumed, and is deleted on submit.

```mermaid
stateDiagram-v2
    [*] --> NOT_STARTED: participant created at run start
    NOT_STARTED --> IN_PROGRESS: first draft saved
    IN_PROGRESS --> IN_PROGRESS: save draft
    IN_PROGRESS --> SUBMITTED: submit (all required answered)
    NOT_STARTED --> SUBMITTED: submit (all required answered)
    SUBMITTED --> [*]
```

```mermaid
sequenceDiagram
    autonumber
    actor P as Respondent
    participant W as Web UI
    participant A as API
    participant D as Database

    P->>W: sign in (RESPONDENT role, email claim)
    W->>A: GET /respond/runs
    A->>D: match the email to a person, list the participations
    A-->>W: my assessments (name, due date, status)
    P->>W: open one
    W->>A: GET /respond/runs/{id}
    A-->>W: questions and my draft
    loop answering
        P->>W: answer, or clear an answer
        W->>A: PUT /respond/runs/{id}/draft
        A->>A: refuse if the run is closed or I already submitted
        A->>A: check each answer against its question
        A->>D: save into the draft
    end
    P->>W: Submit
    W->>A: POST /respond/runs/{id}/submit
    A->>A: every required question answered?
    alt missing answers
        A-->>W: 400 listing the questions
    else complete
        A->>D: in one transaction: insert answers (team, question), delete the draft, mark SUBMITTED
        A-->>W: submitted
    end
```

---

## 11. Scoring through the API

Integrations can score without a questionnaire run. No request may carry configuration, weights or a tenant override. All
three scoring writes accept an `Idempotency-Key`: a repeat of the same request returns the stored result, and the same key
with a different request is refused.

```mermaid
flowchart TD
    subgraph Calculate
        C1[POST /calculate<br/>assessment and inputs or responses] --> C2[validate the inputs]
        C2 --> C3[store an immutable assessment snapshot]
    end
    subgraph Score
        S1[POST /assessments/id/score<br/>no body] --> S2[load the stored snapshot]
    end
    subgraph Recalculate
        R1[POST /assessments/id/score/recalculate<br/>optional explicit version] --> R2[load the snapshot]
    end

    C3 --> RES
    S2 --> RES
    R2 --> RES
    RES[Resolve the effective configuration once] --> K{Idempotency key already used?}
    K -->|same request| REP[return the stored run]
    K -->|different request| ERR[refuse]
    K -->|new| PIPE[[Scoring pipeline, workflow 12]]
    PIPE --> ST[(Store the run, trace, results<br/>in one transaction)]
    ST --> OUT[ORGANIZATION run, plus one TEAM run per team<br/>when team answers were given]
```

Reads of a stored run: `GET /assessments/{id}/score` (latest), `GET /scoring-runs/{id}`, `/explanation`, `/dimensions`,
`/patterns`, `/penalties`, `/imbalances`, and `/answers` (owner, administrator, analyst; audited).

---

## 12. The scoring pipeline

Deterministic: the same inputs, configuration version and engine version always give the same result. Explanations come from
the recorded facts and never recompute a score. Missing data is never treated as zero; too little data gives
`INSUFFICIENT_DATA`, not an invented number.

```mermaid
flowchart TD
    A[Resolve tenant and load the assessment snapshot] --> B[Validate inputs]
    B --> C[Load the configuration, once per run]
    C --> D{Answer scopes}
    D -->|member answers| D1[Combine per question<br/>median or mean, minimum 5 responses]
    D -->|team or organization answers| E
    D1 --> E[Normalize answers<br/>scale, choice, boolean, number, text flag strategies]
    E --> F[Derive deterministic signals]
    F --> G[Raw dimension scores<br/>weighted average per dimension]
    G --> H{Coverage enough?}
    H -->|no| I[INSUFFICIENT_DATA<br/>no score]
    H -->|yes| J[Detect imbalances between dimensions]
    J --> K[Detect patterns<br/>conditions on raw scores and signals]
    K --> L[Evidence and confidence per pattern]
    L --> M[Penalty candidates<br/>eligibility, then amount by strategy]
    M --> N[Aggregate with a stacking policy<br/>record APPLIED, SUPPRESSED, INELIGIBLE, NOT_TRIGGERED]
    N --> O[Adjusted dimension scores, never below 0]
    O --> P[Overall LIS<br/>weighted adjusted dimensions minus overall penalties, 0 to 100]
    P --> Q[Interpret scores into bands]
    Q --> R[Build the explanation from the trace]
    R --> S[(Persist the run with versions and hashes)]
```

Dimensions: `TEAM_HEALTH`, `DELIVERY_STABILITY`, `TECHNICAL_RISK`, `LEADERSHIP_LOAD`. For every score, 100 is the desirable
state and 0 the undesirable one.

---

## 13. Reading results

All of these only read stored runs and never score. They are tenant- and organization-scoped.

```mermaid
flowchart TD
    DB[Dashboard<br/>/organizations/id/dashboard] --> LIS[LIS, interpretation, four dimensions,<br/>top risks, recommendations]
    DB --> TR[Trends<br/>/organizations/id/trends]
    LIS --> EX[Explanation<br/>/runs/id/explanation]
    EX --> DIM[Dimension detail<br/>contributions by question]
    EX --> PAT[Pattern detail<br/>evidence and confidence]
    EX --> PEN[Penalty detail<br/>amounts, suppressed, reason]
    EX --> IMB[Imbalances]
    DB --> HIS[History<br/>/scoring-runs with previous score and comparable flag]
    HIS --> CMP[Compare two runs<br/>/scoring-runs/compare]
    CMP -->|different configurations| NC[shown as not comparable, with the reason]
    LIS --> ANS[Source answers<br/>owner, administrator, analyst; audited]
    LIS --> REP[Reports, workflow 14]
```

Viewers see the same screens with per-question values withheld. The catalog endpoint
(`/scoring-runs/{id}/catalog`) supplies display text and score bands, so labels come from configuration.

---

## 14. Reports

Generated on demand from the stored run, trace, catalog and recommendations; never stored and never recalculated. They
contain no answers and nothing per member. Each generation is audited as `REPORT_GENERATED` (type, format, redacted flag;
never the content).

```mermaid
sequenceDiagram
    autonumber
    actor U as Any tenant role
    participant W as Reports screen
    participant A as ReportController
    participant S as ReportService
    participant B as ReportBuilder
    participant D as Database

    U->>W: choose a report type and an assessment (two for the comparison)
    W->>A: GET /reports/{type}?runId=&format=json (+ baseRunId)
    A->>A: validate type, format and runId (baseRunId only for the comparison)
    A->>S: run(tenant, scope, runId)
    S->>D: read the stored run, trace, catalog
    S->>S: calibration status of every configuration layer
    S->>B: build the document (sections, rows)
    B-->>A: ReportDocument (pure function of stored data)
    A->>A: redact unless owner, administrator or analyst
    A->>D: audit REPORT_GENERATED
    A-->>W: JSON document, drawn on the page
    U->>W: Download PDF or Download CSV
    W->>A: same request with format=pdf or csv
    A->>A: render the same document
    A-->>W: file attachment, saved by the browser
```

Report types: `LEADERSHIP_INTELLIGENCE`, `DIMENSION_ANALYSIS`, `PATTERN_PENALTY`, `HISTORICAL_COMPARISON`. The methodology
block says "Unvalidated" unless every configuration layer has a calibration reference.

---

## 15. Audit and observability

```mermaid
flowchart LR
    subgraph Audited
        A1[Answers read] --> AA[(Access audit)]
        A2[Reports generated] --> AA
        A3[Run started or closed] --> AA
        A4[Tenant created or listed] --> PAU[(Platform audit)]
        A5[Configuration reviews] --> REV[(Review records)]
    end
    subgraph Observed
        M[Micrometer metrics<br/>stage timings, persistence, scoring counts] --> PR["/actuator/prometheus<br/>owner, administrator"]
        L[ECS-format logs<br/>no answers, no tokens] --> LG[(Log pipeline)]
        H["/actuator/health"] --> HC[Public health check]
    end
```

Audit rows record who and how many, never what: no answer text and no report content.

---

## 16. Role and endpoint matrix

| Area | Endpoints | Platform admin | Owner / Admin | Eng. leader | Analyst | Viewer | Respondent |
|------|-----------|:---:|:---:|:---:|:---:|:---:|:---:|
| Tenants | `/platform/tenants` (GET, POST) | yes | no | no | no | no | no |
| Platform default configuration | `/platform/configuration/**` | yes | no | no | no | no | no |
| Tenant configuration, edit | `/configuration/**` (writes) | no | yes | no | no | no | no |
| Tenant configuration, read | `/configuration/**` (GET) | no | yes | no | yes | no | no |
| Questionnaires | `/questionnaires/**` | no | yes | no | no | no | no |
| Roster | `/teams/**`, `/people/**` | no | yes | no | no | no | no |
| Assessment runs | `/runs/**` | no | yes | no | no | no | no |
| Respond | `/respond/**` | no | no | no | no | no | yes |
| Scoring writes | `/calculate`, `/assessments/*/score`, `/score/recalculate` | no | yes | yes | no | no | no |
| Source answers (audited) | `/scoring-runs/*/answers` | no | yes | no | yes | no | no |
| Read results | `GET /assessments`, `/scoring-runs/**`, `/organizations/**` | no | yes | yes (values withheld) | yes | yes (values withheld) | no |
| Reports | `/reports/{type}` | no | yes | yes (values withheld) | yes | yes (values withheld) | no |
| Metrics | `/actuator/prometheus`, `/actuator/metrics` | no | yes | no | no | no | no |
| Health | `/actuator/health` | public | public | public | public | public | public |

"Values withheld" means the redaction rule `!hasAnyRole(OWNER, ADMIN, ANALYST)`. Authority is derived from the `roles`
claim; the tenant and the organizations (`org_ids`) come only from the token.
