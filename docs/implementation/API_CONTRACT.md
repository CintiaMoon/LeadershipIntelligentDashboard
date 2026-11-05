# API Contract (Draft)

**Baseline:** `specs/specv3.md` (V3). Items marked **V3** come from it. Items marked **PROPOSED** come from `specs/frontend-spec.md` §16 or from gap resolutions in `ARCHITECTURE_GAPS.md` and need approval. Nothing here is implemented.

## 1. Conventions

- Base path `/api/v1`. JSON, UTF-8. Every endpoint enforces authentication, tenant scope and authorization (V3 §28).
- Authentication: Spring Security resource server validating platform JWTs (D19). Claims `sub`, `tenant_id`, `org_ids`, `roles`. Roles OWNER, ADMIN, ENGINEERING_LEADER, ANALYST, VIEWER. Writes (`/calculate`, `/score`, `/recalculate`): OWNER, ADMIN, ENGINEERING_LEADER. Reads: all roles. Raw evidence/text in explanations: OWNER, ADMIN, ANALYST (VIEWER receives redacted evidence). Non-default configuration on recalculate: OWNER, ADMIN.
- Tenant and organization identity come from the trusted server context, never from the request (V3 §7.1, §25.1). Tenancy is enforced (D9): every endpoint is tenant-scoped and cross-tenant access returns `TENANT_ACCESS_DENIED` or not-found.
- The request never carries scoring or normalization configuration (V3 §8.2).
- Numeric precision is documented and fixed (V3 §20.3, §31): 2 decimals in the result, rounded at defined stages (D16). The client must not add precision.
- Entities are never exposed; DTOs only (V3 §37.1).
- Missing values are represented explicitly; an insufficient dimension never appears as a fabricated number (V3 §20.3).
- Optional `Idempotency-Key` header on scoring requests (V3 §7.5, D22); unique per tenant; same key with a different request returns 409.

## 2. Endpoints

### 2.1 V3 endpoints (9) plus `/calculate` (D10)

| Method | Path | Purpose | V3 |
|---|---|---|---|
| POST | `/assessments/{assessmentId}/score` | Score a completed assessment from its immutable snapshot; new run or existing idempotent result | §28.1 |
| GET | `/assessments/{assessmentId}/score` | Latest authorized scoring result; documented not-found if none | §28.2 |
| GET | `/scoring-runs/{scoringRunId}` | Immutable result of a run | §28.3 |
| GET | `/scoring-runs/{scoringRunId}/explanation` | Persisted trace; never recalculates | §28.4 |
| POST | `/assessments/{assessmentId}/score/recalculate` | New run; may select a specified configuration with permission | §28.5 |
| GET | `/scoring-runs/{scoringRunId}/dimensions` | Raw and adjusted scores, coverage, contributions, statuses | §28.6 |
| GET | `/scoring-runs/{scoringRunId}/patterns` | Pattern evaluations and permitted evidence | §28.7 |
| GET | `/scoring-runs/{scoringRunId}/penalties` | Candidates, applied and suppressed deductions, reasons | §28.8 |
| GET | `/scoring-runs/{scoringRunId}/imbalances` | Imbalance findings | §28.9 |
| POST | `/calculate` | Submit an assessment and its answers; validate, persist an immutable snapshot, score, persist the run | V3 §4.1, §7.2; D10, D13; D14 |

`POST /score` request (V3 §7.2):

```json
{ "assessmentId": "assessment-001" }
```

The server loads the snapshot and answers. `/score` never accepts `inputs`.

`POST /calculate` request (D10, D13, D14):

```json
{
  "assessment": { "organizationId": "org-001",
                  "templateId": "tpl-1", "templateVersion": "3",
                  "status": "COMPLETED", "completedAt": "2026-10-30T12:00:00Z" },
  "inputs": [ { "questionId": "Q01", "answer": 4 }, { "questionId": "Q02", "answer": "OFTEN" } ]
}
```

The service validates the assessment and inputs (V3 §7.4: known question, belongs to the template version, compatible type, duplicate policy, required answers, configured constraints), persists an immutable assessment snapshot with its answers, scores it, persists the run and returns the same `ScoringResult`. A client-supplied `questionType` is checked against the configured definition and never used as configuration. Tenant comes only from the security context; `organizationId` is checked against the caller's authorized organizations and never overrides context. The request must not carry configuration or weights. The server generates the `assessmentId` and returns it in the result; the client never supplies it. Each call creates a new assessment and snapshot. Retry safety uses the `Idempotency-Key` header (V3 §7.5): the same key with the same request returns the existing run; the same key with a different request returns `IDEMPOTENCY_CONFLICT` (409). `/calculate` uses the same role as `/score`. To re-score an existing assessment, use `/score` or `/score/recalculate`. CLAUDE.md still shows the old `inputs` body on `/score` (CL2).

