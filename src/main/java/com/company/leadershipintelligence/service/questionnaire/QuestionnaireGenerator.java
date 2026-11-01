package com.company.leadershipintelligence.service.questionnaire;

import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.ConditionSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.OptionSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.PenaltySpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.QuestionSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.RuleSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a publishable builder document into a complete scoring configuration layer (decision D47). The layer holds the questionnaire's
 * own questions, signals, dimension mappings, rules, penalties and recommendations; it leaves the dimension settings, the overall
 * weights and interpretation and the response settings to the platform default below it. Lists replace the platform's lists as a whole,
 * and the display sections that are keyed by identifier are replaced with the {@code $replace} marker so identifiers of the platform
 * catalog can never leak into a questionnaire. There is no imbalance rule and no platform recommendation in the layer.
 *
 * <p>Every scored or collected question except free text gets a configuration question and a signal named {@code SIG_<question id>}
 * whose value is the normalized 0 to 100 answer. Free text is collected and never scored, so it stays in the questionnaire only.
 * The generated numbers (even option spacing, the SEVERITY_BASED multipliers, the default weight of 1) are unvalidated authoring
 * defaults, not calibrated values. The output is deterministic for a document.
 */
final class QuestionnaireGenerator {

    static final String SIGNAL_PREFIX = "SIG_";
    private static final JsonNodeFactory F = JsonNodeFactory.instance;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DEFAULT_CONFIDENCE = new BigDecimal("0.5");
    private static final Map<String, BigDecimal> SEVERITY_MULTIPLIERS = new LinkedHashMap<>();

    static {
        SEVERITY_MULTIPLIERS.put("LOW", BigDecimal.ZERO);
        SEVERITY_MULTIPLIERS.put("MEDIUM", new BigDecimal("0.4"));
        SEVERITY_MULTIPLIERS.put("HIGH", new BigDecimal("0.7"));
        SEVERITY_MULTIPLIERS.put("CRITICAL", BigDecimal.ONE);
    }

    private QuestionnaireGenerator() {
    }

    static String signalOf(String questionId) {
        return SIGNAL_PREFIX + questionId;
    }

    static ObjectNode layer(QuestionnaireDocument document) {
        ObjectNode root = F.objectNode();
        ArrayNode questions = root.putArray("questions");
        ArrayNode signals = root.putArray("signals");
        ArrayNode mappings = F.arrayNode();
        ObjectNode questionText = F.objectNode();
        ObjectNode signalText = F.objectNode();
        Map<String, QuestionSpec> byId = new LinkedHashMap<>();
        for (QuestionSpec q : document.questions()) {
            byId.put(q.id(), q);
            if (q.kind() == QuestionKind.FREE_TEXT) {
                continue;
            }
            questions.add(question(q));
            ObjectNode signal = signals.addObject();
            signal.put("id", signalOf(q.id()));
            signal.put("dataType", "NUMBER");
            signal.putArray("sources").add(q.id());
            signal.put("derivationVersion", "1");
            if (q.dimension() != null) {
                ObjectNode mapping = mappings.addObject();
                mapping.put("question", q.id());
                mapping.put("dimension", q.dimension());
                mapping.put("weight", q.weight() == null ? BigDecimal.ONE : q.weight());
            }
            entry(questionText, q.id(), title(q), q.text());
            entry(signalText, signalOf(q.id()), title(q), q.text());
        }
        root.putObject("dimensions").set("mappings", mappings);

        ArrayNode patterns = root.putArray("patterns");
        ArrayNode penalties = root.putArray("penalties");
        root.putArray("imbalances");
        ArrayNode recommendations = root.putArray("recommendations");
        ObjectNode patternText = F.objectNode();
        ObjectNode penaltyText = F.objectNode();
        int order = 0;
        int number = 0;
        for (RuleSpec rule : document.rules()) {
            number++;
            patterns.add(pattern(rule, byId));
            entry(patternText, rule.id(), rule.name(), rule.description(), rule.explanation());
            if (rule.penalty() != null) {
                String penaltyId = penaltyId(rule.id());
                penalties.add(penalty(rule, penaltyId, order++, patternCategory(rule, byId)));
                entry(penaltyText, penaltyId, rule.name() + " penalty", "Deduction caused by: " + rule.name() + ".", rule.explanation());
            }
            if (rule.recommendation() != null) {
                ObjectNode recommendation = recommendations.addObject();
                recommendation.put("key", recommendationKey(rule.id()));
                recommendation.put("title", rule.recommendation().title());
                recommendation.put("description", rule.recommendation().description());
                recommendation.put("priority", rule.recommendation().priority() == null ? number * 10 : rule.recommendation().priority());
            }
        }
        ObjectNode presentation = root.putObject("presentation");
        presentation.set("questions", replace(questionText));
        presentation.set("signals", replace(signalText));
        presentation.set("patterns", replace(patternText));
        presentation.set("penalties", replace(penaltyText));
        return root;
    }

