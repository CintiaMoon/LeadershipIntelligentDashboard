# Proposal D45: response scopes (member, team, organization)

Status: **APPROVED.** All defaults were accepted; the participation factor is `min(1, respondents / fullConfidenceResponses)` with `fullConfidenceResponses` default 10 (unvalidated), using the lowest respondent count among the questions supporting a pattern's evidence. The wording below was applied to `specv3.md` as sections 4.10 to 4.13 and 14.2.1 (section 4 numbers in this document refer to the proposal, not the final spec). `specv3.md` has no text on respondent counts, participation, anonymity thresholds, teams or aggregating several answers, so this changes business behavior and needs approval first (precedence rule: specv3 > architect.md > frontend-spec.md > CLAUDE.md).

## 1. Problem

`POST /calculate` accepts one answer per question. The engine cannot tell whether that answer is one person's, a team's or an organization-wide figure, nor how many people it represents.

## 2. Decisions already taken by the user

| # | Decision |
|---|---|
| 1 | Member is an **input scope only**. The engine never outputs, ranks or drills into an individual result and stores no respondent identity (keeps specv3 2.3 and 2.4). |
| 2 | Outputs are one LIS per **team** and one per **organization**, from the same configuration and pipeline. |
| 3 | Several answers to one question are combined by a method set **per question** in the versioned configuration (MEAN or MEDIAN, default MEDIAN). |
| 4 | A **minimum response count** (default 5, configurable). Below it the question is treated as missing; a unit with too little data is INSUFFICIENT_DATA with no score. |
| 5 | Mixing scopes for one question inside one scoring unit is **rejected** with a 422. |
| 6 | The respondent count feeds **confidence**. |

## 3. Choices still to approve (defaults proposed, all unvalidated)

| # | Question | Proposed default |
|---|---|---|
| a | How is a text/flag signal derived when several responses exist? | Signal is present when at least a configured share of respondents carry it (default 0.5) |
| b | How is the organization LIS computed when teams exist? | Pool all responses and apply the same rules (not an average of team results) |
| c | How is a respondent count on a pre-aggregated TEAM or ORGANIZATION answer treated? | Optional and caller-declared; the trace marks it "declared" and the minimum applies if given; if absent the trace says "participation unknown" and the minimum is not checked |
| d | How does participation change confidence? | A recorded factor comparing respondent count with the minimum. The exact formula and bands need your input; I will not invent calibrated values. Proposal: add one factor named `participation` in the existing confidence breakdown, with weight set in configuration |
| e | Are team ids registered anywhere? | No. A team id is an opaque, format-validated string inside the organization; no team table, name or provisioning |

## 4. Proposed specv3 amendment (text for approval, not applied)

> **4.5 Response scope.** Each answer carries a scope: `MEMBER`, `TEAM` or `ORGANIZATION`. A `MEMBER` answer represents one respondent whose identity is not part of the model. A `TEAM` or `ORGANIZATION` answer is a figure already combined by the caller and may declare how many respondents it represents. A team is an opaque identifier within an organization.
>
> **4.6 Aggregation.** All answers to one question within one scoring unit (the organization, or one team) are normalized individually and combined into one normalized value by the configured method (`MEAN` or `MEDIAN`). A question may have answers of one scope only within a unit; otherwise the request is rejected with `MIXED_RESPONSE_SCOPES`.
>
> **4.7 Minimum responses.** A configuration defines a minimum number of responses. When a question has fewer, it is treated as missing under its missing-data policy (Section 8.10); it is never treated as zero. A unit whose coverage is then insufficient is INSUFFICIENT_DATA and has no score. No result, evidence or answer view may single out an individual respondent.
>
> **4.8 Results by scope.** A calculation produces one result for the organization and one for each team that has sufficient data. Results are compared only within the same scope.
>
> **13.x Confidence.** Respondent count against the configured minimum is recorded as the `participation` factor of pattern confidence. Runs without respondent information keep the earlier confidence algorithm.

## 5. Effect on what exists

- **Backward compatible:** requests with only `inputs[]` are ORGANIZATION-scope with unknown participation. Their input hash (`input-hash-v1`), results and the 460 existing tests are unchanged.
- **New:** `responses[]` request form, aggregation stage, Flyway V6 (answer scope columns, run scope columns), a `responses` configuration block (part of the configuration hash), a trace version bump with `responseSummaries`, scope filters on history, dashboard, trends and compare, and a small frontend scope switcher.
- **Reproducibility:** old runs reproduce exactly; new-style runs record method, count and minimum.
- **Privacy:** members are stored as ordinals only, the source-answer endpoint returns counts and sorted values per question (no per-member order), and teams below the minimum produce no result.

## 6. Phases after approval

B domain and configuration; C persistence and API; D frontend and release documents. I stop after each for your go-ahead.

## 7. Status

| Phase | State |
|---|---|
| A decision and spec amendment | done |
| B domain and configuration | **done**: `ScopedResponse`, `ResponseSummary`, `ParticipationInfo`, `ResponseSettings`, `ResponseAggregationService`, `ScoringOrchestrator.scoreResponses`, confidence algorithm version 2, the `responses` block and per-question `aggregation` in the configuration document, error codes `MIXED_RESPONSE_SCOPES`, `DUPLICATE_TEAM_SCOPE`, `INVALID_RESPONSE_SCOPE`, `INVALID_RESPONSE_CONFIGURATION`. 39 new unit tests. |
| C persistence and API | **done**: Flyway V6 (answer scope columns; run `scope_type`, `scope_id`, `parent_run_id`, `response_summaries`), `responses[]` in `/calculate` (exactly one of `inputs` or `responses`), one organization run plus a run per team in one transaction, scoped input hash (`input-hash-v2`; earlier requests keep `input-hash-v1`), `teamRuns` and `responses` in the result, team runs readable like any run, history/trends/dashboard/latest/compare organization-level only, source answers grouped for members and suppressed below the minimum, dev sample `responses` block, Postman requests. Backend 513 tests (460 before D45); newman 42 requests, 44 assertions, 0 failures; browser tests 45 of 45. |
| D frontend and release documents | **done**: scope switcher, how-the-answers-were-combined panel, team runs in the explanation and dimension views, member answers as groups, a clear no-score message for a team below the minimum, the participation factor in the pattern detail, a sandbox login persona, release documents updated. Frontend 208 unit tests, 49 browser tests (twice in a row). |

Decisions made while building phase C (all within the approved D45):

- **Where the summaries live.** The response summaries are stored in a `response_summaries` column of the run and returned as `responses`, not inside the calculation trace. The trace schema is unchanged, so every stored trace and the frontend trace parser stay valid. The participation factor is inside each pattern's stored `confidenceBreakdown`.
- **A `REQUIRED` question with too few team answers.** The team cannot be scored. The request does not fail: the organization is scored and the team is listed in `teamRuns` as INSUFFICIENT_DATA with no run id and no score. The organization run keeps their ids in `responses.unscoredTeams`, so every read of it lists such a team without a run.
- **Teams below the minimum for non-required questions** get a stored INSUFFICIENT_DATA run, with the respondent counts in `responses`, so the interface can explain why.
- **Validation error shape.** A request with neither or both answer lists is a 400 reported on `details.inputs`, as an empty request was before.

Not done: a team filter on history and trends (team runs are reachable by id from `teamRuns`; no team trend over time exists yet). Listed as limitation 30.