Recalculate body (D21): optional `{ "configurationVersion": "config-v12" }`; OWNER/ADMIN only; version must belong to the tenant and be ACTIVE or ARCHIVED; the run records `configurationSelection: DEFAULT | EXPLICIT`.

Idempotency (V3 §27.4): identity = tenantId + assessmentSnapshotId + inputHash + configurationHash + engineVersion + algorithmVersion. Reuse of an idempotency key with a materially different request returns `IDEMPOTENCY_CONFLICT` (409). `recalculate` always creates a new run.

### 2.2 Not in V3 (build order per D26)

| Method | Path | Status | Depends on |
|---|---|---|---|
| GET | `/assessments`, `/assessments/{id}` | step 2, derived from snapshots (D13, D26); status and organization filters, cursor paging | none |
| GET | `/scoring-runs?assessmentId=&organizationId=&from=&to=` | step 3 run summaries with `comparable` flag (D27) | none |
| GET | `/organizations/{id}/dashboard` | step 4, after run summaries and recommendations | D27, D28 |
| GET | `/scoring-runs/history`, `/scoring-runs/compare` | step 3-4, comparison only when `comparable` (D27) | D27 |
| GET | `/reports/{type}?runId=&format=json\|csv\|pdf` (comparison: `baseRunId=&runId=`) | JSON, CSV and PDF built (D59 to D64, `REPORTS_PROPOSAL.md`): types `LEADERSHIP_INTELLIGENCE`, `DIMENSION_ANALYSIS`, `PATTERN_PENALTY`, `HISTORICAL_COMPARISON`; generated on demand from the stored run, nothing stored; no `GET`/`POST /reports` list | F13 resolved |

FE `/scoring-runs/{id}/contributions` is not separate: contributions are returned by `/dimensions` (V3 §28.6). Pagination and filtering for the pattern and penalty lists (FE §10.1, §11.2) are PROPOSED: `page`, `size`, `sort` and filters named after the FE filter lists.

## 3. ScoringResult (V3 §20.1, canonical)

V3's shape is canonical (D11). Fields marked `+` are additive fields.

```json
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
    "TEAM_HEALTH": { "rawScore": 84, "effectivePenalty": 3, "adjustedScore": 81,
                     "coverage": 1.0, "status": "VALID",
                     "weight": 0.25, "weightedContribution": 20.25, "label": null }
  },
  "overall": { "weightedAdjustedScore": 69.50, "effectiveOverallPenalty": 0, "finalScore": 69.50 },
  "imbalances": [],
  "patterns": [],
  "penalties": [],
  "explanationReference": "run-001"
}
```

Rules:
- `status` (execution) is `PENDING | RUNNING | COMPLETED | FAILED`; `scoreStatus` (validity) is `VALID | INSUFFICIENT_DATA | PARTIAL` (V3 §20.2). The API distinguishes them (V3 §20.3). v1 is synchronous (F8).
- `PARTIAL` appears only if a partial-scoring policy is enabled (V3 §17.5); default disabled.
- A dimension with `status: INSUFFICIENT_DATA` has no numeric `rawScore`/`adjustedScore`. When a required dimension is insufficient, `leadershipIntelligenceScore` is absent and `scoreStatus` is `INSUFFICIENT_DATA`; `interpretation` is not a risk category (V3 §18.1).
- `previousValue`/`change` (FE §16.2) appear only on comparable runs (D27) and are computed by the backend read model; otherwise omitted and the client shows "not comparable". The client never computes them.
- `weight`, `weightedContribution` and `label` (`+`) are additive for the frontend; `label` comes only from backend configuration (D25), which maps the dimension map to its array (C13).
- Penalty `status` is exactly one of `APPLIED`, `SUPPRESSED`, `INELIGIBLE`, `NOT_TRIGGERED` (D7, V3 §16.4), each with a reason code.
- Pattern `evaluationStatus` is exactly one of `MATCHED`, `NOT_MATCHED`, `INELIGIBLE`, `NOT_EVALUATED`, with a reason code (V3 §13.6).
- Confidence is in [0,1] with a configured band label `INSUFFICIENT | WEAK | MODERATE | STRONG` (V3 §14.3, illustrative).
- Interpretation labels come from decimal half-open ranges (D6).
- Canonical fixture: V3 §17.6 (LIS 69.50).

## 4. Explanation (trace schema, D24)

One versioned JSON document (`traceVersion`, ordered `steps`: `RAW_DIMENSIONS`, `IMBALANCES`, `PATTERNS`, `PENALTIES`, `ADJUSTED_DIMENSIONS`, `OVERALL`), stored with the run and read back unchanged. Built from the persisted trace without recalculation, newer configuration or inferred evidence (V3 §19.4). Ordered stages follow V3 §19.1; required content follows V3 §19.2:

