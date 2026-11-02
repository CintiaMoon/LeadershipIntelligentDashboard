# Proposal D52 to D58: calibrating and managing scoring configuration

Status: **APPROVED on 2026-11-02 with the choices in section 6. C1 has started; C2 (backend) is done; C3 (screens with structured forms) is next.** `specv3.md` sections 23.5, 23.6, 30.6 and 42 already require a lifecycle, approval metadata, representative fixtures, a score-distribution preview and calibration artifacts before a configuration becomes active. Today the platform default changes only by editing a file and restarting (`lis.configuration.initial.mode=SYNC`), with a fixed approver, no endpoint, no screen, no preview and no fixtures. Under the precedence rule (specv3 > architect.md > frontend-spec.md > CLAUDE.md) a rule such as "a second person approves" is new business behaviour and needs your approval first.

## 1. What exists and what is missing

| Need (spec) | Today |
|---|---|
| Versioned, immutable ACTIVE configuration; lifecycle DRAFT, VALIDATING, ACTIVE, PAUSED, ARCHIVED (23.5, D33) | **Done** in `ConfigurationService`; protected by a database trigger. No endpoint and no screen call it, except the startup loader and questionnaire publishing |
| Approval metadata (23.6) | A name is recorded; the loader approves its own drafts as `initial-configuration`; nobody checks that approver and author differ |
| Validation reports all useful errors (30.6) | First problem only (D33 point 7, "improvement left for later") |
| Representative fixtures and a preview of results (23.6, 42.3 items 6 and 7) | Not implemented: "no spec defines them" (D33 point 9) |
| Calibration artifacts: dataset reference, methodology, reviewer, validation results, known limitations (42.2) | Not recorded. Every version is silently "unvalidated" |
| Change the platform default or a tenant override while the application runs | Not possible (limitation 1) |
| Content of the bundled initial configuration reviewed by someone who knows the organization (limitation 2) | **`docs/configuration/REVIEW_WORKSHEET.md` is ready for you** (generated from the file; 73 rows to confirm, change or disable) |

## 2. Proposed decisions

**D52. Who manages what.**
- `PLATFORM_ADMIN` manages the **platform default**, which affects every tenant.
- `OWNER` and `ADMIN` manage their own **tenant override** and the **organization overrides** of organizations in their token.
- Questionnaire (template) layers stay owned by the questionnaire builder; they are not edited here.
- `ANALYST` and the other roles can read the active configuration; a respondent cannot.
- A tenant can never see or change another tenant's layer, and the platform role still reaches nothing tenant-scoped (a foreign version looks like a missing one, as today).

**D53. Second-person approval ("four eyes").** A version is **approved by someone other than its author**, compared on the verified subject, with a written note and an effective date. A version moves DRAFT, then (author) "submit for approval" which runs the validation and the preview, then (a different authorized user) "approve" or "reject with a comment". The author cannot approve; rejecting returns the version to DRAFT with the comment. The rule is on by default (`lis.configuration.approval.require-second-person`, default `true`); the startup loader (system actor) and questionnaire publishing (a different domain, whose author is the administrator and whose checks are the publish rules) are not subject to it, and the guide says so. Consequence to accept: the platform default needs **two platform administrators**.

**D54. Validation reports every problem it can find.** The parser validates the document's sections independently (questions, signals, dimensions, patterns, penalties, imbalances, overall, responses, text) and reports the first problem of **each section** (up to about ten), instead of one problem in total, with the JSON path. This is a bounded improvement; reporting every problem within a section is not promised.

