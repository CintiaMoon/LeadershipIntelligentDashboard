package com.company.leadershipintelligence.service.questionnaire;

import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.ConditionSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.OptionSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.PenaltySpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.QuestionSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.RuleSpec;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The publish checks of specv3.md Section 4.14 on a tidied document (decision D47): every dimension has a scored question, every
 * option has a score within 0 to 100, and the rules refer to things that exist. It lists every problem it finds, in document order,
 * so the builder can show them together. The configuration parser then validates the generated layer with the same rules as any
 * configuration; there is no second validator for the scoring rules themselves.
 */
final class QuestionnaireValidator {

    static final int MAX_QUESTIONS = 200;
    static final int MAX_OPTIONS = 50;
    static final int MAX_RULES = 100;
    static final Set<String> STACKING = Set.of("STACK", "MAX", "MAX_PER_CATEGORY", "PRIORITY", "CAPPED_STACK");
    static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Pattern ID = Pattern.compile("[A-Za-z][A-Za-z0-9]{0,31}");
    private static final Set<String> LEAF_VALUE = Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE");
    private static final Set<String> LEAF_OTHER = Set.of("BETWEEN", "IN", "NOT_IN", "EXISTS", "NOT_EXISTS");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private QuestionnaireValidator() {
    }

    /** Limits that hold even for a draft, so an unfinished document is never unbounded. */
    static List<String> shape(QuestionnaireDocument document) {
        List<String> problems = new ArrayList<>();
        if (document.name() != null && document.name().length() > 200) {
            problems.add("the name is longer than 200 characters");
        }
        if (document.questions().size() > MAX_QUESTIONS) {
            problems.add("a questionnaire has at most " + MAX_QUESTIONS + " questions");
        }
        if (document.rules().size() > MAX_RULES) {
            problems.add("a questionnaire has at most " + MAX_RULES + " rules");
        }
        Set<String> ids = new HashSet<>();
        for (QuestionSpec q : document.questions()) {
            if (q.kind() == null) {
                problems.add("question " + q.id() + " has no type");
            }
            if (q.id() != null && !ID.matcher(q.id()).matches()) {
                problems.add("question id '" + q.id() + "' must be letters and digits, starting with a letter, at most 32 characters");
            }
            if (!ids.add(q.id())) {
                problems.add("question id " + q.id() + " is used twice");
            }
            if (q.options().size() > MAX_OPTIONS) {
                problems.add("question " + q.id() + " has more than " + MAX_OPTIONS + " options");
            }
            if ((q.text() != null && q.text().length() > 1000) || (q.title() != null && q.title().length() > 200)
                    || (q.help() != null && q.help().length() > 1000)) {
                problems.add("question " + q.id() + " has text that is too long");
            }
        }
        return problems;
    }

    static List<String> publishable(QuestionnaireDocument document) {
        List<String> problems = new ArrayList<>(shape(document));
        if (document.name() == null) {
            problems.add("the questionnaire needs a name");
        }
        Map<String, QuestionSpec> byId = new HashMap<>();
        document.questions().forEach(q -> byId.put(q.id(), q));
        if (document.questions().isEmpty()) {
            problems.add("the questionnaire has no questions");
        }
        Map<ScoringDimension, Integer> scored = new EnumMap<>(ScoringDimension.class);
        for (QuestionSpec q : document.questions()) {
            question(q, problems);
            ScoringDimension dimension = dimension(q.dimension());
            if (q.dimension() != null && dimension == null) {
                problems.add("question " + q.id() + " names an unknown dimension '" + q.dimension() + "'");
            }
            if (dimension != null && q.kind() != null && q.kind() != QuestionKind.FREE_TEXT) {
                scored.merge(dimension, 1, Integer::sum);
            }
        }
        for (ScoringDimension d : ScoringDimension.values()) {
            if (!scored.containsKey(d)) {
                problems.add("dimension " + d + " has no scored question");
            }
        }
        Set<String> ruleIds = new HashSet<>();
        for (RuleSpec rule : document.rules()) {
            rule(rule, byId, ruleIds, problems);
        }
        return problems;
    }