1. `rawDimensionScores` with coverage, contribution list, positive and negative drivers
2. `patterns` (id/code/version, evaluation status, condition tree result, evidence references, severity, confidence, eligibility reason, action keys)
3. `penalties` (id/code/version, target, strategy, score base, candidate, individual cap, effective, stacking policy, aggregation group, suppression reason, winning penalty)
4. `adjustedDimensionScores`
5. `weightedContributions` with dimension weights
6. `overallPenalties`, LIS before overall penalty, final LIS, interpretation

Evidence values are subject to authorization and data minimization (V3 §19.5, §33.3).

## 5. Errors

Format (V3 §28.10):

```json
{ "code": "QUESTION_NOT_CONFIGURED", "message": "Question Q15 has no scoring configuration.",
  "details": { "questionId": "Q15" }, "correlationId": "request-123" }
```

Catalog: the 34 stable codes in V3 §28.11. HTTP mapping from V3 §29.3: invalid request 400, unauthorized 401, forbidden 403, not found 404, idempotency conflict 409, validation failure 422, unexpected 500. PROPOSED assignment of codes:

| Codes | HTTP |
|---|---|
| `QUESTION_NOT_CONFIGURED`, `QUESTION_NOT_FOUND`, `DUPLICATE_QUESTION_INPUT`, `UNSUPPORTED_QUESTION_TYPE`, `INVALID_ANSWER_TYPE`, `INVALID_SCALE_VALUE`, `INVALID_NUMBER_VALUE`, `INVALID_MULTIPLE_CHOICE_VALUE`, `MISSING_REQUIRED_INPUT`, `ASSESSMENT_NOT_COMPLETED`, `ASSESSMENT_SNAPSHOT_INVALID` | 422 |
| `INVALID_*_CONFIGURATION`, `INVALID_CONDITION_EXPRESSION`, `INVALID_CONFIGURATION_VERSION`, `CONFIGURATION_SNAPSHOT_INVALID`, `CONFIGURATION_ACTIVATION_FAILED` | 422 on activation; 500 if an active version is found invalid |
| `ASSESSMENT_NOT_FOUND`, `ASSESSMENT_SNAPSHOT_NOT_FOUND`, `CONFIGURATION_VERSION_NOT_FOUND`, `SCORING_RUN_NOT_FOUND` | 404 |
| `CONFIGURATION_NOT_ACTIVE` | 409 |
| `TENANT_CONTEXT_MISSING` | 401 |
| `TENANT_ACCESS_DENIED`, `ORGANIZATION_ACCESS_DENIED` | 403 |
| `IDEMPOTENCY_CONFLICT` | 409 |
| `INSUFFICIENT_DATA` | 200 with `scoreStatus: INSUFFICIENT_DATA` (a valid outcome, not a failure); 422 only for REQUIRED-input violations |
| `SCORING_EXECUTION_FAILED`, `PERSISTENCE_FAILURE` | 500 |

Error responses never leak stack traces, configuration internals or raw assessment answers (V3 §29.4).

## 6. Open contract decisions

None blocking. Reports (F13) are specified by D59 to D64 and not yet built. Production question catalog and fixtures are still needed (V3 §42). Decisions D9-D30 are in `ARCHITECTURE_GAPS.md`.

## 7. Recommendations (D28)

Recommendation text comes from a versioned configuration catalog keyed by action key (title, description, priority, next action). Responses include catalog entries for the action keys present in the run, ordered by triggering pattern severity, then configured priority, then key.

## 8. As implemented (phase 12)

- Authentication: bearer JWT, verified with the key set in `lis.security.jwk-set-uri`; claims `sub`, `tenant_id`, `org_ids`, `roles`.
- Extension error codes beyond V3 §28.11: `INVALID_REQUEST` (400, 405, 415), `UNAUTHENTICATED` (401), `ACCESS_DENIED` (403, role).
- Sub-resource bodies are envelopes with `scoringRunId` and the stored findings (`dimensions` and `adjusted`, `patterns`, `penalties` with `effectiveByTarget` and `tieBreakVersion`, `imbalances`) plus a `redacted` flag; `/explanation` returns `{ scoringRunId, redacted, trace }`.
- The result carries `source`, `configurationSelection` and `counts` as additive fields; absent values are omitted, so an insufficient dimension has no score fields.
- Reports (D59 to D64): `GET /api/v1/reports/{type}` returns JSON, CSV or PDF.

## 9. As implemented (phases 14-15)

Read endpoints (any known role; tenant and `org_ids` enforced; a foreign organization is 403 when named, otherwise filtered out; a foreign assessment or run is 404):

