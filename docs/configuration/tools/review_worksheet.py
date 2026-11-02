"""Generates docs/configuration/REVIEW_WORKSHEET.md from the bundled initial configuration and review-decisions.json.

Run from anywhere: python docs/configuration/tools/review_worksheet.py. Edit review-decisions.json to record a decision;
never edit the worksheet, which is overwritten.
"""
import hashlib
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
SRC = os.path.join(ROOT, 'src', 'main', 'resources', 'config', 'initial-platform-default.json')
OUT = os.path.join(ROOT, 'docs', 'configuration', 'REVIEW_WORKSHEET.md')
DECISIONS = os.path.join(HERE, '..', 'review-decisions.json')
d = json.load(open(SRC, encoding='utf-8'))
record = json.load(open(DECISIONS, encoding='utf-8'))
file_hash = hashlib.sha256(open(SRC, 'rb').read()).hexdigest()
decided = record.get('decisions', {})
NOT_YET = 'not yet reviewed'


def decision(section, item=None):
    """The recorded decision for a section (or one item of it), or the words for "not reviewed"."""
    entries = decided.get(section, {})
    return entries.get(item, entries.get('*', NOT_YET)) if item is not None else entries.get('*', NOT_YET)


q_text = {k: v for k, v in d['presentation']['questions'].items()}
sig_text = d['presentation'].get('signals', {})
pat_text = d['presentation']['patterns']
pen_text = d['presentation']['penalties']
signal_name = {s['id']: sig_text.get(s['id'], {}).get('name', s['id']) for s in d['signals']}
signal_src = {s['id']: ', '.join(s['sources']) for s in d['signals']}
weights = {m['question']: (m['dimension'], m['weight']) for m in d['dimensions']['mappings']}
recs = {r['key']: r for r in d['recommendations']}


def human(code):
    return code.replace('_', ' ').capitalize()


def operand(c):
    if 'signal' in c:
        return f"{signal_name.get(c['signal'], c['signal'])} (`{c['signal']}`, from {signal_src.get(c['signal'], '?')})"
    if 'dimension' in c:
        return f"{human(c['dimension'])} raw score"
    return str(c)


OPS = {'LTE': 'at most', 'LT': 'below', 'GTE': 'at least', 'GT': 'above', 'EQ': 'equals', 'NE': 'does not equal'}


def cond(c, top=True):
    op = c['operator']
    if op in ('AND', 'OR'):
        joined = f' {op.lower()} '.join(cond(x, False) for x in c['conditions'])
        return joined if top else f'({joined})'
    if op == 'NOT':
        return 'not ' + cond(c['condition'], False)
    if op in OPS:
        return f"{operand(c)} is {OPS[op]} {c['value']}"
    if op == 'BETWEEN':
        return f"{operand(c)} is between {c['low']} and {c['high']}"
    if op in ('GAP_GT', 'GAP_GTE'):
        return f"gap between {operand(c['left'])} and {operand(c['right'])} ({c['direction']}) is {'above' if op == 'GAP_GT' else 'at least'} {c['value']}"
    if op == 'CONTAINS':
        return f"question {c['question']} flags {c['signal']}"
    return op


def scoring(q):
    n = q['normalization']
    t = q['type']
    if t == 'SCALE':
        return f"scale {n['min']} to {n['max']}, {'higher' if n['direction'] == 'POSITIVE' else 'lower'} is better"
    if t == 'NUMBER':
        return f"number {n['min']} to {n['max']}, {'higher' if n['direction'] == 'POSITIVE' else 'lower'} is better, out of range: {n['outOfRangePolicy'].lower()}"
    if t == 'MULTIPLE_CHOICE':
        return 'options: ' + ', '.join(f'{k} = {v}' for k, v in n['values'].items())
    if t == 'BOOLEAN':
        return f"yes = {n['trueValue']}, no = {n['falseValue']}"
    return t


