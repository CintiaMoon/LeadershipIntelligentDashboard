# Review worksheet for the initial platform configuration

Generated from `src/main/resources/config/initial-platform-default.json` (do not edit by hand: record decisions in `docs/configuration/review-decisions.json` and regenerate with `python docs/configuration/tools/review_worksheet.py`, also after the file changes). It lists every number and rule in the file that decides a score, so that someone who knows the organization can confirm, change or switch each one off. **Nothing in the file is validated or calibrated** (specv3 section 42): the identifiers and the suggested maximum deductions follow the specification's catalog, and the questions, thresholds, severities, evidence requirements, stacking caps and wording were authored for this version.

## Status of the review

Decisions recorded in `docs/configuration/review-decisions.json` by **Cintia** on 2026-11-02: coverage, patterns, questions, signals. Items marked "not yet reviewed" are open. Every decision so far is **Keep**, so the file has not changed and no new version is needed. A Keep confirms an item; it does not validate it (see the evidence line in the sign-off).

- *patterns*: Decision given for the 16 patterns, meaning what each detects and when. The deductions of their penalties, the caps and the stacking were not mentioned and are shown as not yet reviewed.

## How to use it

1. Read each table and decide each item: **Keep**, **Change to ...** or **Disable**, with a note if the reason matters (for example the data you checked it against). Tell the decision to whoever maintains the file; it is recorded in `review-decisions.json`.
2. The changes are made in the JSON file (or, once configuration management exists, as a new version that a second person approves), never in the code.
3. Review the "What the numbers mean together" questions at the end. They are the ones a single row cannot answer.

Scores run from 0 to 100 and 100 is always the desirable end. A pattern is an organizational condition; a penalty is its consequence. Both are separate rows because you may accept one and not the other.

## 1. Questions and how each answer is scored

| Id | Dimension | Weight | Question | Scored as | Decision |
|---|---|---|---|---|---|
| `TH01` | Team health | 1.0 | Our team can keep up its current pace over the long term. (1 = strongly disagree, 5 = strongly agree) | scale 1 to 5, higher is better | Keep |
| `TH02` | Team health | 1.0 | How heavy is the team's workload compared with what it can handle? (1 = very light, 5 = far too heavy) | scale 1 to 5, lower is better | Keep |
| `TH03` | Team health | 1.0 | People on the team feel safe to raise concerns and to disagree. (1 = strongly disagree, 5 = strongly agree) | scale 1 to 5, higher is better | Keep |
| `TH04` | Team health | 1.0 | The team collaborates well across roles and with other teams. (1 = strongly disagree, 5 = strongly agree) | scale 1 to 5, higher is better | Keep |
| `TH05` | Team health | 1.0 | Everyone on the team knows what they are responsible for. (1 = strongly disagree, 5 = strongly agree) | scale 1 to 5, higher is better | Keep |
| `DS01` | Delivery stability | 1.0 | How often are planned commitments delivered when planned? (ALWAYS, OFTEN, SOMETIMES, RARELY or NEVER) | options: ALWAYS = 100, OFTEN = 75, SOMETIMES = 50, RARELY = 25, NEVER = 0 | Keep |
| `DS02` | Delivery stability | 1.0 | How often is the team working on too many things at once? (1 = never, 5 = constantly) | scale 1 to 5, lower is better | Keep |
| `DS03` | Delivery stability | 1.0 | How many of the last ten releases needed a rollback or an urgent fix? (0 to 10) | number 0 to 10, lower is better, out of range: clamp | Keep |
| `DS04` | Delivery stability | 1.0 | Estimates and plans match what actually happens. (1 = strongly disagree, 5 = strongly agree) | scale 1 to 5, higher is better | Keep |
| `DS05` | Delivery stability | 1.0 | Does delivery depend on people regularly working extra hours or making heroic efforts? (yes or no) | yes = 0, no = 100 | Keep |
| `TR01` | Technical risk | 1.0 | How many production incidents affected customers in the last quarter? (0 to 20; more counts as 20) | number 0 to 20, lower is better, out of range: clamp | Keep |
| `TR02` | Technical risk | 1.0 | Automated tests give the team confidence to change the system. (1 = strongly disagree, 5 = strongly agree) | scale 1 to 5, higher is better | Keep |
| `TR03` | Technical risk | 1.0 | The team can quickly detect and diagnose problems in production. (1 = strongly disagree, 5 = strongly agree) | scale 1 to 5, higher is better | Keep |
| `TR04` | Technical risk | 1.0 | How much does technical debt slow the team down? (1 = not at all, 5 = severely) | scale 1 to 5, lower is better | Keep |
| `TR05` | Technical risk | 1.0 | Is there a part of the system that only one person can safely change? (yes or no) | yes = 0, no = 100 | Keep |
| `LL01` | Leadership load | 1.0 | How often do decisions wait for a leader's approval? (1 = rarely, 5 = almost always) | scale 1 to 5, lower is better | Keep |
| `LL02` | Leadership load | 1.0 | How many people report directly to the leader? (0 to 20; more counts as 20) | number 0 to 20, lower is better, out of range: clamp | Keep |
| `LL03` | Leadership load | 1.0 | How often does the leader step in to do work the team could do? (1 = rarely, 5 = constantly) | scale 1 to 5, lower is better | Keep |
| `LL04` | Leadership load | 1.0 | Leaders have time for strategic work and for their people. (1 = strongly disagree, 5 = strongly agree) | scale 1 to 5, higher is better | Keep |
| `LL05` | Leadership load | 1.0 | Someone could take over critical leadership duties if the leader were away. (1 = strongly disagree, 5 = strongly agree) | scale 1 to 5, higher is better | Keep |