| Method | Path | Notes |
|---|---|---|
| GET | `/assessments?organizationId=&status=&cursor=&limit=` | `{items, nextCursor}`; each item has `latestRun` (id, scoreStatus, score, interpretation, calculatedAt) |
| GET | `/assessments/{assessmentId}` | same item shape |
| GET | `/scoring-runs?organizationId=&assessmentId=&from=&to=&cursor=&limit=` | newest first; items have `previousScoringRunId`, `previousScore`, `change` (only when `comparable`), `comparable`, `notComparableReasons` (D37) |
| GET | `/scoring-runs/compare?baseRunId=&runId=` | `comparable`, `notComparableReasons`, `scoreChange`, `dimensions[]` (base, score, change), `patterns` (appeared, resolved, persistent), `notice` |
| GET | `/reports/{type}?runId=&format=json\|csv\|pdf` | A report document `{type, title, redacted, notice, sections[{id, heading, note, columns[], rows[][]}]}`, no generation time, so the same run gives the same document. The type is `LEADERSHIP_INTELLIGENCE`, `DIMENSION_ANALYSIS`, `PATTERN_PENALTY` or `HISTORICAL_COMPARISON` (any case, dashes allowed); the comparison also takes `baseRunId` and refuses it elsewhere. PDF is an A4 landscape download drawn from the same document (tagged, titled, language set, repeating table header, built-in fonts so characters outside Latin-1 are not drawn; its bytes carry a creation time, its text does not change). CSV is a UTF-8 download with a byte-order mark, one block per section (heading, columns, rows), and defuses cells that start with `=`, `+`, `-` (non-numbers), `@`, tab or carriage return. Same scope as the run reads (a foreign run is 404), same redaction for the VIEWER role (per-question values withheld), no answers in any format, each generation audited as `REPORT_GENERATED` (type, format, redacted; never the content). 400 for an unknown type or format (json, csv, pdf), a bad or missing run id, or `baseRunId` on a single-run report |
| GET | `/scoring-runs/{id}/recommendations` | `{recommendations[], unmappedActionKeys[], notice}`; each entry has key, title, description, priority, severity, sources (D38) |
| GET | `/organizations/{organizationId}/dashboard` | `latestPerTemplate[]`, `recentRuns[]`, `assessmentsByStatus`, `notice` |

Invalid `limit` (outside 1-100), `cursor`, `status`, `from`, `to` or run ids are 400 `INVALID_REQUEST`. `ScoringResult` has an additional `notice` (V3 §2.4). Operations: `/actuator/prometheus` and `/actuator/metrics` for OWNER and ADMIN (D36).


## 10. As implemented (frontend step 0: decisions D39, D40)

| Method | Path | Notes |
|---|---|---|
| GET | `/scoring-runs/{id}/catalog` | `patterns`, `penalties`, `dimensions`, `signals`, `questions` (each id to `{name, description, explanation?}`), `lisBands` and `dimensionBands` (`{label, min, max}`, half-open per D6) |
| GET | `/organizations/{id}/trends?templateId=&from=&to=&limit=` | `{organizationId, templateId?, points[], notice}`; each point has `run` (same shape as a run summary), `dimensions[]` (`dimension`, `rawScore`, `adjustedScore`), `patterns[]` (`patternId`, `severity`, `category`), `patternsBySeverity`, `penalties` (`applied`, `suppressed`, `ineligible`, `notTriggered`, `effectiveDeduction`, `suppressedAmount`) |


## 11. As implemented (frontend phase 3: decision D42)

| Method | Path | Notes |
|---|---|---|
| GET | `/scoring-runs/{id}/answers` | `{scoringRunId, answers: [{questionId, answer}]}`, ordered by question id; OWNER, ADMIN, ANALYST only; audited as `ANSWERS_READ` (count only); 404 for another tenant, 403 for an organization outside the token |

## 12. As implemented (response scopes: decision D45)

`POST /calculate` accepts answers in one of two forms, never both (otherwise 400 with `details.inputs`):

- `inputs: [{questionId, answer}]`: one answer per question for the organization. Unchanged in behavior, hash (`input-hash-v1`) and result.
- `responses: [{questionId, answer, scope, teamId?, respondents?, questionType?}]` (up to 20000). `scope` is `MEMBER` (one respondent, no identity), `TEAM` (needs `teamId`) or `ORGANIZATION` (no `teamId`). `respondents` is the number of people a TEAM or ORGANIZATION figure represents, claimed by the caller (labelled `DECLARED`); it is not allowed on a MEMBER answer. `teamId` is 1 to 64 letters, digits, dot, underscore or hyphen. One scope per question inside a scoring unit (`MIXED_RESPONSE_SCOPES`, 422); a second figure for the same question and team is `DUPLICATE_TEAM_SCOPE`, for the organization `DUPLICATE_QUESTION_INPUT`; a bad combination such as a TEAM answer without a team is `INVALID_RESPONSE_SCOPE` (422). A question is scored from the answers of its unit: median or mean of the individually normalized answers (configuration `responses.defaultMethod`, per question `aggregation`); fewer answers than `responses.minResponses` (default 5) make the question missing under its own missing-data policy, never zero.

