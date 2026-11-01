# Known limitations

Ordered by how much they matter for going live. Nothing here is hidden elsewhere in the product: where the interface cannot show something, it says so.

## Blocking for a production release

1. **The configuration is a file read at startup; there is no way to change it while the application runs.** The platform configuration is one JSON document (`docs/configuration/INITIAL_CONFIGURATION.md`): the bundled initial one, or a file named by `lis.configuration.initial.location`. It is validated and activated at startup, and a changed file becomes a new version when started with `lis.configuration.initial.mode=SYNC`. There is no endpoint for it, no loading of tenant, template or organization overrides from files, and no approval step with a second person (the startup records the approver `initial-configuration`). The service, validation, lifecycle, immutability and audit exist and can be called from a controlled job.
2. **The initial configuration is a proposal, not validated content.** It has 20 questions, 17 signals, 16 patterns with their penalties, one imbalance rule, recommendation and presentation text, and the rules for combining answers from several people. Identifiers and suggested maximum deductions follow the specification's catalog, but the questionnaire, the thresholds, the severities, the evidence requirements, the stacking caps and all wording were written for this version and are not empirically calibrated (decisions D4, D29, D43; specv3 section 42). The list of choices to review first is in `docs/configuration/INITIAL_CONFIGURATION.md`. The dev profile still uses the separate illustrative sample.
3. **No real identity provider is wired.** Authentication is verified against `lis.security.jwk-set-uri`; locally a mock provider issues the tokens. The token contract (`sub`, `tenant_id`, `org_ids`, `roles`, `OWNER` to `VIEWER`) must be implemented by the real provider, the public client registered with PKCE, and `lis.security.issuer` and `lis.security.audience` set. The persona picker is disabled in production builds.
4. **Tenants can be created and listed, nothing more.** The platform administrator creates tenants and lists them in the product (decision D46). There is no rename, disable or delete of a tenant (a tenant id is permanent), no paging of the list, and no screen or endpoint for organizations or users: an organization is registered the first time an assessment names it, and users and their roles live in the identity provider, including the `PLATFORM_ADMIN` role.

## Product gaps

5. **Reports (PDF, CSV and the five report types) are not built.** The specification does not define them (decision F13), so no endpoint or screen exists.
6. **Assessment capture is basic.** Owners and administrators build questionnaires, keep a roster, start runs and close them; respondents answer in the product (decisions D47 to D51). There are no reminders or e-mail, no reopening of a closed run, no change of the teams of a started run, no autosave of drafts, no bulk import of people and no paging of the lists. Free-text answers are collected and kept with the run but nothing shows them yet. Everything authored in the builder is unvalidated by construction.
7. **Maximum deduction of a penalty is not shown.** It is not recorded in the stored trace (D42). Showing it needs a trace-version change and a migration; the penalty page states the omission.
8. **History only includes valid runs.** A run with INSUFFICIENT_DATA has no score to trend (D37). If every assessment of an organization is insufficient the dashboard shows "No scored assessments yet", which is slightly imprecise.
9. **Dashboard shows one template at a time**, the latest valid run of the chosen template.
10. **Recommendation text, pattern wording and band labels depend entirely on the configured catalog.** Missing entries fall back to readable codes and are listed as unmapped; nothing is generated or translated. There is no localization.

## Security and operations

11. **The backend dependency vulnerability scan did not complete.** The OWASP dependency-check was started (`mvn org.owasp:dependency-check-maven:12.1.0:check`); without an NVD API key the CVE database download stalled for hours (the local database stopped growing at 610 MB) and the run was stopped, so **no backend vulnerability result exists**. Run it in CI with `NVD_API_KEY` set (or use a software-composition-analysis service) before go-live. Frontend `npm audit` is clean (0 vulnerabilities).
12. **No rate limiting or abuse protection** in the application or the sample nginx. Put a gateway or WAF in front, and add limits on `POST /calculate` if exposed beyond trusted callers.
13. **No TLS inside the stack.** The sample images speak HTTP. Terminate TLS in front and add Strict-Transport-Security there.
14. **No token refresh.** Sessions end when the access token expires (default 1 hour from the mock provider); the user signs in again. A refresh flow depends on the real provider's policy.
15. **No refresh of data on a schedule.** Data is read when a page opens; scoring is synchronous, so nothing changes in the background.
16. **Multi-instance behaviour is untested.** The configuration cache is per instance (Caffeine); with several instances a newly activated version can be served stale until the cache entry expires or the instance restarts. Redis is the documented future option.
17. **Observability backends are not provisioned.** Metrics are exposed for Prometheus and logs are ECS JSON on stdout; dashboards, alerts and a trace exporter are not part of this repository.
18. **Retention.** History is immutable by design; no retention or archival policy is implemented.

## Quality coverage

19. **Accessibility was verified automatically (axe, WCAG 2.2 AA tags) and by keyboard and markup review.** It was not audited with a screen reader, at 400 % zoom or in high-contrast mode.
20. **Browser matrix.** End-to-end tests run in Chromium only.
21. **Performance figures are from one development machine on localhost.** No load test of the API or database was run, and V3 section 35.1 defines no target.
22. **Backend code coverage is not measured** (no JaCoCo configured); frontend unit coverage is 82 % of statements.
23. **Pinned tooling.** TypeScript is pinned to 5.9 (version 7 conflicts with `openapi-typescript` and others) and `minimatch` is overridden to a patched major to clear an advisory in development tools.

## Known design trade-offs

