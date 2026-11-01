package com.company.leadershipintelligence.service.questionnaire;

import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.ConditionSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.OptionSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.PenaltySpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.QuestionSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.RecommendationSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.RuleSpec;

import java.math.BigDecimal;
import java.util.List;

/** Builder documents for the questionnaire tests. Thresholds and amounts are made up for the tests. */
public final class QuestionnaireFixtures {

    private QuestionnaireFixtures() {
    }

    public static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    /** A rating, 1 to 5, higher is better. */
    public static QuestionSpec rating(String id, String dimension) {
        return new QuestionSpec(id, QuestionKind.RATING, null, "How is " + id + "?", null, true, dimension, null, null, List.of(), null,
                List.of(), null, null, null);
    }

    public static QuestionSpec singleChoice(String id, String dimension, String... keys) {
        List<OptionSpec> options = java.util.Arrays.stream(keys).map(k -> new OptionSpec(k, k.toLowerCase(), null)).toList();
        return new QuestionSpec(id, QuestionKind.SINGLE_CHOICE, "Choice " + id, "Pick one for " + id, null, false, dimension, null, null, options,
                null, List.of(), null, null, null);
    }

    public static QuestionSpec yesNo(String id, String dimension, String desirable) {
        return new QuestionSpec(id, QuestionKind.YES_NO, null, "Is " + id + " so?", null, false, dimension, null, null, List.of(), null,
                List.of(), null, null, desirable);
    }

    /** A number from 0 to 10 where lower is better. */
    public static QuestionSpec numberLowerIsBetter(String id, String dimension) {
        return new QuestionSpec(id, QuestionKind.NUMERIC, null, "How many " + id + "?", null, false, dimension, n("2"), false, List.of(), null,
                List.of(), n("0"), n("10"), null);
    }

    public static QuestionSpec freeText(String id) {
        return new QuestionSpec(id, QuestionKind.FREE_TEXT, null, "Anything else?", null, false, null, null, null, List.of(), null, List.of(),
                null, null, null);
    }

    public static QuestionSpec multiChoiceUnscored(String id, String... keys) {
        List<OptionSpec> options = java.util.Arrays.stream(keys).map(k -> new OptionSpec(k, k.toLowerCase(), null)).toList();
        return new QuestionSpec(id, QuestionKind.MULTI_CHOICE, null, "Which apply for " + id + "?", null, false, null, null, null, options, null,
                List.of(), null, null, null);
    }

    public static ConditionSpec leq(String question, String value) {
        return new ConditionSpec("LTE", null, null, question, null, n(value), null, null, null);
    }

    public static ConditionSpec and(ConditionSpec... children) {
        return new ConditionSpec("AND", List.of(children), null, null, null, null, null, null, null);
    }

    public static RuleSpec rule(String name, ConditionSpec condition, PenaltySpec penalty, RecommendationSpec recommendation) {
        return new RuleSpec(null, name, name + " is happening.", "It was detected because of the answers.", "HIGH", null, condition, 2,
                n("0.5"), penalty, recommendation);
    }

    /**
     * Four dimensions with one scored question each (Q01 rating, Q02 choice, Q03 yes/no where NO is desirable, Q04 number where lower is
     * better), an unscored multiple choice (Q05) and a free text (Q06), and one rule that fires when Q01 and Q03 are both low.
     */
    public static QuestionnaireDocument full() {
        PenaltySpec penalty = new PenaltySpec("TEAM_HEALTH", "SEVERITY_BASED", n("10"), "CAPPED_STACK", n("20"), null, null);
        return new QuestionnaireDocument("Team pulse", "A short check", List.of(
                rating("Q01", "TEAM_HEALTH"),
                singleChoice("Q02", "DELIVERY_STABILITY", "NEVER", "SOMETIMES", "ALWAYS"),
                yesNo("Q03", "TECHNICAL_RISK", "NO"),
                numberLowerIsBetter("Q04", "LEADERSHIP_LOAD"),
                multiChoiceUnscored("Q05", "A", "B"),
                freeText("Q06")), List.of(
                rule("Burnout and incidents", and(leq("Q01", "25"), leq("Q03", "50")), penalty,
                        new RecommendationSpec("Look at capacity", "Compare the plan with what the team can do.", null))));
    }

    public static QuestionnaireDocument without(QuestionnaireDocument document, String questionId) {
        return new QuestionnaireDocument(document.name(), document.description(),
                document.questions().stream().filter(q -> !q.id().equals(questionId)).toList(), document.rules());
    }
}
