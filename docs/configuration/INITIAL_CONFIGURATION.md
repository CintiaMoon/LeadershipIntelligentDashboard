# Initial platform configuration

**Status: initial and unvalidated.** The specification (`specs/specv3.md`) defines the pattern and penalty taxonomy, suggested maximum deductions and recommended stacking policies, but it does not define a questionnaire. The questions, thresholds, caps and wording in this configuration were written for this version. They are a starting point to be reviewed and calibrated against real assessments, not empirically derived values, and the product must not present them as such (specv3 14.3, CLAUDE.md).

## How it is provided

The platform configuration is one JSON document. It is read when the application starts:

| Setting | Default | Meaning |
|---|---|---|
| `lis.configuration.initial.enabled` (`LIS_CONFIGURATION_INITIAL_ENABLED`) | `true` | read the file at startup |
| `lis.configuration.initial.location` (`LIS_CONFIGURATION_INITIAL_LOCATION`) | `classpath:config/initial-platform-default.json` | the bundled initial configuration; use `file:/etc/lis/platform-default.json` to keep your own outside the application |
| `lis.configuration.initial.mode` (`LIS_CONFIGURATION_INITIAL_MODE`) | `IF_MISSING` | `IF_MISSING` activates the file only when no platform configuration is active and otherwise leaves the active one alone, warning if it differs. `SYNC` also activates a changed file as a new version and archives the previous one |

What happens at startup, in order: the file is read; its canonical hash (formatting and key order do not matter) is compared with the active version; if there is none, or `SYNC` finds a difference, a draft is created, validated with the same rules as any configuration and activated with an approval record (`initial-configuration`). **A file that does not exist, is not JSON or fails validation stops the application with the reason**, so it can never run without rules. An active version is never edited: a change is always a new version, and every scoring run records the exact version and hash it used.

With the `dev` profile the application uses the illustrative sample of `DevDataSeeder` instead, because the browser tests depend on its exact numbers. Never run the `dev` profile in production.

What this does **not** provide: tenants and organizations (limitation 4), identity provider setup (limitation 3), and a way to change the configuration while the application runs (replace the file and restart with `SYNC`, or use the service from a controlled job).

## What the document contains

Every part of the configuration document is present: questions with their normalization, signals, dimension mappings and required coverage, patterns with conditions and evidence requirements, penalties with strategy, cap and stacking policy, one imbalance rule, overall weights and score bands, the rules for combining answers from several people, recommendation text, and names, descriptions and explanations for everything the interface shows.

| Part | Count |
|---|---|
| Questions | 20 (five per dimension, equal weight 1.0) |
| Signals | 17 |
| Patterns | 16 (all with a penalty) |
| Penalties | 16 |
| Imbalance rules | 1 |
| Recommendations | 17 |

### Direction

Every normalized value and signal uses the same direction: **100 is the desirable end** (specv3 3.1). A question whose raw answer grows with the problem (workload, incidents, direct reports) is configured with direction `NEGATIVE` or a negative count, so a signal such as `WORKLOAD_MANAGEABILITY` is low when the workload is heavy. The specification's example condition "workload pressure at least 75" therefore reads "workload manageability at most 25" here.

### Questions