Every question is optional (a missing answer is ignored, never counted as zero). A dimension needs enough of its questions answered to be scored: see section 3.

## 2. Signals

A signal is one answer turned into a 0 to 100 value that patterns can test.

| Signal | Name | From question | Decision |
|---|---|---|---|
| `TEAM_SUSTAINABILITY` | Team sustainability | TH01 | Keep |
| `WORKLOAD_MANAGEABILITY` | Workload manageability | TH02 | Keep |
| `PSYCHOLOGICAL_SAFETY` | Psychological safety | TH03 | Keep |
| `DELIVERY_PREDICTABILITY` | Delivery predictability | DS01 | Keep |
| `WIP_CONTROL` | Control of work in progress | DS02 | Keep |
| `RELEASE_STABILITY` | Release stability | DS03 | Keep |
| `PLANNING_REALISM` | Planning realism | DS04 | Keep |
| `DELIVERY_WITHOUT_HEROICS` | Delivery without heroics | DS05 | Keep |
| `PRODUCTION_STABILITY` | Production stability | TR01 | Keep |
| `TEST_CONFIDENCE` | Test confidence | TR02 | Keep |
| `OBSERVABILITY` | Observability | TR03 | Keep |
| `KNOWLEDGE_SPREAD` | Knowledge spread | TR05 | Keep |
| `DECISION_FLOW` | Decision flow | LL01 | Keep |
| `SPAN_MANAGEABILITY` | Span manageability | LL02 | Keep |
| `DELEGATION` | Delegation | LL03 | Keep |
| `LEADER_CAPACITY` | Leadership capacity | LL04 | Keep |
| `SUCCESSION_COVERAGE` | Succession coverage | LL05 | Keep |

## 3. Dimensions, overall weights and score bands

| Setting | Value | Decision |
|---|---|---|
| Share of Team health questions that must be answered before it is scored | 0.75 | Keep |
| Share of Delivery stability questions that must be answered before it is scored | 0.75 | Keep |
| Share of Technical risk questions that must be answered before it is scored | 0.75 | Keep |
| Share of Leadership load questions that must be answered before it is scored | 0.75 | Keep |
| Weight of Team health in the overall score | 0.25 | not yet reviewed |
| Weight of Delivery stability in the overall score | 0.25 | not yet reviewed |
| Weight of Technical risk in the overall score | 0.25 | not yet reviewed |
| Weight of Leadership load in the overall score | 0.25 | not yet reviewed |
| Tolerance when checking that the weights add up to 1 | 0.0001 | not yet reviewed |

| Band (overall score) | From | To | Decision |
|---|---|---|---|
| Critical | 0 | 40 | not yet reviewed |
| At risk | 40 | 60 | not yet reviewed |
| Healthy | 60 | 80 | not yet reviewed |
| Strong | 80 | 100 | not yet reviewed |