The response is the organization run (all answers pooled) with these additional fields:

| Field | Meaning |
|---|---|
| `scope` | `ORGANIZATION` or `TEAM` |
| `teamId`, `organizationScoringRunId` | only on a TEAM run: its team and the organization run it was produced with |
| `responses` | absent for `inputs`; otherwise `{questions: [{questionId, scope, method, respondents?, participation (OBSERVED, DECLARED, UNKNOWN), usedAnswers, minimum, belowMinimum}], unscoredTeams?}`; never an answer |
| `teamRuns` | on an organization run: `[{teamId, scoringRunId?, scoreStatus, leadershipIntelligenceScore?, interpretation?}]` in team order; a team with too few answers is `INSUFFICIENT_DATA` with no score, and a team that could not be scored at all (a required question with too few answers) has no run |

Every endpoint that reads a run (`/scoring-runs/{id}`, `/explanation`, `/dimensions`, `/patterns`, `/penalties`, `/catalog`, `/recommendations`) works on a TEAM run exactly as on an organization run. History, trends, the dashboard, an assessment's latest result and comparison are organization-level only: a team run is never listed in them and cannot be compared (`/scoring-runs/compare` returns 404 for it). Scoring again reuses the organization run and its team runs; recalculating creates a new set.

`GET /scoring-runs/{id}/answers` for scoped answers: organization and team answers as before (`answer`, `scope`, `teamId?`, `declaredRespondents?`); member answers come as one group per question `{questionId, scope: "MEMBER", count, values (sorted, carries no submission order), suppressed}`. A group smaller than the minimum is `suppressed: true` with its count only. A TEAM run shows only its team's answers. Still OWNER, ADMIN, ANALYST only and audited.

Confidence: a run whose answers carry respondent counts uses confidence algorithm version 2 (participation factor, specv3 14.2.1); each pattern's `confidenceBreakdown` then has `participation` and `respondents`. Other runs keep version 1.

Storage: Flyway V6 adds the answer scope columns and the run `scope_type`, `scope_id`, `parent_run_id` and `response_summaries`. Members are stored with an ordinal only.

## 13. As implemented (platform administration: decision D46)

Only the `PLATFORM_ADMIN` role reaches `/api/v1/platform/**`. The role belongs to no tenant: its token needs no `tenant_id` (and carries none), so it cannot reach anything tenant-scoped (403 on every other endpoint), and the five tenant roles get 403 here, anonymous callers 401. A token that carries both the role and a tenant changes nothing about what these endpoints return.

| Method | Path | Notes |
|---|---|---|
| GET | `/platform/tenants` | `{items: [{id, name, createdAt}]}`, oldest first; the read is audited as `TENANTS_LISTED` (the count, no tenant) |
| POST | `/platform/tenants` | body `{id, name}` and nothing else (unknown fields are rejected). `id`: 2 to 63 lowercase letters, digits or hyphens, starting with a letter or digit, permanent; it is trimmed. `name`: 1 to 200 characters, trimmed. 201 with the tenant. 409 `TENANT_ALREADY_EXISTS` for a taken id (nothing changes); 422 `INVALID_TENANT` for an id or name that is not allowed; 400 for a missing or oversized field. Audited as `TENANT_CREATED` against the new tenant, with the subject of the caller |

A tenant can score assessments as soon as it exists and the identity provider issues tokens with its `tenant_id`. Organizations are still registered the first time an assessment names them.

## 14. As implemented (questions with several chosen options: decision D49)