| Id | Dimension | Question | Answer is scored as |
|---|---|---|---|
| `TH01` | Team Health | Our team can keep up its current pace over the long term. (1 = strongly disagree, 5 = strongly agree) | scale 1 to 5, higher is better |
| `TH02` | Team Health | How heavy is the team's workload compared with what it can handle? (1 = very light, 5 = far too heavy) | scale 1 to 5, higher is worse |
| `TH03` | Team Health | People on the team feel safe to raise concerns and to disagree. (1 = strongly disagree, 5 = strongly agree) | scale 1 to 5, higher is better |
| `TH04` | Team Health | The team collaborates well across roles and with other teams. (1 = strongly disagree, 5 = strongly agree) | scale 1 to 5, higher is better |
| `TH05` | Team Health | Everyone on the team knows what they are responsible for. (1 = strongly disagree, 5 = strongly agree) | scale 1 to 5, higher is better |
| `DS01` | Delivery Stability | How often are planned commitments delivered when planned? (ALWAYS, OFTEN, SOMETIMES, RARELY or NEVER) | one of ALWAYS, OFTEN, SOMETIMES, RARELY, NEVER |
| `DS02` | Delivery Stability | How often is the team working on too many things at once? (1 = never, 5 = constantly) | scale 1 to 5, higher is worse |
| `DS03` | Delivery Stability | How many of the last ten releases needed a rollback or an urgent fix? (0 to 10) | number 0 to 10, higher is worse, beyond the range counts as the limit |
| `DS04` | Delivery Stability | Estimates and plans match what actually happens. (1 = strongly disagree, 5 = strongly agree) | scale 1 to 5, higher is better |
| `DS05` | Delivery Stability | Does delivery depend on people regularly working extra hours or making heroic efforts? (yes or no) | yes is 0, no is 100 |
| `TR01` | Technical Risk | How many production incidents affected customers in the last quarter? (0 to 20; more counts as 20) | number 0 to 20, higher is worse, beyond the range counts as the limit |
| `TR02` | Technical Risk | Automated tests give the team confidence to change the system. (1 = strongly disagree, 5 = strongly agree) | scale 1 to 5, higher is better |
| `TR03` | Technical Risk | The team can quickly detect and diagnose problems in production. (1 = strongly disagree, 5 = strongly agree) | scale 1 to 5, higher is better |
| `TR04` | Technical Risk | How much does technical debt slow the team down? (1 = not at all, 5 = severely) | scale 1 to 5, higher is worse |
| `TR05` | Technical Risk | Is there a part of the system that only one person can safely change? (yes or no) | yes is 0, no is 100 |
| `LL01` | Leadership Load | How often do decisions wait for a leader's approval? (1 = rarely, 5 = almost always) | scale 1 to 5, higher is worse |
| `LL02` | Leadership Load | How many people report directly to the leader? (0 to 20; more counts as 20) | number 0 to 20, higher is worse, beyond the range counts as the limit |
| `LL03` | Leadership Load | How often does the leader step in to do work the team could do? (1 = rarely, 5 = constantly) | scale 1 to 5, higher is worse |
| `LL04` | Leadership Load | Leaders have time for strategic work and for their people. (1 = strongly disagree, 5 = strongly agree) | scale 1 to 5, higher is better |
| `LL05` | Leadership Load | Someone could take over critical leadership duties if the leader were away. (1 = strongly disagree, 5 = strongly agree) | scale 1 to 5, higher is better |

No question is free text: keyword matching on free text is weak evidence, so the initial questionnaire uses scales, counts and yes/no answers. The engine supports text questions.

### Patterns and penalties

Identifiers, categories and suggested maximum deductions come from specv3 13.3 and 15.5. Only patterns that the questionnaire can measure are enabled; the catalog's other entries are not configured, because a catalog entry is not executable until its conditions are defined and the specification forbids inferring them from the name.

| Pattern | Severity | Raised when | Evidence needed | Penalty | Target | Maximum | Stacking |
|---|---|---|---|---|---|---|---|
| `TH-001` Team burnout risk | Critical | WORKLOAD_MANAGEABILITY at most 25 and TEAM_SUSTAINABILITY at most 35 | 2 signal(s), confidence 0.7 | `TH-P01` | Team Health | 12 | CAPPED_STACK |
| `TH-002` Low psychological safety | High | PSYCHOLOGICAL_SAFETY at most 25 | 1 signal(s), confidence 0.5 | `TH-P02` | Team Health | 8 | CAPPED_STACK |
| `DS-001` Chronic delivery unpredictability | High | DELIVERY_PREDICTABILITY at most 25 and PLANNING_REALISM at most 25 | 2 signal(s), confidence 0.7 | `DS-P01` | Delivery Stability | 10 | CAPPED_STACK |
| `DS-003` Excessive work in progress | Medium | WIP_CONTROL at most 25 | 1 signal(s), confidence 0.5 | `DS-P03` | Delivery Stability | 6 | CAPPED_STACK |
| `DS-005` Delivery heroics | High | DELIVERY_WITHOUT_HEROICS at most 0 | 1 signal(s), confidence 0.5 | `DS-P05` | Delivery Stability | 12 | CAPPED_STACK |
| `DS-009` Release instability | High | RELEASE_STABILITY at most 30 | 1 signal(s), confidence 0.5 | `DS-P09` | Delivery Stability | 10 | CAPPED_STACK |
| `TR-003` Single point of failure | High | KNOWLEDGE_SPREAD at most 0 | 1 signal(s), confidence 0.5 | `TR-P03` | Technical Risk | 12 | CAPPED_STACK |
| `TR-004` Weak test automation | Medium | TEST_CONFIDENCE at most 25 | 1 signal(s), confidence 0.5 | `TR-P04` | Technical Risk | 8 | CAPPED_STACK |
| `TR-005` Production instability | Critical | PRODUCTION_STABILITY at most 50 | 1 signal(s), confidence 0.5 | `TR-P05` | Technical Risk | 15 | CAPPED_STACK |
| `TR-006` Poor observability | Medium | OBSERVABILITY at most 25 | 1 signal(s), confidence 0.5 | `TR-P06` | Technical Risk | 6 | CAPPED_STACK |
| `LL-001` Leadership bottleneck | High | DECISION_FLOW at most 25 and DELEGATION at most 25 | 2 signal(s), confidence 0.7 | `LL-P01` | Leadership Load | 10 | MAX_PER_CATEGORY |
| `LL-004` Decision latency | Medium | DECISION_FLOW at most 25 | 1 signal(s), confidence 0.5 | `LL-P04` | Leadership Load | 8 | MAX_PER_CATEGORY |
| `LL-006` Leadership overload | Critical | LEADER_CAPACITY at most 25 and SPAN_MANAGEABILITY at most 40 | 2 signal(s), confidence 0.7 | `LL-P06` | Leadership Load | 12 | CAPPED_STACK |
| `LL-008` Weak succession coverage | Medium | SUCCESSION_COVERAGE at most 25 | 1 signal(s), confidence 0.5 | `LL-P08` | Leadership Load | 5 | CAPPED_STACK |
| `XD-001` Unsustainable delivery model | High | the raw Team Health score at most 40 and the raw Delivery Stability score at most 40 | 2 signal(s), confidence 0.7 | `XD-P01` | Overall | 8 | CAPPED_STACK |
| `XD-008` Hero culture | High | DELIVERY_WITHOUT_HEROICS at most 0 and KNOWLEDGE_SPREAD at most 0 | 2 signal(s), confidence 0.7 | `XD-P08` | Overall | 10 | CAPPED_STACK |