| Band (a dimension) | From | To | Decision |
|---|---|---|---|
| Critical | 0 | 40 | not yet reviewed |
| At risk | 40 | 60 | not yet reviewed |
| Healthy | 60 | 80 | not yet reviewed |
| Strong | 80 | 100 | not yet reviewed |

## 4. Patterns and their penalties

Severity is the impact of a pattern; confidence is the quality of the evidence. A pattern is only detected when enough independent signals back it and the confidence is at least the stated minimum. A severity-based penalty deducts the base amount times a multiplier (the multipliers are listed under each strategy). The maximum is a cap on one penalty, and a cumulative cap limits several penalties on the same target.

| Pattern | Severity | Detected when | Signals needed | Min. confidence | Penalty | Lowers | Amount | Maximum | Stacking | Cumulative cap | Decision |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `TH-001` Team burnout risk | Critical | Workload manageability (`WORKLOAD_MANAGEABILITY`, from TH02) is at most 25 and Team sustainability (`TEAM_SUSTAINABILITY`, from TH01) is at most 35 | 2 | 0.7 | `TH-P01` Burnout risk deduction | Team health | 12 base (x low 0, medium 0.4, high 0.7, critical 1.0) | 12 | capped stack, category sustainability | 30 | Pattern: Keep. Penalty: not yet reviewed |
| `TH-002` Low psychological safety | High | Psychological safety (`PSYCHOLOGICAL_SAFETY`, from TH03) is at most 25 | 1 | 0.5 | `TH-P02` Psychological safety deduction | Team health | 8 base (x low 0, medium 0.4, high 0.7, critical 1.0) | 8 | capped stack, category psychological_safety | 30 | Pattern: Keep. Penalty: not yet reviewed |
| `DS-001` Chronic delivery unpredictability | High | Delivery predictability (`DELIVERY_PREDICTABILITY`, from DS01) is at most 25 and Planning realism (`PLANNING_REALISM`, from DS04) is at most 25 | 2 | 0.7 | `DS-P01` Delivery unpredictability deduction | Delivery stability | 10 base (x low 0, medium 0.4, high 0.7, critical 1.0) | 10 | capped stack, category predictability | 30 | Pattern: Keep. Penalty: not yet reviewed |
| `DS-003` Excessive work in progress | Medium | Control of work in progress (`WIP_CONTROL`, from DS02) is at most 25 | 1 | 0.5 | `DS-P03` Work in progress deduction | Delivery stability | 6 base (x low 0, medium 0.4, high 0.7, critical 1.0) | 6 | capped stack, category work_in_progress | 30 | Pattern: Keep. Penalty: not yet reviewed |
| `DS-005` Delivery heroics | High | Delivery without heroics (`DELIVERY_WITHOUT_HEROICS`, from DS05) is at most 0 | 1 | 0.5 | `DS-P05` Delivery heroics deduction | Delivery stability | 12 base (x low 0, medium 0.4, high 0.7, critical 1.0) | 12 | capped stack, category heroics | 30 | Pattern: Keep. Penalty: not yet reviewed |
| `DS-009` Release instability | High | Release stability (`RELEASE_STABILITY`, from DS03) is at most 30 | 1 | 0.5 | `DS-P09` Release instability deduction | Delivery stability | 10 base (x low 0, medium 0.4, high 0.7, critical 1.0) | 10 | capped stack, category releases | 30 | Pattern: Keep. Penalty: not yet reviewed |
| `TR-003` Single point of failure | High | Knowledge spread (`KNOWLEDGE_SPREAD`, from TR05) is at most 0 | 1 | 0.5 | `TR-P03` Single point of failure deduction | Technical risk | 12 base (x low 0, medium 0.4, high 0.7, critical 1.0) | 12 | capped stack, category knowledge | 30 | Pattern: Keep. Penalty: not yet reviewed |
| `TR-004` Weak test automation | Medium | Test confidence (`TEST_CONFIDENCE`, from TR02) is at most 25 | 1 | 0.5 | `TR-P04` Test automation deduction | Technical risk | 8 base (x low 0, medium 0.4, high 0.7, critical 1.0) | 8 | capped stack, category testing | 30 | Pattern: Keep. Penalty: not yet reviewed |
| `TR-005` Production instability | Critical | Production stability (`PRODUCTION_STABILITY`, from TR01) is at most 50 | 1 | 0.5 | `TR-P05` Production instability deduction | Technical risk | 15 base (x low 0, medium 0.4, high 0.7, critical 1.0) | 15 | capped stack, category production | 30 | Pattern: Keep. Penalty: not yet reviewed |
| `TR-006` Poor observability | Medium | Observability (`OBSERVABILITY`, from TR03) is at most 25 | 1 | 0.5 | `TR-P06` Observability deduction | Technical risk | 6 base (x low 0, medium 0.4, high 0.7, critical 1.0) | 6 | capped stack, category observability | 30 | Pattern: Keep. Penalty: not yet reviewed |
| `LL-001` Leadership bottleneck | High | Decision flow (`DECISION_FLOW`, from LL01) is at most 25 and Delegation (`DELEGATION`, from LL03) is at most 25 | 2 | 0.7 | `LL-P01` Leadership bottleneck deduction | Leadership load | 10 base (x low 0, medium 0.4, high 0.7, critical 1.0) | 10 | max per category, category decision_flow |  | Pattern: Keep. Penalty: not yet reviewed |
| `LL-004` Decision latency | Medium | Decision flow (`DECISION_FLOW`, from LL01) is at most 25 | 1 | 0.5 | `LL-P04` Decision latency deduction | Leadership load | 8 base (x low 0, medium 0.4, high 0.7, critical 1.0) | 8 | max per category, category decision_flow |  | Pattern: Keep. Penalty: not yet reviewed |
| `LL-006` Leadership overload | Critical | Leadership capacity (`LEADER_CAPACITY`, from LL04) is at most 25 and Span manageability (`SPAN_MANAGEABILITY`, from LL02) is at most 40 | 2 | 0.7 | `LL-P06` Leadership overload deduction | Leadership load | 12 base (x low 0, medium 0.4, high 0.7, critical 1.0) | 12 | capped stack, category capacity | 30 | Pattern: Keep. Penalty: not yet reviewed |
| `LL-008` Weak succession coverage | Medium | Succession coverage (`SUCCESSION_COVERAGE`, from LL05) is at most 25 | 1 | 0.5 | `LL-P08` Succession coverage deduction | Leadership load | 5 base (x low 0, medium 0.4, high 0.7, critical 1.0) | 5 | capped stack, category succession | 30 | Pattern: Keep. Penalty: not yet reviewed |
| `XD-001` Unsustainable delivery model | High | Team health raw score is at most 40 and Delivery stability raw score is at most 40 | 2 | 0.7 | `XD-P01` Operating model deduction | Overall | 8 base (x low 0, medium 0.4, high 0.7, critical 1.0) | 8 | capped stack, category operating_model | 15 | Pattern: Keep. Penalty: not yet reviewed |
| `XD-008` Hero culture | High | Delivery without heroics (`DELIVERY_WITHOUT_HEROICS`, from DS05) is at most 0 and Knowledge spread (`KNOWLEDGE_SPREAD`, from TR05) is at most 0 | 2 | 0.7 | `XD-P08` Hero culture deduction | Overall | 10 base (x low 0, medium 0.4, high 0.7, critical 1.0) | 10 | capped stack, category dependency | 15 | Pattern: Keep. Penalty: not yet reviewed |

