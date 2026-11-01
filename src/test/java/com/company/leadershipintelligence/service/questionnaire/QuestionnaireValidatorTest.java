package com.company.leadershipintelligence.service.questionnaire;

import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.ConditionSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.OptionSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.PenaltySpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.QuestionSpec;
import com.company.leadershipintelligence.service.questionnaire.QuestionnaireDocument.RuleSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.company.leadershipintelligence.service.questionnaire.QuestionnaireFixtures.full;
import static com.company.leadershipintelligence.service.questionnaire.QuestionnaireFixtures.n;
import static org.assertj.core.api.Assertions.assertThat;

class QuestionnaireValidatorTest {

    private static List<String> problems(QuestionnaireDocument document) {
        return QuestionnaireValidator.publishable(QuestionnaireNormalizer.tidy(document));
    }

    private static QuestionnaireDocument withQuestions(List<QuestionSpec> questions) {
        QuestionnaireDocument f = full();
        return new QuestionnaireDocument(f.name(), f.description(), questions, List.of());
    }

    private static QuestionnaireDocument withRule(RuleSpec rule) {
        QuestionnaireDocument f = full();
        return new QuestionnaireDocument(f.name(), f.description(), f.questions(), List.of(rule));
    }

    @Test
    void aCompleteQuestionnaireHasNoProblems() {
        assertThat(problems(full())).isEmpty();
    }

    @Test
    void everyDimensionNeedsAScoredQuestionAndTheProblemsAreListedTogether() {
        QuestionnaireDocument noTechnical = QuestionnaireFixtures.without(QuestionnaireFixtures.without(full(), "Q03"), "Q04");
        assertThat(problems(new QuestionnaireDocument(noTechnical.name(), null, noTechnical.questions(), List.of())))
                .containsExactly("dimension TECHNICAL_RISK has no scored question", "dimension LEADERSHIP_LOAD has no scored question");
    }

    @Test
    void anUnscoredQuestionDoesNotCountTowardsADimension() {
        List<QuestionSpec> questions = new ArrayList<>(full().questions());
        questions.set(2, QuestionnaireFixtures.yesNo("Q03", null, "NO"));
        assertThat(problems(withQuestions(questions))).containsExactly("dimension TECHNICAL_RISK has no scored question");
    }

    @Test
    void anEmptyQuestionnaireAndAMissingNameAreReported() {
        assertThat(problems(new QuestionnaireDocument(null, null, List.of(), List.of()))).contains("the questionnaire needs a name",
                "the questionnaire has no questions");
    }

    @Test
    void freeTextCannotBeScored() {
        List<QuestionSpec> questions = new ArrayList<>(full().questions());
        QuestionSpec text = questions.get(5);
        questions.set(5, new QuestionSpec(text.id(), text.kind(), null, text.text(), null, false, "TEAM_HEALTH", null, null, List.of(), null,
                List.of(), null, null, null));
        assertThat(problems(withQuestions(questions))).anyMatch(p -> p.contains("free text") && p.contains("cannot have a dimension"));
    }

    @Test
    void choiceQuestionsNeedTwoOptionsWithLabelsUniqueKeysAndAllOrNoScoresWithinRange() {
        assertThat(problems(withQuestions(replace(1, QuestionnaireFixtures.singleChoice("Q02", "DELIVERY_STABILITY", "ONLY")))))
                .anyMatch(p -> p.contains("needs at least two options"));
        List<OptionSpec> some = List.of(new OptionSpec("A", "a", n("10")), new OptionSpec("B", "b", null));
        assertThat(problems(withQuestions(replace(1, choice(some))))).anyMatch(p -> p.contains("scores some options and not others"));
        List<OptionSpec> outOfRange = List.of(new OptionSpec("A", "a", n("-1")), new OptionSpec("B", "b", n("101")));
        assertThat(problems(withQuestions(replace(1, choice(outOfRange))))).anyMatch(p -> p.contains("outside 0 to 100"));
        List<OptionSpec> duplicate = List.of(new OptionSpec("A", "a", null), new OptionSpec("A", "b", null));
        assertThat(problems(withQuestions(replace(1, choice(duplicate))))).anyMatch(p -> p.contains("uses option key A twice"));
        List<OptionSpec> unlabeled = List.of(new OptionSpec("A", " ", null), new OptionSpec("B", "b", null));
        assertThat(problems(withQuestions(replace(1, choice(unlabeled))))).anyMatch(p -> p.contains("option without a label"));
    }