**D55. Preview and impact analysis (before approval).** "Preview" evaluates the draft merged over the layers above it against two sets, **in memory only**: (a) the saved fixtures (D56); (b) the answers of the caller's own tenant's most recent stored assessments (up to 50, chosen by the author; never another tenant's). A platform administrator belongs to no tenant, so a preview of the platform default by that role uses fixtures only; an owner or administrator previewing their tenant's override uses both. Nothing is persisted or logged; a preview is not a scoring run. The result is, per item, the overall score and the dimension scores before and after, the patterns and penalties that appear or disappear, and for the set a distribution (minimum, median, maximum, and the count in each band before and after). Only owners and administrators can preview against real assessments, the read is audited (action and count, never answers), and the screen shows ids and numbers, not answers. This is specv3 42.3 items 6 and 7 and the "penalty impact" and "sensitivity" artifacts of 42.

**D56. Fixtures (representative test cases) with a gate.** A fixture is a named set of answers with expectations: an allowed range for the overall score, optionally per dimension, and patterns that must or must not be detected. Fixtures belong to a configuration slot (the platform default, or a tenant). **Approval is refused while any fixture of the slot fails** with the draft applied. A starter set ships with the initial configuration (best answers, worst answers, a burnout profile, a balanced profile, a single-low-answer profile) and is checked in the release tests. A slot with no fixtures can still be approved, and the screen says so.

**D57. Calibration record on every approved version.** At approval the system stores the author, the approver, both notes, the effective date, the validation result, the preview summary (counts, distribution, no answers), the fixture results, and two free-text fields: **calibration reference** (the dataset, pilot or expert review the numbers were checked against) and **known limitations**. A version without a calibration reference is shown as **Unvalidated** wherever its configuration version is shown (it is today, silently). Stored in a new table (migration V10); the audit trail (`scoring_configuration_audit`) keeps recording every action.

**D58. API and screens.**
- API `/api/v1/configuration/...`: list the layers and versions the caller may manage; read a version; create a draft from the active version (a copy to edit); replace a draft's JSON; validate; preview; manage fixtures; submit for approval; approve; reject; pause, resume and archive; "start a new draft from this archived version" for rollback (an active version is never mutated, rollback is a new version).
- Screen **Configuration** (owners, administrators and platform administrators): the layers with their active version and an "Unvalidated" or calibration badge; a version page with a **JSON editor** (validation errors with their path), a **comparison with the active version** (what questions, patterns, penalties and settings changed, field by field), the **preview**, the fixtures and their results, and the approval step. A structured form editor for each section is a later improvement; the JSON editor is the minimum that is safe and complete.

## 3. Phases (each ends with a report and waits for your go-ahead)

| Phase | Deliverable | Needs |
|---|---|---|
| **C1. Review** | You review `REVIEW_WORKSHEET.md`; the JSON file is changed accordingly (a new version through the existing loader in `SYNC` mode). **Started 2026-11-02:** the 20 questions, the 17 signals, the dimension coverage and the 16 patterns are recorded as Keep in `docs/configuration/review-decisions.json` (no change to the file); the penalty deductions, caps and stacking, the overall weights and score bands, the advice text, the imbalance rule, the group-answer settings and the six combined questions are open, and no evidence or dataset was named | **You** |
| **C2. Backend** | V10 (review record, fixtures); multi-section validation; preview and impact analysis; fixtures and the gate; the approval workflow with the second-person rule; `/api/v1/configuration`; tests including tenant isolation, role matrix and "the author cannot approve" | D52 to D57 approved |
| **C3. Screens** | Configuration list, version page with editor, comparison, preview, fixtures and approval; role-aware navigation | C2 |
| **C4. Release** | Browser journey (draft, preview, submit, a second user approves, scoring uses the new version, history unchanged), Postman folder, release documents | C3 |

## 4. Risks and what I am not proposing

- **Platform-wide impact.** A platform default change affects every tenant at once. The second person, the fixtures gate, the preview and an effective date in the future are the guards; a tenant override is checked the same way but affects one tenant.
- **Two people are needed.** With one platform administrator the platform default cannot change in the product. Either appoint two or set the rule to `false` knowingly.
- **Multiple instances.** The configuration cache is per instance (limitation 16), so a newly approved version can be served stale for up to five minutes on another instance. Not solved here.
- **Previews read real answers in memory.** They stay inside the request, are never stored or logged, and are audited by action and count. If you do not want that, preview uses fixtures only.
- **Not proposed:** automatic calibration, learning from results, A/B comparison of live runs, changing history (a run keeps the layers it was scored with), or editing an active version in place.

## 5. Questions for you

1. **D52:** is it right that the platform administrator manages the platform default and an owner or administrator manages their tenant and organization overrides?
2. **D53:** second-person approval on by default, with two platform administrators needed for the platform default? Or one administrator for a start, with the rule off?
3. **D55:** may owners and administrators preview against the tenant's recent assessments (in memory, audited), or fixtures only?
4. **D56:** block approval while a fixture fails (recommended), or only warn?
5. **D58:** a JSON editor with validation, comparison and preview now (recommended), or wait for structured forms?
6. **C1:** will you review the worksheet yourself, or who should? I can apply your decisions to the JSON, but I cannot validate the numbers.

## 6. Decisions taken (2026-11-02)

| Question | Answer | Effect on the proposal |
|---|---|---|
| 1. Who manages what (D52) | Yes, as proposed | Platform administrator: the platform default. Owner and administrator: their tenant and organization overrides |
| 2. Second-person approval (D53) | **On** | `lis.configuration.approval.require-second-person` defaults to `true`; the platform default needs two platform administrators. Stricter than proposed: the approver must differ from the creator, from everyone who edited the version and from whoever submitted it |
| 3. Preview against real assessments (D55) | **Preview** | Owners and administrators may preview against their tenant's recent assessments, in memory and audited. Read as "yes to previewing against recent assessments"; `lis.configuration.preview.use-recent-assessments` (default `true`) switches it to fixtures only |
| 4. Fixtures (D56) | My recommendation | Approval is **refused** while any fixture of the slot fails |
| 5. Editor (D58) | **Wait for structured forms** | No JSON editor will ship. The API works on the whole configuration document, so structured forms can be built on it; the Configuration screens (C3) are planned with structured forms, and the comparison, preview, fixtures and approval screens do not depend on how a draft is edited |

## 7. C2 as built (2026-11-02)

- **Migration V10**: `configuration_review` (the calibration and approval record of a version, immutable once approved) and `configuration_fixture` (per layer, unique name).
- **D54**: `ConfigurationDocumentParser.diagnose` reports the first problem of each item and section (at most 50); `parse` still stops at the first, so every other use of it is unchanged. `ConfigurationService` submit, activate and the new `check` use it.
- **D55 and D56**: `ConfigurationEvaluator` scores answers against a candidate configuration in memory and checks fixture expectations; `EffectiveConfigurationService.resolveWithCandidate` merges a draft over the layers it sits on; `ConfigurationPreviewService` produces the preview (fixtures, recent assessments, a before/after distribution) and audits the read of assessments. Five starter fixtures ship beside the bundled configuration (`initial-platform-default.fixtures.json`) and are put in place once when the platform default has none; they hold for it (a test checks).
- **D53 and D57**: `ConfigurationReviewService` runs the workflow. The approver must differ from the creator, every editor and the submitter (read from the audit trail); approval is refused while a fixture fails; the record stores the note, the calibration reference and the known limitations.
- **D52 and D58**: `/api/v1/configuration/**` and `/api/v1/platform/configuration/**` (one abstract controller, two thin subclasses that build the caller from the verified token). Settings: `lis.configuration.approval.require-second-person` (default true), `lis.configuration.preview.use-recent-assessments` (default true) and `lis.configuration.preview.max-assessments` (default 50).
- **Found on the way:** a failed read inside a transactional call marks the surrounding transaction rollback-only even when the caller catches it, so the candidate resolver is declared not to roll back for scoring exceptions; and a refused approval simply rolls back and leaves the version awaiting approval (its ACTIVATION_FAILED audit row, if activation itself fails, rolls back with it).
- **Tests**: 46 new backend tests (686 in all): the all-problems diagnosis (9), starter fixtures and the evaluator (5), the workflow, gates, preview and isolation against PostgreSQL (19), the two switchable settings (2), the API with the role matrix (9), the loader's starter fixtures (2). The Postman collection has a folder for it (143 requests executed in all, 179 assertions, no failures, run twice).
- **Development identities added**: `platform-admin-2` and `assess-owner` (so a second person exists for the platform default and for the scratch organization).