### Advice raised by each pattern

| Pattern | Advice | Decision |
|---|---|---|
| `TH-001` | **Review team capacity**: Compare the planned workload with the team's real capacity and agree what can move or stop. | not yet reviewed |
| `TH-001` | **Review workload distribution**: Check whether work is concentrated on a few people and rebalance it. | not yet reviewed |
| `TH-002` | **Build psychological safety**: Make it routine to raise problems early, for example with blameless reviews, and respond to concerns visibly. | not yet reviewed |
| `DS-001` | **Review planning practice**: Compare past plans with outcomes and adjust how work is sized and committed. | not yet reviewed |
| `DS-003` | **Limit work in progress**: Agree a limit on parallel work and finish work before starting more. | not yet reviewed |
| `DS-005` | **Reduce reliance on heroic effort**: Find out why extra effort is needed and fix the cause, so delivery does not depend on it. | not yet reviewed |
| `DS-009` | **Improve release practice**: Review recent rollbacks and urgent fixes and strengthen checks before release. | not yet reviewed |
| `TR-003` | **Reduce key-person dependency**: Spread critical knowledge across more than one person, for example through pairing and documentation. | not yet reviewed |
| `TR-004` | **Strengthen test automation**: Add automated tests where changes are riskiest and keep them fast and reliable. | not yet reviewed |
| `TR-005` | **Stabilize production**: Review the recent incidents, fix the repeating causes and agree how to prevent them. | not yet reviewed |
| `TR-006` | **Improve observability**: Improve monitoring and logging so problems are found and understood quickly. | not yet reviewed |
| `LL-001` | **Delegate decisions**: Agree which decisions the team can take without approval and make it explicit. | not yet reviewed |
| `LL-001` | **Clarify decision rights**: Write down who decides what, and review decisions that waited the longest. | not yet reviewed |
| `LL-004` | **Clarify decision rights**: Write down who decides what, and review decisions that waited the longest. | not yet reviewed |
| `LL-006` | **Review leadership capacity**: Look at the leader's commitments and direct reports and decide what to hand over or add support for. | not yet reviewed |
| `LL-008` | **Plan succession coverage**: Name who covers critical leadership duties and prepare them. | not yet reviewed |
| `XD-001` | **Review the operating model**: Look at how work is organized across teams and agree changes that make it sustainable. | not yet reviewed |
| `XD-008` | **Reduce reliance on heroic effort**: Find out why extra effort is needed and fix the cause, so delivery does not depend on it. | not yet reviewed |
| `XD-008` | **Reduce key-person dependency**: Spread critical knowledge across more than one person, for example through pairing and documentation. | not yet reviewed |