A question of type `MULTI_SELECT` takes a **list of option identifiers** as its `answer` (in `inputs[]` and in `responses[]`, for any scope). Its scoring rule is the mean of the values of the options chosen (specv3 4.2.1). The order does not matter and a repeated option counts once; an empty list is missing data, never zero (the dimension's coverage then decides whether it still has a score); an option the configuration does not define is a 422 (`INVALID_MULTIPLE_CHOICE_VALUE`) unless the question ignores unknown options; anything that is not a list of strings is a 422 (`INVALID_ANSWER_TYPE`); more than 100 options, or an option longer than 200 characters, is a 400. `questionType: "MULTI_SELECT"` may be declared and is checked against the configuration like any type.

Stored answers: a selection is kept as a sorted set, and the input hash ignores order and repeats, so the same choices always hash the same. `GET /scoring-runs/{id}/answers` returns the list as `answer` (sorted); for member answers each value of the `values` group is a list. Configuration documents define the type with `"type": "MULTI_SELECT"` and `"normalization": {"values": {"OPTION": 0-100, ...}, "unknownOption": "REJECT|TREAT_AS_MISSING"}`.

Configuration layers: an object `{"$replace": X}` replaces what lower layers have for that key instead of merging (specv3 23.3, decision D47); it also works as a whole layer. Nothing uses it yet in the product; the questionnaire builder (phase 2) will.


## 15. As implemented (questionnaire library: decisions D47, D50)

All endpoints are tenant-scoped (tenant from the token only) and reachable by `OWNER` and `ADMIN` only, for reads as well as writes. A questionnaire of another tenant answers 404 `QUESTIONNAIRE_NOT_FOUND`. Questionnaire content is never logged.

| Method and path | Purpose | Notes |
|---|---|---|
| `GET /api/v1/questionnaires` | list, newest first | `state`: `DRAFT`, `PUBLISHED`, `PUBLISHED_WITH_DRAFT`, `ARCHIVED` |
| `POST /api/v1/questionnaires` | create an empty questionnaire with a first draft | body `{name}`; 201 |
| `POST /api/v1/questionnaires/from-initial` | create a draft copied from the configuration that applies to the tenant | body `{name}`; 201; `notes` lists what the builder could not carry over |
| `GET /api/v1/questionnaires/{id}?version=` | the open draft, else the latest published version, or exactly `version` | `problems` lists what blocks publishing a draft; `versions` lists every version |
| `PUT /api/v1/questionnaires/{id}` | save the open draft; opens the next draft when the latest version is published | body is the builder document; unknown fields are rejected (400); a draft may be incomplete, only its shape is checked (422 `QUESTIONNAIRE_INVALID`) |
| `POST /api/v1/questionnaires/{id}/publish` | publish the open draft as an active TEMPLATE layer | 422 `QUESTIONNAIRE_INVALID` with every problem as `details.problem01..`; 409 `QUESTIONNAIRE_STATE` without an open draft or when archived |
| `POST /api/v1/questionnaires/{id}/archive` | hide from new use (idempotent) | the active layer stays |
| `POST /api/v1/questionnaires/{id}/duplicate` | new questionnaire with a copy as its first draft | 201 |

The builder document is `{name, description, questions[], rules[]}`. A question has `id` (assigned `Q01..` when blank), `kind` (`SINGLE_CHOICE`, `MULTI_CHOICE`, `RATING`, `LIKERT`, `NPS`, `NUMERIC`, `YES_NO`, `FREE_TEXT`), `text`, optional `title` and `help`, `required`, `dimension` (null means collected, not mapped), `weight`, `higherIsBetter`, `options[{key, label, score?}]`, `points`, `labels`, `min`, `max` and `desirableAnswer` (`YES`/`NO`). A rule has `id` (assigned `X-1..`), `name`, `description`, `explanation`, `severity`, `condition` (`op`, `conditions`, `condition`, `question` or `dimension`, `value`, `low`, `high`, `values`), `minimumSignals`, `minimumConfidence`, an optional `penalty` (`target`, `strategy`, `amount`, `stackingPolicy`, `cumulativeCap`, `category`, `priority`) and an optional `recommendation` (`title`, `description`, `priority`). The questionnaire id is also the template id for `POST /calculate`. Choices made in phase 2 are listed in `QUESTIONNAIRES_PROPOSAL.md` section 9.

## 16. As implemented (roster, runs and the respondent flow: decision D48)

Errors: `ROSTER_NOT_FOUND` and `RUN_NOT_FOUND` (404), `ROSTER_INVALID` and `RUN_INVALID` (422), `ROSTER_CONFLICT` and `RUN_STATE` (409). A team, person or run of another tenant, or of an organization outside the token, looks like a missing one.

**Administrators (OWNER, ADMIN)**

| Method and path | Purpose |
|---|---|
| `GET /api/v1/teams?organizationId=` | teams with the number of active people |
| `POST /api/v1/teams` `{organizationId, name}` | create a team (201); duplicate name in the organization is 409 |
| `PUT /api/v1/teams/{teamId}` `{name}` | rename |
| `GET /api/v1/people?teamId=` | the roster (the read is audited) |
| `POST /api/v1/people` `{teamId, name, email}` | add a person (201); the e-mail is stored lower-cased and is unique per tenant |
| `PUT /api/v1/people/{personId}` `{teamId, name, email, active}` | replace; people are deactivated, never deleted |
| `GET /api/v1/runs?organizationId=&status=OPEN\|CLOSED` | runs, newest first |
| `POST /api/v1/runs` `{name, organizationId, questionnaireId, teamIds, dueDate?}` | start a run on the latest published version (201); one participant per active person of the teams |
| `GET /api/v1/runs/{runId}` | progress per team and the participant list with their status; never an answer; the read is audited; carries the notice about small teams |
| `POST /api/v1/runs/{runId}/close` | score the stored answers once as member answers with the pinned configuration; 409 when already closed or when nothing was submitted |

**Respondents (role RESPONDENT, matched by the token e-mail; nothing else is reachable)**

| Method and path | Purpose |
|---|---|
| `GET /api/v1/respond/runs` | my runs |
| `GET /api/v1/respond/runs/{runId}` | the questions (no dimension, weight, direction, desirable answer or option score), my status and my draft |
| `PUT /api/v1/respond/runs/{runId}/draft` `{answers:[{questionId, answer}]}` | save answers; a null or empty answer removes one; every answer is checked against its question (422 `RUN_INVALID`) |
| `POST /api/v1/respond/runs/{runId}/submit` | store my answers with my team only and delete my draft; 422 lists the required questions still open (`details.missing01..`); 409 when already submitted or the run is closed |

Answer shapes: rating, Likert, score and number questions take a JSON number; yes/no a boolean; single choice an option key; multiple choice a list of option keys; free text a string.

## 17. As implemented (configuration management: decisions D52 to D57)

Two ways in with the same endpoints. Owners and administrators manage the **tenant override** and the **organization overrides** of the organizations in their token under `/api/v1/configuration` (analysts may read); the platform administrator manages the **platform default** under `/api/v1/platform/configuration` (nothing else reaches it). Questionnaire (template) layers belong to the questionnaire builder and are not managed here. A version, layer or fixture outside the caller's reach looks missing (404 `CONFIGURATION_VERSION_NOT_FOUND`). Paths below are relative to either base.

| Method and path | Purpose |
|---|---|
| `GET /layers` | the layers the caller manages, each with its active version and the one being worked on; `unvalidated` is true without a calibration reference |
| `GET /versions?scope=TENANT\|ORGANIZATION&organizationId=` | the versions of one layer, newest first, without their documents (on the platform path the layer is always the platform default) |
| `GET /versions/{versionId}` | one version with its document and its review record |
| `POST /versions` `{scope, organizationId?, document?}` | a new draft (201): the given document, or a copy of the layer's active version; `scope` is TENANT (default) or ORGANIZATION |
| `PUT /versions/{versionId}` (the whole configuration document) | replace a draft's document; any other status is 409 |
| `POST /versions/{versionId}/copy` | a new draft with the content of any version of the layer (201): how a change is rolled back |
| `POST /versions/{versionId}/validate` | every problem of the version, without changing it: the first problem of each question, signal, pattern, penalty, imbalance and recommendation and of each other section, with its JSON path (at most 50), and the cross-references only when nothing else is wrong |
| `POST /versions/{versionId}/preview` | scores the layer's fixtures, and the tenant's recent assessments (up to 50, within the caller's organizations) when `lis.configuration.preview.use-recent-assessments` is true, with the draft in place and as it is now, in memory only; returns ids, scores, bands, patterns, penalties and what changed, never an answer; the read of assessments is audited (`CONFIGURATION_PREVIEW`, counts only). A platform preview uses fixtures only |
| `POST /versions/{versionId}/submit` | validates (every problem is returned and the version stays a draft) and previews; a valid draft moves to VALIDATING, which here means awaiting approval |
| `POST /versions/{versionId}/approve` `{note, effectiveFrom?, calibrationReference?, knownLimitations?}` | approves and activates; 403 `CONFIGURATION_APPROVAL_REFUSED` for anyone who created, edited or submitted it (unless `lis.configuration.approval.require-second-person` is false); 409 `CONFIGURATION_FIXTURES_FAILED` while a fixture fails (`details.fixture01..` name them); 400 without a note |
| `POST /versions/{versionId}/reject` `{comment}` | back to draft with the reason (anyone who manages the layer, including the author) |
| `POST /versions/{versionId}/pause`, `/resume`, `/archive` | lifecycle changes of an active version (an active version is never edited) |
| `GET /layers/{layerId}/fixtures`, `POST` (201), `PUT /{fixtureId}`, `DELETE /{fixtureId}` (204) | the fixtures of a layer: `{name, answers:[{questionId, answer}], expectations:{status?, overall?:{min,max}, dimensions?, patternsMatched?, patternsNotMatched?}}`; 422 `FIXTURE_INVALID` (a name is unique per layer) |

A version carries its **review record** once submitted: submitter, validation problems, preview summary (counts and a before/after distribution) and fixture results, then who approved it, the note, the calibration reference and the known limitations, or who rejected it and why. The record of an approved version does not change (database trigger). Every action is also in the configuration audit trail (`CREATED`, `UPDATED`, `VALIDATED`, `REJECTED`, `ACTIVATED`, `APPROVED`).


## 18. As implemented (accounts and sessions, phase P1: decisions D65 to D68, D73, D74)

Built-in accounts (`docs/implementation/IDENTITY_PROPOSAL.md`). These endpoints use a **server-side session** and CSRF protection, not a bearer token; every other endpoint still takes the bearer token until the company memberships of phase P3 move it to sessions too. A session cookie alone reaches nothing tenant-scoped (401). All paths are under `/api/v1/auth`.

Cookies: `LIS_SESSION` (the session id; HttpOnly, Secure, SameSite=Lax; 30 minutes idle, 8 hours at most) and `XSRF-TOKEN` (readable by the web application). Every `POST` needs the header `X-XSRF-TOKEN` with the value of the `XSRF-TOKEN` cookie; without it the answer is 403 `CSRF_TOKEN_INVALID`. A new session and a new CSRF token are issued at every sign-in.

| Method and path | Body | Answer |
|---|---|---|
| `GET /csrf` | | 200 `{headerName, token}` and sets `XSRF-TOKEN` |
| `POST /register` | `{email, password, displayName}` | 202, empty. The same for a new and for a known address (a known one gets an email instead). 422 `INVALID_ACCOUNT_REQUEST` (bad email or name), 422 `WEAK_PASSWORD` |
| `POST /verify-email` | `{token, password}` (the password chosen at registration) | 204; 400 `INVALID_TOKEN` (unknown, used, expired or retired by a newer link, or a wrong password; a wrong password leaves the link usable) |
| `POST /resend-verification` | `{email}` | 202, always |
| `POST /login` | `{email, password}` | 200 `{id, email, displayName, platformAdmin, companies}` and sets the session cookie; 401 `AUTHENTICATION_FAILED` with the same body for an unknown email, a wrong password, an unverified and a disabled account |
| `POST /logout` | | 204, also without a session. Like every POST it needs the CSRF header (D68); a client whose token is gone calls `GET /csrf` and retries |
| `GET /me` | | 200 as for login; 401 without a session or when the account is no longer ACTIVE |
| `POST /password/forgot` | `{email}` | 202, always; an ACTIVE account is sent a reset link (valid 1 hour, once), a pending one a verification link |
| `POST /password/reset` | `{token, newPassword}` | 204 and every session of the user is ended; 400 `INVALID_TOKEN`; 422 `WEAK_PASSWORD` (the link still works afterwards) |
| `POST /password/change` | `{currentPassword, newPassword}` | 204 (needs a session); the user's other sessions are ended; 422 when the current password is wrong |

Password rule: 12 to 128 characters, not a very common password, not the email address, not a single repeated pattern; no composition rules. Emails are normalized (trimmed, lower case). `companies` is always empty in P1 and will list only companies with an ACTIVE membership. New codes: `AUTHENTICATION_FAILED` (401), `INVALID_ACCOUNT_REQUEST` and `WEAK_PASSWORD` (422), `INVALID_TOKEN` (400), `CSRF_TOKEN_INVALID` (403), `TOO_MANY_REQUESTS` (429).

Throttling (decision D71; starting values, not calibrated): every refusal is `429 TOO_MANY_REQUESTS` with a `Retry-After` header (seconds) and is written to the security audit as `RATE_LIMITED` with the rule that fired (`details.rule`). The counts depend only on what the caller sent, never on whether an account exists.

| Rule | Limit |
|---|---|
| `login-ip` | 20 sign-in calls per address per 5 minutes |
| `login-account` | 5 failed sign-ins per account per 15 minutes, then a delay of 30 s that doubles with every further failure, up to 15 minutes; never a permanent lock; a successful sign-in clears it |
| `account-ip-*`, `account-email-*` | 5 calls per address and 5 per email per hour, each of register, resend-verification and forgot-password on its own |
| `token-ip` | 20 uses of a verification or reset link per address per hour |

Settings are under `lis.rate-limit.*`. `lis.rate-limit.store=memory` keeps the counters in the process (default; one instance); `redis` shares them between instances through `SPRING_DATA_REDIS_HOST`. If Redis cannot be reached the limits keep applying with counters in the process, a warning is logged and the metric `auth.ratelimit.fallback` counts the calls served that way; the metric `auth.ratelimit.hits{rule}` counts refusals. Behind a proxy set `LIS_FORWARD_HEADERS=framework` so the client address and the Secure flag of cookies come from `X-Forwarded-*` (default `none`: not trusted).

Registering a still pending address again replaces the earlier registration (the latest wins, earlier links are retired). Activation needs the link and the password together, so a stranger who registered an address first cannot get it activated by its owner clicking the link.