    static String penaltyId(String ruleId) {
        return ruleId.replaceFirst("^X-", "X-P");
    }

    static String recommendationKey(String ruleId) {
        return "RECOMMEND_" + ruleId.replace('-', '_').toUpperCase(java.util.Locale.ROOT);
    }

    // ---------------- questions ----------------

    private static ObjectNode question(QuestionSpec q) {
        ObjectNode node = F.objectNode();
        node.put("id", q.id());
        node.put("missingPolicy", "IGNORE");
        ObjectNode n = node.putObject("normalization");
        String direction = Boolean.FALSE.equals(q.higherIsBetter()) ? "NEGATIVE" : "POSITIVE";
        switch (q.kind()) {
            case SINGLE_CHOICE, MULTI_CHOICE -> {
                node.put("type", q.kind() == QuestionKind.SINGLE_CHOICE ? "MULTIPLE_CHOICE" : "MULTI_SELECT");
                ObjectNode values = n.putObject("values");
                List<OptionSpec> options = q.options();
                for (int i = 0; i < options.size(); i++) {
                    values.put(options.get(i).key(), score(options, i));
                }
                n.put("unknownOption", "REJECT");
            }
            case RATING -> scale(node, n, 1, 5, direction);
            case LIKERT -> scale(node, n, 1, q.points() == null ? 5 : q.points(), direction);
            case NPS -> scale(node, n, 0, 10, direction);
            case NUMERIC -> {
                node.put("type", "NUMBER");
                n.put("min", q.min());
                n.put("max", q.max());
                n.put("direction", direction);
                n.put("outOfRangePolicy", "CLAMP");
            }
            case YES_NO -> {
                node.put("type", "BOOLEAN");
                boolean yesDesirable = "YES".equals(q.desirableAnswer());
                n.put("trueValue", yesDesirable ? HUNDRED : BigDecimal.ZERO);
                n.put("falseValue", yesDesirable ? BigDecimal.ZERO : HUNDRED);
            }
            default -> throw new IllegalStateException("no configuration question for " + q.kind());
        }
        return node;
    }

    private static void scale(ObjectNode node, ObjectNode n, int min, int max, String direction) {
        node.put("type", "SCALE");
        n.put("min", min);
        n.put("max", max);
        n.put("direction", direction);
        n.put("outOfRangePolicy", "REJECT");
        n.put("allowDecimals", false);
    }

    /** An explicit score, or the options spaced evenly from 0 (first) to 100 (last) in the order shown. */
    private static BigDecimal score(List<OptionSpec> options, int index) {
        BigDecimal given = options.get(index).score();
        if (given != null) {
            return given;
        }
        BigDecimal spaced = HUNDRED.multiply(BigDecimal.valueOf(index)).divide(BigDecimal.valueOf(options.size() - 1), 2, RoundingMode.HALF_UP);
        return new BigDecimal(spaced.stripTrailingZeros().toPlainString());
    }

    // ---------------- rules ----------------

    private static ObjectNode pattern(RuleSpec rule, Map<String, QuestionSpec> questions) {
        ObjectNode node = F.objectNode();
        node.put("id", rule.id());
        node.put("code", "AUTHORED_" + rule.id().replace('-', '_').toUpperCase(java.util.Locale.ROOT));
        node.put("version", 1);
        node.put("category", patternCategory(rule, questions));
        node.put("severity", rule.severity());
        node.put("enabled", rule.enabled() == null || rule.enabled());
        node.put("penaltyEligible", rule.penalty() != null);
        ObjectNode evidence = node.putObject("evidence");
        evidence.put("minimumSignals", rule.minimumSignals() == null ? 1 : rule.minimumSignals());
        evidence.put("minimumConfidence", rule.minimumConfidence() == null ? DEFAULT_CONFIDENCE : rule.minimumConfidence());
        ArrayNode actions = node.putArray("actions");
        if (rule.recommendation() != null) {
            actions.add(recommendationKey(rule.id()));
        }
        node.set("condition", condition(rule.condition()));
        return node;
    }