## 5. Imbalance

| Rule | Compares | Reported when | Severity | Decision |
|---|---|---|---|---|
| `DELIVERY_TECHNICAL_GAP` | Delivery stability minus Technical risk | the gap is at least 30 (raw scores) | High | not yet reviewed |

An imbalance is only reported; it never lowers a score.

## 6. Answers from several people

| Setting | Value | Decision |
|---|---|---|
| How the answers of several people to one question are combined | MEDIAN | not yet reviewed |
| Minimum number of responses for a question to count (fewer is treated as missing) | 5 | not yet reviewed |
| Share of a signal that must come from answers (participation) | 0.5 | not yet reviewed |
| Number of respondents that gives full participation confidence | 10 | not yet reviewed |

## What the numbers mean together

Single rows cannot answer these. They are the questions the specification asks for before a configuration becomes active (specv3 section 42): please answer them, or say how you would find out.

1. **Distribution.** What overall score should a typical healthy team get, and a struggling one? With the bands above, how many of your teams would you expect in each band? Do these thresholds put them there?
2. **Penalty impact.** If the worst pattern in each dimension fires, how many points should the dimension lose, and the overall score? Are the maximum and cumulative caps in section 4 the limits you intend?
3. **False positives.** Which patterns would you expect to fire often by accident (a single low answer is enough for any pattern that needs only one signal)? Should those need two signals or a higher confidence?
4. **Severity against deduction.** Is the order of severity (critical, high, medium, low) the order of deduction you want? Several high-severity patterns deduct less than medium ones.
5. **Missing data.** Is a share of 0.75 of a dimension's questions the right minimum before it is scored, and is a minimum of five responses the right group size for your teams?
6. **Evidence for this review.** What dataset or experience was this checked against (a pilot, past assessments, expert judgement)? Name it, so that the calibration record can refer to it.

## Sign-off

| Reviewed by | Role | Date | Evidence or dataset | Version of the file reviewed (hash) |
|---|---|---|---|---|
| Cintia (partial review) | project owner | 2026-11-02 | not stated: no dataset or reference was named, so the configuration stays unvalidated | `44455863f724f4ddd5688c8b3ce5fd27dddfc936fe643dc8552f7946b84ab05c` (SHA-256 of the file) |