L = []
w = L.append
w('# Review worksheet for the initial platform configuration')
w('')
w('Generated from `src/main/resources/config/initial-platform-default.json` (do not edit by hand: record decisions in `docs/configuration/review-decisions.json` and regenerate with `python docs/configuration/tools/review_worksheet.py`, also after the file changes). It lists every number and rule in the file that decides a score, so that someone who knows the organization can confirm, change or switch each one off. **Nothing in the file is validated or calibrated** (specv3 section 42): the identifiers and the suggested maximum deductions follow the specification\'s catalog, and the questions, thresholds, severities, evidence requirements, stacking caps and wording were authored for this version.')
w('')
w('## Status of the review')
w('')
reviewed = ', '.join(sorted(decided)) or 'nothing yet'
w(f"Decisions recorded in `docs/configuration/review-decisions.json` by **{record.get('reviewedBy')}** on {record.get('date')}: {reviewed}. Items marked \"{NOT_YET}\" are open. Every decision so far is **Keep**, so the file has not changed and no new version is needed. A Keep confirms an item; it does not validate it (see the evidence line in the sign-off).")
w('')
if record.get('fileSha256') != file_hash:
    w('> **The file has changed since these decisions were recorded** (recorded SHA-256 `' + str(record.get('fileSha256')) + '`, now `' + file_hash + '`). Check that the decisions still apply.')
    w('')
for section, text in record.get('notes', {}).items():
    w(f"- *{section}*: {text}")
w('')
w('## How to use it')
w('')
w('1. Read each table and decide each item: **Keep**, **Change to ...** or **Disable**, with a note if the reason matters (for example the data you checked it against). Tell the decision to whoever maintains the file; it is recorded in `review-decisions.json`.')
w('2. The changes are made in the JSON file (or, once configuration management exists, as a new version that a second person approves), never in the code.')
w('3. Review the "What the numbers mean together" questions at the end. They are the ones a single row cannot answer.')
w('')
w('Scores run from 0 to 100 and 100 is always the desirable end. A pattern is an organizational condition; a penalty is its consequence. Both are separate rows because you may accept one and not the other.')
w('')

w('## 1. Questions and how each answer is scored')
w('')
w('| Id | Dimension | Weight | Question | Scored as | Decision |')
w('|---|---|---|---|---|---|')
for q in d['questions']:
    dim, wt = weights.get(q['id'], ('', ''))
    w(f"| `{q['id']}` | {human(dim)} | {wt} | {q_text.get(q['id'], {}).get('description', '')} | {scoring(q)} | {decision('questions', q['id'])} |")
w('')
w('Every question is optional (a missing answer is ignored, never counted as zero). A dimension needs enough of its questions answered to be scored: see section 3.')
w('')

w('## 2. Signals')
w('')
w('A signal is one answer turned into a 0 to 100 value that patterns can test.')
w('')
w('| Signal | Name | From question | Decision |')
w('|---|---|---|---|')
for s in d['signals']:
    w(f"| `{s['id']}` | {signal_name[s['id']]} | {', '.join(s['sources'])} | {decision('signals', s['id'])} |")
w('')

w('## 3. Dimensions, overall weights and score bands')
w('')
w('| Setting | Value | Decision |')
w('|---|---|---|')
for dim, s in d['dimensions']['settings'].items():
    w(f"| Share of {human(dim)} questions that must be answered before it is scored | {s['requiredCoverage']} | {decision('coverage', dim)} |")
for dim, wt in d['overall']['weights'].items():
    w(f"| Weight of {human(dim)} in the overall score | {wt} | {NOT_YET} |")
w(f"| Tolerance when checking that the weights add up to 1 | {d['overall']['tolerance']} | {NOT_YET} |")
w('')
w('| Band (overall score) | From | To | Decision |')
w('|---|---|---|---|')
for b in d['overall']['lisInterpretation']:
    w(f"| {human(b['label'])} | {b['min']} | {b['max']} | {NOT_YET} |")
w('')
w('| Band (a dimension) | From | To | Decision |')
w('|---|---|---|---|')
for b in d['overall']['dimensionInterpretation']:
    w(f"| {human(b['label'])} | {b['min']} | {b['max']} | {NOT_YET} |")
w('')

