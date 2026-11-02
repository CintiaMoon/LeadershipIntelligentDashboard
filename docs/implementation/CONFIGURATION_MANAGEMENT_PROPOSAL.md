# Proposal D52 to D58: calibrating and managing scoring configuration

Status: **PROPOSED, not approved. Nothing is implemented.** `specv3.md` sections 23.5, 23.6, 30.6 and 42 already require a lifecycle, approval metadata, representative fixtures, a score-distribution preview and calibration artifacts before a configuration becomes active. Today the platform default changes only by editing a file and restarting (`lis.configuration.initial.mode=SYNC`), with a fixed approver, no endpoint, no screen, no preview and no fixtures. Under the precedence rule (specv3 > architect.md > frontend-spec.md > CLAUDE.md) a rule such as "a second person approves" is new business behaviour and needs your approval first.

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