    /** The dimension all of the rule's operands belong to, else CROSS_DIMENSIONAL. */
    private static String patternCategory(RuleSpec rule, Map<String, QuestionSpec> questions) {
        Set<String> dimensions = new LinkedHashSet<>();
        collectDimensions(rule.condition(), questions, dimensions);
        return dimensions.size() == 1 && !dimensions.contains(null) ? dimensions.iterator().next() : "CROSS_DIMENSIONAL";
    }

    private static void collectDimensions(ConditionSpec c, Map<String, QuestionSpec> questions, Set<String> out) {
        if (c == null) {
            return;
        }
        if (c.question() != null) {
            QuestionSpec q = questions.get(c.question());
            out.add(q == null ? null : q.dimension());
        }
        if (c.dimension() != null) {
            out.add(c.dimension());
        }
        collectDimensions(c.condition(), questions, out);
        if (c.conditions() != null) {
            c.conditions().forEach(child -> collectDimensions(child, questions, out));
        }
    }

    private static ObjectNode condition(ConditionSpec c) {
        ObjectNode node = F.objectNode();
        node.put("operator", c.op());
        switch (c.op()) {
            case "AND", "OR" -> {
                ArrayNode list = node.putArray("conditions");
                c.conditions().forEach(child -> list.add(condition(child)));
            }
            case "NOT" -> node.set("condition", condition(c.condition()));
            default -> {
                if (c.question() != null) {
                    node.put("signal", signalOf(c.question()));
                } else {
                    node.put("dimension", c.dimension());
                }
                switch (c.op()) {
                    case "BETWEEN" -> {
                        node.put("low", c.low());
                        node.put("high", c.high());
                    }
                    case "IN", "NOT_IN" -> {
                        ArrayNode values = node.putArray("values");
                        c.values().forEach(values::add);
                    }
                    case "EXISTS", "NOT_EXISTS" -> {
                    }
                    default -> node.put("value", c.value());
                }
            }
        }
        return node;
    }

    private static ObjectNode penalty(RuleSpec rule, String id, int order, String patternCategory) {
        PenaltySpec p = rule.penalty();
        ObjectNode node = F.objectNode();
        node.put("id", id);
        node.put("code", "AUTHORED_" + id.replace('-', '_').toUpperCase(java.util.Locale.ROOT));
        node.put("version", 1);
        ObjectNode trigger = node.putObject("trigger");
        trigger.put("pattern", rule.id());
        trigger.put("version", 1);
        node.put("target", p.target());
        ObjectNode strategy = node.putObject("strategy");
        strategy.put("type", p.strategy());
        if ("FIXED".equals(p.strategy())) {
            strategy.put("deduction", p.amount());
        } else {
            strategy.put("baseDeduction", p.amount());
            ObjectNode multipliers = strategy.putObject("multipliers");
            SEVERITY_MULTIPLIERS.forEach(multipliers::put);
        }
        node.put("maximumDeduction", p.amount());
        node.put("category", p.category() == null || p.category().isBlank() ? patternCategory : p.category());
        node.put("priority", p.priority() == null ? 100 : p.priority());
        node.put("order", order);
        String policy = p.stackingPolicy() == null ? "STACK" : p.stackingPolicy();
        node.put("stackingPolicy", policy);
        node.put("enabled", rule.enabled() == null || rule.enabled());
        node.put("explanationKey", "penalty." + id);
        if ("CAPPED_STACK".equals(policy)) {
            node.put("maximumCumulativeDeduction", p.cumulativeCap());
        }
        return node;
    }

    // ---------------- display text ----------------

    private static String title(QuestionSpec q) {
        if (q.title() != null) {
            return q.title();
        }
        return q.text().length() <= 80 ? q.text() : q.text().substring(0, 77) + "...";
    }

    private static void entry(ObjectNode into, String key, String name, String description) {
        entry(into, key, name, description, null);
    }

    private static void entry(ObjectNode into, String key, String name, String description, String explanation) {
        ObjectNode entry = into.putObject(key);
        entry.put("name", name);
        entry.put("description", description);
        if (explanation != null) {
            entry.put("explanation", explanation);
        }
    }

    private static JsonNode replace(ObjectNode value) {
        ObjectNode marker = F.objectNode();
        marker.set("$replace", value);
        return marker;
    }
}