w('## 4. Patterns and their penalties')
w('')
w('Severity is the impact of a pattern; confidence is the quality of the evidence. A pattern is only detected when enough independent signals back it and the confidence is at least the stated minimum. A severity-based penalty deducts the base amount times a multiplier (the multipliers are listed under each strategy). The maximum is a cap on one penalty, and a cumulative cap limits several penalties on the same target.')
w('')
w('| Pattern | Severity | Detected when | Signals needed | Min. confidence | Penalty | Lowers | Amount | Maximum | Stacking | Cumulative cap | Decision |')
w('|---|---|---|---|---|---|---|---|---|---|---|---|')
pens = {p['trigger']['pattern']: p for p in d['penalties']}
for p in d['patterns']:
    pen = pens.get(p['id'])
    name = pat_text.get(p['id'], {}).get('name', p['code'])
    if pen:
        st = pen['strategy']
        amount = f"{st['baseDeduction']} base (x {', '.join(f'{k.lower()} {v}' for k, v in st['multipliers'].items())})" if st['type'] == 'SEVERITY_BASED' else str(st.get('deduction', st['type']))
        pen_cols = f"`{pen['id']}` {pen_text.get(pen['id'], {}).get('name', '')} | {human(pen['target'])} | {amount} | {pen['maximumDeduction']} | {pen['stackingPolicy'].lower().replace('_', ' ')}, category {pen['category'].lower()} | {pen.get('maximumCumulativeDeduction', '')}"
    else:
        pen_cols = ' | | | | | '
    pattern_decision = decision('patterns', p['id'])
    cell = f"Pattern: {pattern_decision}. Penalty: {NOT_YET}" if pen else f"Pattern: {pattern_decision}"
    w(f"| `{p['id']}` {name} | {human(p['severity'].lower())} | {cond(p['condition'])} | {p['evidence']['minimumSignals']} | {p['evidence']['minimumConfidence']} | {pen_cols} | {cell} |")
w('')

w('### Advice raised by each pattern')
w('')
w('| Pattern | Advice | Decision |')
w('|---|---|---|')
for p in d['patterns']:
    for a in p.get('actions', []):
        r = recs.get(a, {})
        w(f"| `{p['id']}` | **{r.get('title', a)}**: {r.get('description', '')} | {NOT_YET} |")
w('')

w('## 5. Imbalance')
w('')
w('| Rule | Compares | Reported when | Severity | Decision |')
w('|---|---|---|---|---|')
for i in d['imbalances']:
    w(f"| `{i['id']}` | {human(i['left'])} minus {human(i['right'])} | the gap is {'at least' if i['operator'] == 'GAP_GTE' else 'above'} {i['threshold']} (raw scores) | {human(i['severity'].lower())} | {NOT_YET} |")
w('')
w('An imbalance is only reported; it never lowers a score.')
w('')

w('## 6. Answers from several people')
w('')
r = d.get('responses', {})
w('| Setting | Value | Decision |')
w('|---|---|---|')
w(f"| How the answers of several people to one question are combined | {r.get('defaultMethod')} | {NOT_YET} |")
w(f"| Minimum number of responses for a question to count (fewer is treated as missing) | {r.get('minResponses')} | {NOT_YET} |")
w(f"| Share of a signal that must come from answers (participation) | {r.get('signalShare')} | {NOT_YET} |")
w(f"| Number of respondents that gives full participation confidence | {r.get('fullConfidenceResponses')} | {NOT_YET} |")
w('')

w('## What the numbers mean together')
w('')
w('Single rows cannot answer these. They are the questions the specification asks for before a configuration becomes active (specv3 section 42): please answer them, or say how you would find out.')
w('')
w('1. **Distribution.** What overall score should a typical healthy team get, and a struggling one? With the bands above, how many of your teams would you expect in each band? Do these thresholds put them there?')
w('2. **Penalty impact.** If the worst pattern in each dimension fires, how many points should the dimension lose, and the overall score? Are the maximum and cumulative caps in section 4 the limits you intend?')
w('3. **False positives.** Which patterns would you expect to fire often by accident (a single low answer is enough for any pattern that needs only one signal)? Should those need two signals or a higher confidence?')
w('4. **Severity against deduction.** Is the order of severity (critical, high, medium, low) the order of deduction you want? Several high-severity patterns deduct less than medium ones.')
w('5. **Missing data.** Is a share of 0.75 of a dimension\'s questions the right minimum before it is scored, and is a minimum of five responses the right group size for your teams?')
w('6. **Evidence for this review.** What dataset or experience was this checked against (a pilot, past assessments, expert judgement)? Name it, so that the calibration record can refer to it.')
w('')
w('## Sign-off')
w('')
w('| Reviewed by | Role | Date | Evidence or dataset | Version of the file reviewed (hash) |')
w('|---|---|---|---|---|')
w(f"| {record.get('reviewedBy')} (partial review) | project owner | {record.get('date')} | {record.get('evidence') or 'not stated: no dataset or reference was named, so the configuration stays unvalidated'} | `{file_hash}` (SHA-256 of the file) |")
w('')
open(OUT, 'w', encoding='utf-8').write('\n'.join(L) + '\n')
print(len(L), 'lines')