    private static void question(QuestionSpec q, List<String> problems) {
        String at = "question " + q.id();
        if (q.text() == null) {
            problems.add(at + " has no text");
        }
        if (q.kind() == null) {
            return;
        }
        if (q.kind() == QuestionKind.FREE_TEXT) {
            if (q.dimension() != null) {
                problems.add(at + " is free text, which is collected and never scored, so it cannot have a dimension");
            }
            return;
        }
        if (q.weight() != null && q.weight().signum() <= 0) {
            problems.add(at + " has a weight that is not positive");
        }
        switch (q.kind()) {
            case SINGLE_CHOICE, MULTI_CHOICE -> choices(q, problems);
            case LIKERT -> {
                if (q.points() == null || (q.points() != 5 && q.points() != 7)) {
                    problems.add(at + " is a Likert scale and needs 5 or 7 points");
                }
                if (!q.labels().isEmpty() && q.points() != null && q.labels().size() != q.points()) {
                    problems.add(at + " has " + q.labels().size() + " labels for " + q.points() + " points");
                }
            }
            case NUMERIC -> {
                if (q.min() == null || q.max() == null || q.min().compareTo(q.max()) >= 0) {
                    problems.add(at + " is numeric and needs a minimum below its maximum");
                }
            }
            case YES_NO -> {
                if (!"YES".equals(q.desirableAnswer()) && !"NO".equals(q.desirableAnswer())) {
                    problems.add(at + " is yes/no and needs to say which answer is desirable (YES or NO)");
                }
            }
            default -> {
            }
        }
    }

    private static void choices(QuestionSpec q, List<String> problems) {
        String at = "question " + q.id();
        if (q.options().size() < 2) {
            problems.add(at + " needs at least two options");
            return;
        }
        Set<String> keys = new HashSet<>();
        int withScore = 0;
        for (OptionSpec o : q.options()) {
            if (o.label() == null) {
                problems.add(at + " has an option without a label");
            }
            if (!keys.add(o.key())) {
                problems.add(at + " uses option key " + o.key() + " twice");
            }
            if (o.score() != null) {
                withScore++;
                if (o.score().signum() < 0 || o.score().compareTo(HUNDRED) > 0) {
                    problems.add(at + " has an option score outside 0 to 100");
                }
            }
        }
        if (withScore != 0 && withScore != q.options().size()) {
            problems.add(at + " scores some options and not others; score all of them or none");
        }
    }