24. **Tokens live in `sessionStorage`** so they disappear with the tab, which means a second tab asks for a new sign-in. A backend-for-frontend with an HttpOnly session cookie would remove tokens from the browser altogether; it was not built.
25. **The CSP allows inline `style` attributes** (`style-src 'unsafe-inline'`) because the chart library positions elements with them. Scripts are restricted to the application's own files.
26. **Development stack exposes the mock identity provider and a persona picker.** These exist only in the compose stack and in `VITE_DEV_PERSONAS=true` builds.

## Answers from several people (decision D45)

All defaults below are unvalidated, as the specification says (specv3 4.11, 4.12, 14.2.1). They live in the versioned configuration, so a deployment can change them without code, and every run records the values it used.

27. **The minimum of 5 responses, the median, the signal share of 0.5 and the participation formula are illustrative.** Participation confidence is `min(1, respondents / 10)` over the fewest respondents among the questions behind a pattern's evidence. Nobody has calibrated these, and the pattern wording must not suggest otherwise.
28. **Collecting answers is limited to the respondent flow.** A respondent count on a team or organization figure sent through the API is stated by the sender and cannot be verified; the interface says so. Answers collected through a run are counted exactly.
29. **Teams are free identifiers.** There is no team registry, no team name, no membership and no provisioning (decision D45). The interface shows "Team alpha" from the identifier it was given. Two senders that spell a team differently create two teams.
30. **No history for a team.** Trends, the dashboard, the previous-assessment comparison and the comparison page work for the organization only. A team run can be opened from the latest organization result, but there is no series of one team over time.
31. **A team that cannot be scored at all has no run.** When a required question has too few answers from a team, the team gets no run, no explanation and no answers to open. It is still listed, without a run and without a score, wherever the organization run is read (from the names stored with that run), and the interface explains why instead of showing an empty page.
32. **Member answers are stored, as answers without a person.** The product never records who answered, never returns one member's answer on its own and hides the values of a group below the minimum. It cannot prevent a sender from putting identifying words into a free-text answer, or from sending a group so small that the organization total points to a person. The minimum protects the common case, not every case.
33. **Teams are never ranked, and nothing prevents a reader from doing it.** The engine and the interface show no ranking and no cross-team comparison; the scope switcher lists teams in identifier order and shows no scores.
34. **The development login lists a sandbox persona** (organization `org-sandbox`) for trying out answers from teams. It exists only with the development stack and `VITE_DEV_PERSONAS=true`.
35. **The platform administrator sees only an id, a name and a creation date.** There are no usage figures per tenant (they would expose activity across tenants to the platform role), and the audit of tenant creation is read from the database; the product has no screen for it.
36. **A tenant created in the product cannot be used until the identity provider knows it.** The product creates the tenant record; the identity provider must issue tokens with that `tenant_id` and the users' roles and organizations.

## Questionnaires, roster, runs and respondents (decisions D47 to D51)

37. **Everything authored in the builder is unvalidated.** Question wording, option scores, thresholds, penalty amounts, severities and recommendations are the author's own. The generated defaults (options spaced evenly with the first option at 0 and the last at 100, severity multipliers of 0, 0.4, 0.7 and 1, a weight of 1) are conventions, not calibrated values.
38. **A questionnaire inherits some settings from the platform default**: required coverage, overall weights and interpretation, response settings (minimum number of responses) and the dimension names. A platform change to these reaches every questionnaire. The builder has no imbalance rules and no way to set these values.
39. **The builder is narrower than the engine.** Rules compare the normalized value of one question or a raw dimension score; there are no gap or text-flag conditions, only fixed and severity-scaled deductions, one penalty and one recommendation per rule, and no exclusive groups. Copying the initial questionnaire leaves out what the builder cannot express and says what in a list shown once.
40. **Archiving only hides a questionnaire.** It stops edits and new runs; its active layer stays so past assessments remain explainable. There is no un-archive and no deletion.
41. **A run closed under an older questionnaire version is scored without tenant and organization overrides.** When the questionnaire has been republished since the run started, closing selects the pinned configuration version explicitly, which merges it over the current platform default and skips the tenant and organization layers (as recalculation by version does).
42. **Anonymity has limits, stated rather than hidden.** An administrator can see who has not finished, which in a team smaller than the minimum number of responses can point to a person (the run page says so). Stored answers have no person, time or order, but PostgreSQL keeps rows in insertion order on disk, which no endpoint exposes. A sender of free text can still write something identifying; the form warns against it.
43. **Free-text answers are stored and read by nothing.** They are kept with the run and never scored, never shown and never exported. Showing them anonymously needs its own decision (a minimum group size, no attribution).
44. **The roster is personal data with no retention rule.** Names and e-mail addresses are kept until someone removes them from the database; people are deactivated, never deleted, and a person who moves or is deactivated after a run started stays a participant of that run. Reads of the roster and of a participant list are audited.
45. **A respondent is whoever the identity provider says owns an e-mail address.** The match is the verified `email` claim in the token's tenant; if the provider issues an address it has not verified, someone could answer as another person. One e-mail address is one person per tenant.
46. **No reminders, no reopening, no edits to a started run.** Nothing is sent by e-mail; a closed run stays closed; the teams of a run cannot change after it starts; nothing closes a run at its due date. Drafts are saved on request, not automatically.
47. **The browser journey and the roster persist.** The Playwright journey writes to its own scratch organization (`org-assess`) and re-uses a roster it finds, so repeated runs against one stack work; a clean state needs `docker compose down -v`.
48. **The interface choices that were not in your mock-ups.** No global search, notification bell, AI-credit meter, Reports or Settings entries; headings use the system serif stack because a web font would need an origin the content security policy does not allow; a returning light-theme user can see one dark frame while the stored theme is applied.