    @Test
    void numericLikertYesNoAndWeightAreChecked() {
        QuestionSpec reversed = new QuestionSpec("Q04", QuestionKind.NUMERIC, null, "t", null, false, "LEADERSHIP_LOAD", null, null, List.of(), null,
                List.of(), n("10"), n("10"), null);
        assertThat(problems(withQuestions(replace(3, reversed)))).anyMatch(p -> p.contains("minimum below its maximum"));
        QuestionSpec likert = new QuestionSpec("Q04", QuestionKind.LIKERT, null, "t", null, false, "LEADERSHIP_LOAD", null, null, List.of(), 6,
                List.of(), null, null, null);
        assertThat(problems(withQuestions(replace(3, likert)))).anyMatch(p -> p.contains("5 or 7 points"));
        QuestionSpec labels = new QuestionSpec("Q04", QuestionKind.LIKERT, null, "t", null, false, "LEADERSHIP_LOAD", null, null, List.of(), 5,
                List.of("a", "b"), null, null, null);
        assertThat(problems(withQuestions(replace(3, labels)))).anyMatch(p -> p.contains("2 labels for 5 points"));
        assertThat(problems(withQuestions(replace(2, QuestionnaireFixtures.yesNo("Q03", "TECHNICAL_RISK", "MAYBE")))))
                .anyMatch(p -> p.contains("which answer is desirable"));
        QuestionSpec heavy = new QuestionSpec("Q01", QuestionKind.RATING, null, "t", null, false, "TEAM_HEALTH", n("0"), null, List.of(), null,
                List.of(), null, null, null);
        assertThat(problems(withQuestions(replace(0, heavy)))).anyMatch(p -> p.contains("weight that is not positive"));
    }

    @Test
    void shapeLimitsHoldEvenForADraft() {
        List<QuestionSpec> twice = List.of(QuestionnaireFixtures.rating("Q01", null), QuestionnaireFixtures.rating("Q01", null),
                QuestionnaireFixtures.rating("1bad", null));
        assertThat(QuestionnaireValidator.shape(QuestionnaireNormalizer.tidy(new QuestionnaireDocument("n", null, twice, List.of()))))
                .anyMatch(p -> p.contains("Q01 is used twice")).anyMatch(p -> p.contains("'1bad'"));
        List<QuestionSpec> tooMany = new ArrayList<>();
        for (int i = 0; i < QuestionnaireValidator.MAX_QUESTIONS + 1; i++) {
            tooMany.add(QuestionnaireFixtures.rating("Q" + i, null));
        }
        assertThat(QuestionnaireValidator.shape(new QuestionnaireDocument("n", null, tooMany, List.of()))).anyMatch(p -> p.contains("at most 200 questions"));
    }

    @Test
    void rulesMustReferToRealQuestionsAndDimensionsAndUseKnownOperators() {
        assertThat(problems(withRule(rule(new ConditionSpec("LTE", null, null, "Q99", null, n("5"), null, null, null)))))
                .anyMatch(p -> p.contains("unknown question Q99"));
        assertThat(problems(withRule(rule(new ConditionSpec("LTE", null, null, "Q06", null, n("5"), null, null, null)))))
                .anyMatch(p -> p.contains("free-text question Q06"));
        assertThat(problems(withRule(rule(new ConditionSpec("LTE", null, null, null, "NOPE", n("5"), null, null, null)))))
                .anyMatch(p -> p.contains("unknown dimension NOPE"));
        assertThat(problems(withRule(rule(new ConditionSpec("LTE", null, null, "Q01", "TEAM_HEALTH", n("5"), null, null, null)))))
                .anyMatch(p -> p.contains("exactly one of question or dimension"));
        assertThat(problems(withRule(rule(new ConditionSpec("CLOSE_TO", null, null, "Q01", null, n("5"), null, null, null)))))
                .anyMatch(p -> p.contains("unknown operator CLOSE_TO"));
        assertThat(problems(withRule(rule(new ConditionSpec("BETWEEN", null, null, "Q01", null, null, n("9"), n("1"), null)))))
                .anyMatch(p -> p.contains("BETWEEN comparison with missing or inconsistent values"));
        assertThat(problems(withRule(rule(new ConditionSpec("AND", List.of(), null, null, null, null, null, null, null)))))
                .anyMatch(p -> p.contains("AND without conditions"));
        assertThat(problems(withRule(rule(new ConditionSpec("NOT", null, null, null, null, null, null, null, null)))))
                .anyMatch(p -> p.contains("NOT without a condition"));
    }