The deduction of each penalty is its suggested maximum multiplied by a severity factor (low 0, medium 0.4, high 0.7, critical 1.0, the example of specv3 15.4), so a pattern deducts its full maximum only at critical severity. Penalties of one dimension stack up to a cumulative cap of 30 points (15 for the overall score), and the two leadership penalties about decisions (`LL-P01`, `LL-P04`) are alternatives: only the larger applies.

Other settings: required coverage 0.75 per dimension (a dimension with fewer than four of its five questions answered has no score), dimension weights 0.25 each, score bands 0 to 40 Critical, 40 to 60 At risk, 60 to 80 Healthy, 80 to 100 Strong (the illustrative bands of specv3 14.3 and 18), and the imbalance rule `DELIVERY_TECHNICAL_GAP` exactly as in specv3 12.2.

## Choices made for this version that the specification does not give

Review these first.

- The questionnaire itself: the questions, their wording, their scales and the mapping of each to one dimension with equal weight.
- Every threshold in the pattern conditions ("at most 25" on a 0 to 100 signal means the two lowest answers of a five-point scale), the severity of each pattern, and the evidence requirements (one signal at confidence 0.5, or two signals at 0.7). With the answers of several people, the participation factor lowers confidence below 10 respondents, so two-signal patterns need about 7 respondents to qualify for a deduction.
- The cumulative caps (30 per dimension, 15 overall) and the pairing of the two decision penalties. The specification recommends `CAPPED_STACK` within a dimension and `MAX_PER_CATEGORY` for related categories but gives no cap.
- The use of severity-based deductions for every penalty, with the multipliers of the specification's example.
- Required coverage 0.75, the equal dimension weights and the score bands, which the specification marks as illustrative.
- The minimum of 5 responses, the median, the signal share of 0.5 and the participation formula (decision D45).
- All recommendation, pattern, penalty, signal and question wording. It describes working conditions, never an individual.

## Changing it

1. Copy `src/main/resources/config/initial-platform-default.json` (or fetch the active version) and edit it. Keep identifiers from the catalog and never reuse one for a different meaning.
2. Check it: parse and validate it with `InitialConfigurationTest` (adjust the counts) or start the application against a scratch database.
3. Put it where the application can read it (`LIS_CONFIGURATION_INITIAL_LOCATION=file:/etc/lis/platform-default.json`) and start with `LIS_CONFIGURATION_INITIAL_MODE=SYNC` once. The old version is archived, the new one becomes active, and runs already stored keep the version they used.
4. Tenant, template and organization overrides use the same document format and are applied on top of this one (specv3 23.2); this repository has no endpoint for them yet.
