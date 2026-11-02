# Proposal D59 to D64: reports (resolves F13)

Status: **APPROVED on 2026-11-02 as written, with the defaults for the open questions of section 4: OpenPDF for PDF, no stored reports and no report list, every role that can read a run may generate its report. D1 (contract documents) is done; D2 (backend, JSON and CSV) is built; D3 (PDF) is next.** `specv3.md` does not define reports (decision F13); `frontend-spec.md` section 14 only suggests five report types and says generation and export "must use backend endpoints when available" and that the frontend "must not independently generate a different scoring interpretation". Under the precedence rule this is new behaviour and needs your approval first. Nothing is built yet.

Your choices so far: report types Leadership Intelligence, Historical Comparison, Dimension Analysis and Pattern and Penalty (Risk); generated on demand and not stored; export as PDF and CSV.

## 1. Principles

- A report is a **rendering of stored facts**. It never scores, never recalculates and never infers evidence; it reads the immutable scoring run, its explanation and its catalog text (the same sources as the existing read endpoints, D37 to D39). Same run, same report content.
- It carries the **methodology block** required by the frontend outline: engine version, algorithm version, configuration version and hash, rounding policy, and the calibration status (an unvalidated configuration says so, D57).
- It contains **no answers and no per-member data** (decision D45 and D42). Group results are shown only at the organization and team levels the run already stores.
- An `INSUFFICIENT_DATA` or `PARTIAL` run is reported as such, with no invented number.

## 2. Proposed decisions

**D59. Four report types** (the Organizational Risk report of the frontend outline is merged into Pattern and Penalty, since both read the same facts):

| Type | Content | Input |
|---|---|---|
| `LEADERSHIP_INTELLIGENCE` | Organization, assessment period, LIS and band, dimension scores (raw and adjusted), patterns, applied penalties, imbalances, key evidence, recommendations, methodology | one run |
| `DIMENSION_ANALYSIS` | Per dimension: raw and adjusted score, status, and the contributions (question, normalized value, weight, contribution) | one run |
| `PATTERN_PENALTY` | Every pattern with severity, confidence and evidence; every penalty with its terminal state (APPLIED, SUPPRESSED, INELIGIBLE, NOT_TRIGGERED) and the raw, effective and suppressed amounts | one run |
| `HISTORICAL_COMPARISON` | Two runs side by side: LIS and dimension deltas, patterns and penalties that appear or disappear, and whether the runs are comparable (same configuration version or not, as the existing compare endpoint states) | two runs |

**D60. Generated on demand, nothing stored.** `POST /api/v1/reports` is replaced by a read-style request, so no report table, retention rule or per-report access control is needed:

```http
GET /api/v1/reports/{type}?runId=...&format=json|csv|pdf          (comparison: baseRunId=...&runId=...)
```

`json` returns the structured report (the screen renders it, so screen and exports cannot disagree), `csv` and `pdf` return the same data as a download. `GET /reports` as a list is **not** built: there is nothing stored to list; the screen offers the four types and a run picker. This departs from the two endpoints in the frontend outline (`GET` and `POST /reports`); the outline said "when available", and the API contract is updated.

**D61. Access.** Same scoping as the run reads: tenant from the token, organization in `org_ids`, a foreign run looks like a missing one (404). Roles that can read a run can produce its report, except that the **comparison and CSV of contributions are not restricted further**, because they contain scores only. `PLATFORM_ADMIN` and `RESPONDENT` reach nothing. Every generation is written to the access audit (action, type, run id, format; no content).

**D62. Formats.**
- **CSV**: one block per section (heading, column names, rows, an empty line), UTF-8 with a byte-order mark for spreadsheet use, formula-injection safe (a cell starting with `=`, `+`, `-` or `@` is prefixed with a quote), RFC 4180 quoting. Built with no new dependency.
- **PDF**: a plain, accessible, single-column document (title, headings, tables; document language and title set). This needs a PDF library. **Proposal: OpenPDF (LGPL/MPL), pinned to an exact version and checked by the dependency scan.** Alternative with no new dependency: the screen is print-styled and the browser saves it as PDF, and the server serves only `json` and `csv`. Decision for you in section 4.

**D63. Screen.** `/reports` (Reports in the navigation): choose the type, the organization, the run (and a second run for the comparison), see the report on the page, and download PDF or CSV. The page shows the same text and numbers the exports contain and nothing the backend did not return. Light and dark, keyboard and axe checks as for the other screens.

**D64. Determinism and tests.** The report is a pure function of the stored run and the catalog text. Tests: a golden structured report for a fixed run (byte-identical on replay), CSV escaping and formula-injection cases, PDF opens and contains the headline numbers (text extraction), tenant isolation (a foreign run is 404 for every type and format), `INSUFFICIENT_DATA` and a run with suppressed penalties, comparison of runs on different configuration versions, audit rows written, no answer text anywhere in any format. Postman collection and API contract updated.

## 3. Phases (each ends with a report and waits for your go-ahead)

- **D1**: this proposal approved; `API_CONTRACT.md`, traceability and the F13 entries updated.
- **D2**: backend: report model, the four builders, JSON and CSV, audit, tests.
- **D3**: PDF (if approved).
- **D4**: Reports screen, browser journey, release documents.

## 4. Questions for you

1. **PDF**: add OpenPDF to the server, or use the browser's print-to-PDF and ship only JSON and CSV?
2. **No stored reports and no report list** (D60): confirm, or say if you need an archive of issued reports.
3. **Who may generate**: every role that can read a run (proposed), or owners, administrators and analysts only?
4. Anything the four report types should show that is missing, or should not show?
