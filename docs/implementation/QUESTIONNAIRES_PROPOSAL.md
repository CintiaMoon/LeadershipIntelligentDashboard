# Proposal D47 to D51: questionnaires, the builder, assessment runs and the respondent flow

Status: **APPROVED.** The wording of section 4 was applied to `specv3.md` as sections 4.2.1, 4.14 and an addition to 23.3. The product name shown in the interface is **"Leadership Intelligent Dashboard"** (the images say "Helm"; the name is one constant). The open points of section 6 are accepted and recorded as limitations at release. `specv3.md` defines questions, normalization and configuration layers, but not a builder, runs, a roster or a respondent flow, and it has no multi-select question type. Under the precedence rule (specv3 > architect.md > frontend-spec.md > CLAUDE.md) that is new business behavior and needs approval first.

## 1. Problem

Questions are fixed in the platform configuration JSON and answers arrive only through `POST /calculate`. Administrators cannot author a questionnaire, and team members cannot complete one. The screens in `specs/screens/` (a questionnaire library and a builder) are the baseline.

## 2. Decisions already taken by the user

| # | Decision |
|---|---|
| 1 | Build the **full builder**, plus assessment runs and the respondent flow. |
| 2 | **Anonymity:** a draft is linked to a member only until submit; on submit the answers are stored with the team only, and a separate completion record says who finished. "Anonymous responses" is always on. |
| 3 | Respondents sign in through the identity provider with a new **`RESPONDENT`** role. Team membership comes from a **roster of people**. |
| 4 | The **administrator closes** a run, which scores it once. |
| 5 | **Multiple choice is scored** (mean of the selected options' values). **Free text is collected, not scored.** |
| 6 | **Restyle the whole application** like the images (dark, left sidebar, "Helm"). |
| 7 | Administrators **author patterns and penalties** in the builder. |

## 3. Decisions D47 to D51 (the design that follows from them)

### D47. A questionnaire is a template layer of the configuration
A published questionnaire is an ACTIVE `TEMPLATE` configuration layer of the tenant (`ConfigurationScope.TEMPLATE`, which the engine already merges between the platform default and the organization). A generator turns the builder document into a complete layer: `questions`, `signals`, `dimensions.mappings`, `patterns`, `penalties`, `imbalances`, `overall`, `responses`, `recommendations`, `presentation`. It is validated by the same parser and lifecycle as any configuration (draft, validate, activate with an approval record); there is no second validator. A published version is immutable; republishing creates a new version and archives the previous one. Template ids of questionnaires are generated (the `assessment_template.id` namespace is global across tenants).

Mapping from the builder's question types:

| Builder type | Engine | Notes |
|---|---|---|
| Single choice | `MULTIPLE_CHOICE` | every option has a 0 to 100 score, default evenly spaced in the order shown |
| Rating (1 to 5) | `SCALE` 1 to 5 | switch "higher is better / worse" |
| Likert scale | `SCALE` with 5 or 7 points and labels | same switch |
| NPS | `SCALE` 0 to 10 | |
| Numeric | `NUMBER` min, max, direction | out-of-range answers are clamped and recorded |
| Yes / No | `BOOLEAN` | which answer is the desirable one is a switch |
| Multiple choice | **new `MULTI_SELECT`** | D49 |
| Free text | `TEXT` | collected, not mapped to a dimension, never scored |

A scored question has one of the four dimensions and a weight (default 1). Each scored question automatically gets a signal named after it, so patterns refer to questions. "Required" is enforced by the respondent flow, not by the engine: the engine's missing-data policy stays `IGNORE`, because `REQUIRED` would fail a whole run for one blank answer. **Publish is refused** unless every dimension has at least one scored question, every option has a score, option scores are within 0 to 100, and the generated layer passes the parser.

**Layer replacement.** `ConfigurationMerger` replaces arrays wholesale but merges objects key by key. A questionnaire replaces the platform's questions, so stale platform ids in object sections such as `presentation` would fail validation, and a later platform change could silently break a published questionnaire. D47 adds one explicit marker, `{"$replace": X}`: the value replaces whatever lower layers had for that key (the explicit semantics specv3 23.3 asks for, next to the existing `{"$remove": true}`).

### D48. Runs, roster, respondents and anonymity
- A **team** belongs to an organization (id, name). Answers still carry only the team id (D45).
- A **person** is a roster entry: name, email, one team, active or not. The roster is for OWNER and ADMIN only.
- An **assessment run** pins a questionnaire version and its configuration version, an organization, the chosen teams and an optional due date. Starting it snapshots one **participant** per active person of those teams with a status (`NOT_STARTED`, `IN_PROGRESS`, `SUBMITTED`). This completion record never holds answers.
- A respondent is matched to the roster by the **`email` claim** of the verified token (and `tenant_id`); the role is `RESPONDENT`.
- **Drafts** (answers so far) are linked to the participant so they can resume, in the same session or later. They are deleted on submit and on close.
- **Submit** is one transaction: the answers are written as `run_response` rows with a random id, the team and the question, and **no participant reference and no timestamp**; the drafts are deleted; the participant becomes `SUBMITTED`. No order is stored, so submission order cannot re-identify anyone.
- **Close** (administrator only) scores the run once: the responses become `MEMBER` answers per team and go through the existing scoring, using the pinned configuration version, so republishing a questionnaire cannot change an open run. One organization result and one result per team with enough answers are produced; teams under the minimum get no score.
- `RESPONDENT` is **not** a value of the tenant `Role` enum, for the reason D46 gives for `PLATFORM_ADMIN`: the rules that let "every role" read tenant data are built from that enum. `/api/v1/respond/**` has its own rule, and a respondent reaches nothing else.

What the design does and does not protect: scores of a small team are not produced (minimum responses), member answers are never listed one by one, and the answers are not linked to a person after submit. It cannot hide **who has not finished** from an administrator (that is the point of the completion record), so in a very small team the administrator can guess who is missing. The interface says so.

### D49. Multiple choice is scored: `MULTI_SELECT`
New question type and normalization strategy (specv3 4.2 allows additional types through extensible strategies). Each option has a 0 to 100 value; the normalized value is the **mean of the values of the selected options**; no selection is missing data (never zero). The answer is a **list of option keys**, so the engine and its edges accept list-valued answers: input hash (order-independent), stored answers, the request body, the answers endpoint and the frontend types. A member-level list is normalized per member and then combined by the existing aggregation, like every other type. Results for one answer per question are unchanged.

### D50. Authored patterns and penalties
The builder stores conditions over question signals (AND, OR, NOT and the existing operators), a severity, evidence minimums, and penalties (fixed or severity-based amount, target, category, stacking policy, cap), with names, explanations and recommendation text. Validation is the existing condition validator and snapshot build. Authored ids are generated per questionnaire (`X-1`, `X-2`...), and the catalog identifiers of the specification (`TH-*`, `DS-*`...) are **not** reused for authored rules (stable-ID rule). The builder says that authored thresholds and deductions are unvalidated. "Start from the initial questionnaire" copies the platform default into a builder draft.

### D51. Restyle
New design tokens (dark by default, light kept behind a toggle so contrast can be checked in both), a left sidebar (Dashboard, Questionnaires, Assessment runs, People, and Tenants for the platform administrator; no links to features that do not exist), the brand "Helm, Leadership Intelligence" from the images as one constant that is easy to change, and a chart palette that works on dark. Every existing screen, unit test, browser test and accessibility check is re-verified. This changes the look of the whole product and nothing about what it computes.

## 4. Proposed `specv3.md` wording (not applied)

> **4.2 (addition) Multi-select.** `MULTI_SELECT` is a question type. Its answer is a set of option identifiers. Each option has a normalized value in [0, 100]. The normalized value of an answer is the arithmetic mean of the values of the selected options. An empty selection is missing data. Unknown options are handled by the question's unknown-option policy.
>
> **23.3 (addition) Replacement.** An object `{"$replace": X}` in a layer replaces the value of that key in every lower layer with `X`, instead of merging. It is the counterpart of the deletion tombstone and is the only way a layer replaces an object section.
>
> **New section: Questionnaires, runs and respondents.**
> A *questionnaire* is a versioned set of questions with their scoring configuration, published as a template layer of one tenant; a published version is immutable. An *assessment run* pins one published version for one organization and a set of teams and is *open* or *closed*. A *participant* is a person of the roster who is expected to answer; the participant record states whether the person has finished and never contains answers. A respondent's unfinished answers (a *draft*) may be linked to the participant until submit. On submit the answers are stored with the team only: they carry no reference to the participant, no timestamp and no order, and the draft is deleted. Closing a run scores the stored answers once as member answers (4.10 to 4.13). Answers are never returned one by one or linked to a person. Free-text answers are collected and never scored.

## 5. Interfaces (sketch, to be fixed in phase 2 and 3)

| Area | Endpoints | Who |
|---|---|---|
| Questionnaires | `GET/POST /api/v1/questionnaires`, `GET/PUT /{id}` (draft), `POST /{id}/publish`, `/archive`, `/duplicate`, `POST /from-initial` | OWNER, ADMIN |
| Roster | `GET/POST /api/v1/teams`, `GET/POST/PUT /api/v1/people` | OWNER, ADMIN |
| Runs | `GET/POST /api/v1/runs`, `GET /{id}` (completion per team, who is missing), `POST /{id}/close` | OWNER, ADMIN |
| Respondent | `GET /api/v1/respond/runs`, `GET /respond/runs/{id}` (questions and my draft), `PUT /respond/runs/{id}/draft`, `POST /respond/runs/{id}/submit` | RESPONDENT only |

Migrations: V8 (questionnaire library), V9 (teams, people, runs, participants, drafts, responses). The token contract gains the `RESPONDENT` role and requires the `email` claim for respondents.

## 6. Open points to confirm later (not blocking phase 0)

- **Retention of roster data** (names, emails) and what happens when a person leaves; recorded as a limitation.
- **The brand name.** Decided: "Leadership Intelligent Dashboard", one constant to change later.
- **Reminders.** There is no e-mail; the administrator sees who has not finished.
- **Calibration.** Everything authored in the builder is unvalidated by construction.

## 7. Phases

0 decisions and wording (this document), 1 engine (list-valued answers, `MULTI_SELECT`, `$replace`), 2 questionnaire backend, 3 roster, runs and respondent backend, 4 restyle, 5 new screens, 6 end-to-end, release documents. Each ends with a report and waits for your go-ahead.

## 8. Status

| Phase | State |
|---|---|
| 0 decisions and wording | **done**, approved |
| 1 engine | **done**: `MULTI_SELECT` (config, strategy, parser), list-valued answers end to end (input hash, stored form, request limits, answers view, frontend display), empty selection is missing data, `{"$replace": X}` in the configuration merger. 49 new tests; backend 585 tests, frontend 252. Answers with one value per question score exactly as before. |
| 2 questionnaire backend | **done**: V8 (`questionnaire`, `questionnaire_version`, immutability trigger), builder document, normalizer, validator (publish checks of specv3 4.14), generator (builder document to template layer), publish through the ordinary configuration lifecycle in one transaction, `/api/v1/questionnaires` (list, get, create, save draft, publish, archive, duplicate, from-initial; OWNER and ADMIN only). 40 new tests; backend 625 tests. Choices below. |
| 3 roster, runs and respondent backend | **done**: V9 (`team`, `person`, `assessment_run`, `assessment_run_team`, `run_participant`, `run_draft`, `run_response`), `/api/v1/teams`, `/people`, `/runs` (OWNER and ADMIN) and `/api/v1/respond/**` (RESPONDENT only), `CalculateCommand` takes an optional explicit configuration version. 15 new tests; backend 640 tests. Choices below. |
| 4 restyle | **done**: dark default and light behind a toggle, left sidebar and top bar, brand constant, theme-aware chart palette. Frontend 268 unit tests, 59 browser tests (axe in both themes). Choices below. |
| 5 new screens | **done**: questionnaire library, new-questionnaire page and builder (questions, rules with a nested condition editor, summary and publish checks), people (teams and roster), assessment runs (list, start, detail with close), and the respondent flow (my assessments, answer form with drafts). Frontend 391 unit tests; 67 browser tests, with every new screen scanned by axe in both themes and checked on a phone. Choices below. |
| 6 end to end and release | **done**: browser journey (`frontend/e2e/assessments.spec.ts`), a Postman folder for the new endpoints (81 requests in the collection, 109 executed, 126 assertions, 0 failures), and the release documents (`docs/release/`: implementation report, test results, traceability, known limitations 37 to 48, deployment guide with the respondent role and e-mail claim). |


## 9. Phase 2 choices (for review)

- **The questionnaire id is the template id.** It is generated (`q-` and 12 hex characters), because `assessment_template.id` is one namespace for all tenants. Publishing registers the template and its version, so assessments of a questionnaire need no other setup. `POST /calculate` with `templateId` = the questionnaire id scores with its layer.
- **Free text is not in the scoring layer.** The engine's `TEXT` type needs keyword flag rules and cannot be mapped to a dimension, which is not what "collected, never scored" means. Free-text questions live only in the questionnaire document; the respondent flow (phase 3) collects them and the close step will not send them to scoring. Every other question, scored or not, is a configuration question with a signal `SIG_<question id>` whose value is the normalized 0 to 100 answer.
- **Option scores.** Unscored options are spaced evenly in the order shown, **first option 0, last option 100**. If the intended default is the opposite, it is one line in `QuestionnaireGenerator.score`. The `SEVERITY_BASED` multipliers of an authored penalty are fixed at LOW 0, MEDIUM 0.4, HIGH 0.7, CRITICAL 1.0, the values of the platform default. All of these are unvalidated authoring defaults.
- **What the layer contains and inherits.** It replaces questions, signals, dimension mappings, patterns, penalties, imbalances (none) and recommendations as lists, and replaces the question, signal, pattern and penalty display sections with `{"$replace": ...}`. It inherits the dimension settings (required coverage), overall weights and interpretation, response settings and the dimension display names from the platform default, so a platform change to those reaches every questionnaire. The builder has no imbalance rules.
- **Publish is one transaction** through `ConfigurationService` (create draft, validate, activate with the publisher as approver). Any failure, including a parser rejection of the generated layer, leaves no configuration version behind.
- **Archive hides, it does not deactivate.** An archived questionnaire cannot be edited or published; its layer stays active so past assessments remain explainable and recalculable. Phase 3 refuses new runs on an archived questionnaire.
- **Authored rule limits.** Conditions compare a question's normalized value or a raw dimension score (EQ, NE, GT, GTE, LT, LTE, BETWEEN, IN, NOT_IN, EXISTS, NOT_EXISTS, with AND, OR, NOT). A rule has at most one penalty (FIXED or SEVERITY_BASED; STACK, MAX, MAX_PER_CATEGORY, PRIORITY or CAPPED_STACK, never EXCLUSIVE_GROUP) and one recommendation.
- **From the initial questionnaire** copies the configuration that applies to the tenant (platform default plus the tenant override). Text-derived flags, gap and contains conditions, other penalty strategies, extra penalties and actions of one pattern, imbalance rules and missing-data policies are left out and listed in `notes` of the response (shown once, not stored).
- **Not done in phase 2:** `required` is stored and returned but only the respondent flow (phase 3) enforces it; there is no un-archive; no endpoint lists the published layer's configuration version beyond its id in `versions`.

## 10. Phase 3 choices (for review)

- **Anonymity as built.** `run_response` has the columns `id` (random), `run_id`, `tenant_id`, `team_id`, `question_id`, `answer` and nothing else; a test asserts that list. Submit is one transaction: responses written, draft deleted, participant marked finished. Residual limits, stated rather than hidden: the heap order of rows in PostgreSQL follows insertion order (no API exposes it and the reads sort by team, question and answer), and the completion list tells an administrator who has not finished, which in a team smaller than the minimum number of responses can point to a person. The run detail carries that notice.
- **Respondent identity.** Role `RESPONDENT` (not in the `Role` enum), roster match by the token `email` claim in the token tenant, one person per e-mail per tenant. A respondent token without an e-mail identifies nobody (403). A respondent reaches `/api/v1/respond/**` and nothing else; the tenant roles do not reach it.
- **Pinning.** A run stores the questionnaire version and the configuration version of the layer published at start. At close, the ordinary configuration resolution is used when that layer is still the active one; otherwise the pinned version is selected explicitly (`configurationSelection` EXPLICIT, as for recalculation by version), which merges it over the current platform default and skips tenant and organization overrides. Respondents are shown the pinned version of the questions.
- **What is scored.** Every stored answer of a non-free-text question, as a MEMBER answer of its team. Teams under the minimum number of responses get a run without a score (INSUFFICIENT_DATA), exactly as before. Closing with no scorable answer is refused (409). A failed scoring rolls the close back and leaves the run open; the idempotency key `run-close-<id>` makes a retry safe.
- **Free text** is stored in `run_response` and kept after the close. Nothing reads it yet: showing free-text comments anonymously needs its own decision (minimum group size, no attribution).
- **Required** is enforced at submit; an answer is checked against its question when it is given (ratings 1 to 5, Likert 1 to its points, scores 0 to 10, a known option, distinct known options, a boolean, a finite number, text up to 4000 characters), so a stored answer can always be scored.
- **Roster.** Teams cannot be deleted or archived and people are deactivated, never deleted. A person who moves team or is deactivated after a run started stays a participant of that run (the snapshot). Reading the roster and a run participant list are audited by action and count, never by name or address.
- **Not done in phase 3:** no reminders or e-mail, no reopening of a closed run, no change of the teams of a started run, no paging of the lists, no bulk import of people.

## 11. Phase 4 choices (for review)

- **Tokens only.** Every colour is a token in `frontend/src/index.css` (`@theme` holds the dark values, `:root[data-theme="light"]` overrides them, and chart colours are `--chart-*` custom properties that `palette.ts` refers to by name, so charts follow the theme without redrawing). A unit test fails the build when a component writes a colour literal or a stock palette class, when the light theme lacks a token the dark one has, or when any text and background pair drops below 4.5:1 (chart lines below 3:1).
- **That test found a defect in the old light palette:** the amber chart colour was 2.59:1 on white. It is now `#a87a0a` (3.85:1), and the grey for low severity `#71829a` (3.92:1).
- **Theme.** Dark is the default. The choice is a per-browser convenience stored in `localStorage` (guarded, so a browser that refuses storage still works for the page). It is applied before the first render in `main.tsx`; an inline script would be blocked by the content security policy, so a returning light-theme user can see one frame of dark.
- **Brand.** `frontend/src/brand.ts` holds the short name "Helm", the line "Leadership Intelligence" and the product name "Leadership Intelligent Dashboard" (document title and sign-in heading). The dashboard still has its own heading "Leadership Intelligence".
- **Layout.** Sidebar (brand and navigation) plus a top bar (organization, roles, theme toggle, sign out). On a phone the sidebar becomes a header with the navigation on its own row. The sidebar lists only screens that exist (Dashboard and Compare, or Tenants for the platform administrator); phase 5 adds Questionnaires, Assessment runs and People through `app/layouts/nav.ts`.
- **Left out of the mock-ups on purpose:** the global search box, the notification bell, the "AI credits" meter, the Reports and Settings entries and the tenant chip have no feature behind them. Headings use the system serif stack, because loading a web font would need a font origin the content security policy does not allow.
- **Primary buttons** are the inverse of the page (light on dark, dark on light), as in the mock-ups; the accent colour marks selection, status and focus.

## 12. Phase 5 choices (for review)

- **Routes.** `/questionnaires`, `/questionnaires/new`, `/questionnaires/:id`, `/people`, `/assessment-runs` (`/new`, `/:runId`), and for respondents `/respond` and `/respond/:runId`. Assessment runs live under `/assessment-runs` because `/runs/...` already means a stored scoring result. Owners and administrators reach the first group, respondents only the second; a screen a role may not use shows that it is not for them, and the backend refuses it as well.
- **Navigation by role** is one list in `app/layouts/nav.ts`: owners and administrators see Dashboard, Compare, Questionnaires, Assessment runs and People; other tenant roles see Dashboard and Compare; a respondent sees My assessments and no organization selector; the platform administrator sees Tenants.
- **The builder shows what the server says.** It keeps only the unsaved edits and otherwise shows the server copy. "Before you publish" lists the server's checks of the last saved draft (with a hint when there are unsaved changes), and a refused publish lists every problem. Publish saves unsaved changes first. A published version is shown read only and "Edit as a new draft" opens the next one.
- **Rules.** The condition editor builds all-of, any-of and none-of groups (to a depth of four) around comparisons of a question (0 to 100, 100 desirable) or of a dimension's raw score. A rule can lower one dimension or the overall score by a fixed amount or an amount scaled by severity, with one of five stacking choices, and can recommend one action. The page says that thresholds and amounts are the author's own and not calibrated.
- **The respondent form** shows no scoring detail (no dimension, weight, direction or scores); the backend does not send any. Required questions are checked in the browser before submitting and again by the server. "Save draft" is explicit; there is no autosave. After submitting, the answers are not shown again.
- **Development personas added** to the mock identity provider: `respondent-1` to `respondent-6` (e-mail `respondentN@demo.test`, role RESPONDENT; `respondent-1` is in the sign-in list) and `assess-admin`, an administrator of the scratch organization `org-assess`. The browser journey runs in `org-assess`, because the other browser tests rely on `org-1` and `org-2` holding only the seeded series (a run closed there changes the dashboard they read).
- **Found and fixed on the way:** the builder overflowed a 390 px screen by 69 px (fieldsets and the single-column grid did not shrink); the browser suite caught it, not the unit tests.
- **Not done:** no autosave, no undo, no drag-and-drop ordering (up and down buttons instead), no preview of the respondent's view from the builder, no table of question counts in the library (the list endpoint does not return them), no paging of the lists, no screen for the answers of a run beyond the scored result.
