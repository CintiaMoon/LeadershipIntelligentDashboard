package com.company.leadershipintelligence.service.questionnaire;

import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.ConditionSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.OptionSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.QuestionSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.RuleSpec;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tidies what the builder sent without judging it: text is trimmed, and the identifiers the server owns are assigned when missing
 * (question ids Q01.., option keys O1.., rule ids X-1..). Identifiers that are present are never changed, so a saved questionnaire keeps
 * the ids that answers and rules refer to. Whether the document is publishable is the validator's job.
 */
final class QuestionnaireNormalizer {

    private QuestionnaireNormalizer() {
    }

    static QuestionnaireDocument tidy(QuestionnaireDocument document) {
        Set<String> questionIds = new HashSet<>();
        document.questions().forEach(q -> {
            if (!blank(q.id())) {
                questionIds.add(q.id().trim());
            }
        });
        int nextQuestion = 1;
        List<QuestionSpec> questions = new ArrayList<>();
        for (QuestionSpec q : document.questions()) {
            String id = blank(q.id()) ? null : q.id().trim();
            if (id == null) {
                do {
                    id = "Q" + (nextQuestion < 10 ? "0" : "") + nextQuestion++;
                } while (questionIds.contains(id));
                questionIds.add(id);
            }
            questions.add(new QuestionSpec(id, q.kind(), trim(q.title()), trim(q.text()), trim(q.help()), q.required(), trim(q.dimension()),
                    q.weight(), q.higherIsBetter(), options(q.options()), q.points(),
                    q.labels().stream().map(QuestionnaireNormalizer::trim).toList(), q.min(), q.max(), trim(q.desirableAnswer())));
        }
        Set<String> ruleIds = new HashSet<>();
        document.rules().forEach(r -> {
            if (!blank(r.id())) {
                ruleIds.add(r.id().trim());
            }
        });
        int nextRule = 1;
        List<RuleSpec> rules = new ArrayList<>();
        for (RuleSpec r : document.rules()) {
            String id = blank(r.id()) ? null : r.id().trim();
            if (id == null) {
                do {
                    id = "X-" + nextRule++;
                } while (ruleIds.contains(id));
                ruleIds.add(id);
            }
            rules.add(new RuleSpec(id, trim(r.name()), trim(r.description()), trim(r.explanation()), trim(r.severity()), r.enabled(),
                    condition(r.condition()), r.minimumSignals(), r.minimumConfidence(), r.penalty(), r.recommendation()));
        }
        return new QuestionnaireDocument(trim(document.name()), trim(document.description()), questions, rules);
    }

    private static List<OptionSpec> options(List<OptionSpec> options) {
        Set<String> keys = new HashSet<>();
        options.forEach(o -> {
            if (!blank(o.key())) {
                keys.add(o.key().trim());
            }
        });
        int next = 1;
        List<OptionSpec> result = new ArrayList<>();
        for (OptionSpec o : options) {
            String key = blank(o.key()) ? null : o.key().trim();
            if (key == null) {
                do {
                    key = "O" + next++;
                } while (keys.contains(key));
                keys.add(key);
            }
            result.add(new OptionSpec(key, trim(o.label()), o.score()));
        }
        return result;
    }

    private static ConditionSpec condition(ConditionSpec c) {
        if (c == null) {
            return null;
        }
        String op = trim(c.op());
        return new ConditionSpec(op == null ? null : op.toUpperCase(Locale.ROOT),
                c.conditions() == null ? null : c.conditions().stream().map(QuestionnaireNormalizer::condition).toList(),
                condition(c.condition()), trim(c.question()), trim(c.dimension()), c.value(), c.low(), c.high(), c.values());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