    @Test
    void aDimensionOperandIsAllowedAndNestedConditionsAreChecked() {
        ConditionSpec raw = new ConditionSpec("LT", null, null, null, "TEAM_HEALTH", n("40"), null, null, null);
        ConditionSpec nested = new ConditionSpec("NOT", null, new ConditionSpec("OR", List.of(raw, QuestionnaireFixtures.leq("Q01", "20")),
                null, null, null, null, null, null, null), null, null, null, null, null, null);
        assertThat(problems(withRule(rule(nested)))).isEmpty();
    }

    @Test
    void ruleMetadataAndPenaltiesAreChecked() {
        RuleSpec bad = new RuleSpec(null, null, null, null, "SEVERE", null, QuestionnaireFixtures.leq("Q01", "20"), 5, n("1.5"),
                new PenaltySpec("NOWHERE", "PROPORTIONAL", n("0"), "EXCLUSIVE_GROUP", null, null, null),
                new QuestionnaireDocument.RecommendationSpec(null, null, null));
        assertThat(problems(withRule(bad))).anyMatch(p -> p.contains("needs a name and a description"))
                .anyMatch(p -> p.contains("needs a severity"))
                .anyMatch(p -> p.contains("minimum confidence outside 0 to 1"))
                .anyMatch(p -> p.contains("asks for 5 independent signals but its condition refers to 1"))
                .anyMatch(p -> p.contains("unknown target"))
                .anyMatch(p -> p.contains("not FIXED or SEVERITY_BASED"))
                .anyMatch(p -> p.contains("penalty amount outside 0 to 100"))
                .anyMatch(p -> p.contains("stacking policy that is not one of"))
                .anyMatch(p -> p.contains("recommendation without a title or a description"));
        PenaltySpec uncapped = new PenaltySpec("OVERALL", "FIXED", n("5"), "CAPPED_STACK", null, null, null);
        assertThat(problems(withRule(new RuleSpec(null, "r", "d", null, "LOW", null, QuestionnaireFixtures.leq("Q01", "20"), null, null, uncapped, null))))
                .anyMatch(p -> p.contains("needs a cumulative cap"));
    }

    @Test
    void theNormalizerAssignsIdentifiersWithoutChangingExistingOnesAndTrimsText() {
        QuestionSpec withId = QuestionnaireFixtures.rating("Q02", "TEAM_HEALTH");
        QuestionSpec without = new QuestionSpec(" ", QuestionKind.RATING, "  title  ", "  text  ", null, false, null, null, null,
                List.of(new OptionSpec(null, " x ", null), new OptionSpec("O1", "y", null), new OptionSpec("  ", "z", null)), null, List.of(),
                null, null, null);
        QuestionnaireDocument tidy = QuestionnaireNormalizer.tidy(new QuestionnaireDocument("  name ", "   ", List.of(without, withId), List.of(
                new RuleSpec("X-1", "a", "b", null, "LOW", null, QuestionnaireFixtures.leq("Q02", "5"), null, null, null, null),
                new RuleSpec(null, "c", "d", null, "low", null, new ConditionSpec(" lte ", null, null, " Q02 ", null, n("5"), null, null, null),
                        null, null, null, null))));
        assertThat(tidy.name()).isEqualTo("name");
        assertThat(tidy.description()).isNull();
        assertThat(tidy.questions()).extracting(QuestionSpec::id).containsExactly("Q01", "Q02");
        assertThat(tidy.questions().get(0).title()).isEqualTo("title");
        assertThat(tidy.questions().get(0).text()).isEqualTo("text");
        assertThat(tidy.questions().get(0).options()).extracting(OptionSpec::key).containsExactly("O2", "O1", "O3");
        assertThat(tidy.rules()).extracting(RuleSpec::id).containsExactly("X-1", "X-2");
        assertThat(tidy.rules().get(1).condition().op()).isEqualTo("LTE");
        assertThat(tidy.rules().get(1).condition().question()).isEqualTo("Q02");
    }

    private static List<QuestionSpec> replace(int index, QuestionSpec question) {
        List<QuestionSpec> questions = new ArrayList<>(full().questions());
        questions.set(index, question);
        return questions;
    }

    private static QuestionSpec choice(List<OptionSpec> options) {
        return new QuestionSpec("Q02", QuestionKind.SINGLE_CHOICE, null, "t", null, false, "DELIVERY_STABILITY", null, null, options, null,
                List.of(), null, null, null);
    }

    private static RuleSpec rule(ConditionSpec condition) {
        return new RuleSpec(null, "r", "d", null, "LOW", null, condition, null, null, null, null);
    }
}