    private static void rule(RuleSpec rule, Map<String, QuestionSpec> questions, Set<String> ruleIds, List<String> problems) {
        String at = "rule " + rule.id();
        if (!ruleIds.add(rule.id())) {
            problems.add("rule id " + rule.id() + " is used twice");
        }
        if (rule.name() == null || rule.description() == null) {
            problems.add(at + " needs a name and a description");
        }
        if (rule.severity() == null || !SEVERITIES.contains(rule.severity())) {
            problems.add(at + " needs a severity (LOW, MEDIUM, HIGH or CRITICAL)");
        }
        if (rule.minimumConfidence() != null
                && (rule.minimumConfidence().signum() < 0 || rule.minimumConfidence().compareTo(BigDecimal.ONE) > 0)) {
            problems.add(at + " has a minimum confidence outside 0 to 1");
        }
        Set<String> operands = new HashSet<>();
        if (rule.condition() == null) {
            problems.add(at + " has no condition");
        } else {
            condition(rule.condition(), at, questions, operands, problems);
        }
        if (rule.minimumSignals() != null
                && (rule.minimumSignals() < 1 || (!operands.isEmpty() && rule.minimumSignals() > operands.size()))) {
            problems.add(at + " asks for " + rule.minimumSignals() + " independent signals but its condition refers to " + operands.size());
        }
        PenaltySpec penalty = rule.penalty();
        if (penalty != null) {
            if (penalty.target() == null || (!"OVERALL".equals(penalty.target()) && dimension(penalty.target()) == null)) {
                problems.add(at + " has a penalty with an unknown target (a dimension or OVERALL)");
            }
            if (!"FIXED".equals(penalty.strategy()) && !"SEVERITY_BASED".equals(penalty.strategy())) {
                problems.add(at + " has a penalty strategy that is not FIXED or SEVERITY_BASED");
            }
            if (penalty.amount() == null || penalty.amount().signum() <= 0 || penalty.amount().compareTo(HUNDRED) > 0) {
                problems.add(at + " has a penalty amount outside 0 to 100");
            }
            String policy = penalty.stackingPolicy() == null ? "STACK" : penalty.stackingPolicy();
            if (!STACKING.contains(policy)) {
                problems.add(at + " has a stacking policy that is not one of " + new TreeSet<>(STACKING));
            }
            if ("CAPPED_STACK".equals(policy) && (penalty.cumulativeCap() == null || penalty.cumulativeCap().signum() <= 0)) {
                problems.add(at + " uses CAPPED_STACK and needs a cumulative cap");
            }
        }
        if (rule.recommendation() != null && (rule.recommendation().title() == null || rule.recommendation().description() == null)) {
            problems.add(at + " has a recommendation without a title or a description");
        }
    }

    private static void condition(ConditionSpec c, String at, Map<String, QuestionSpec> questions, Set<String> operands,
                                  List<String> problems) {
        String op = c.op();
        if (op == null) {
            problems.add(at + " has a condition without an operator");
            return;
        }
        switch (op) {
            case "AND", "OR" -> {
                if (c.conditions() == null || c.conditions().isEmpty()) {
                    problems.add(at + " has an " + op + " without conditions");
                } else {
                    c.conditions().forEach(child -> condition(child, at, questions, operands, problems));
                }
            }
            case "NOT" -> {
                if (c.condition() == null) {
                    problems.add(at + " has a NOT without a condition");
                } else {
                    condition(c.condition(), at, questions, operands, problems);
                }
            }
            default -> leaf(c, op, at, questions, operands, problems);
        }
    }

    private static void leaf(ConditionSpec c, String op, String at, Map<String, QuestionSpec> questions, Set<String> operands,
                             List<String> problems) {
        if (!LEAF_VALUE.contains(op) && !LEAF_OTHER.contains(op)) {
            problems.add(at + " uses the unknown operator " + op);
            return;
        }
        if ((c.question() == null) == (c.dimension() == null)) {
            problems.add(at + " needs exactly one of question or dimension in each comparison");
            return;
        }
        if (c.question() != null) {
            QuestionSpec q = questions.get(c.question());
            if (q == null) {
                problems.add(at + " refers to an unknown question " + c.question());
            } else if (q.kind() == QuestionKind.FREE_TEXT) {
                problems.add(at + " refers to free-text question " + c.question() + ", which has no value to compare");
            } else {
                operands.add("question:" + c.question());
            }
        } else if (dimension(c.dimension()) == null) {
            problems.add(at + " refers to an unknown dimension " + c.dimension());
        } else {
            operands.add("dimension:" + c.dimension());
        }
        boolean ok = switch (op) {
            case "BETWEEN" -> c.low() != null && c.high() != null && c.low().compareTo(c.high()) <= 0;
            case "IN", "NOT_IN" -> c.values() != null && !c.values().isEmpty();
            case "EXISTS", "NOT_EXISTS" -> true;
            default -> c.value() != null;
        };
        if (!ok) {
            problems.add(at + " has a " + op + " comparison with missing or inconsistent values");
        }
    }

    static ScoringDimension dimension(String name) {
        if (name == null) {
            return null;
        }
        try {
            return ScoringDimension.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
