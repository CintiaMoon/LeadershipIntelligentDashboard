package com.company.leadershipintelligence.domain.service;

import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.valueobject.AssessmentSnapshotId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InputHasherTest {

    private static final AssessmentSnapshotId SNAPSHOT = new AssessmentSnapshotId("snp-1");

    private static String hash(Object... answers) {
        List<ScoringInput> inputs = new ArrayList<>();
        for (int i = 0; i < answers.length; i++) {
            inputs.add(new ScoringInput("Q" + (i + 1), answers[i]));
        }
        return InputHasher.inputHash(SNAPSHOT, inputs);
    }

    @Test
    void hashHasTheDocumentedFormat() {
        assertThat(hash(4, "OFTEN")).matches("sha256:[0-9a-f]{64}");
        assertThat(InputHasher.answersHash(List.of(new ScoringInput("Q1", 1)))).matches("sha256:[0-9a-f]{64}");
    }

    @Test
    void inputOrderDoesNotMatter() {
        List<ScoringInput> inputs = List.of(new ScoringInput("Q1", 4), new ScoringInput("Q2", "OFTEN"), new ScoringInput("Q3", true),
                new ScoringInput("Q4", "text"), new ScoringInput("Q5", null));
        String expected = InputHasher.inputHash(SNAPSHOT, inputs);
        for (int seed = 0; seed < 20; seed++) {
            List<ScoringInput> shuffled = new ArrayList<>(inputs);
            Collections.shuffle(shuffled, new Random(seed));
            assertThat(InputHasher.inputHash(SNAPSHOT, shuffled)).isEqualTo(expected);
        }
    }

    @Test
    void equalNumbersInAnyRepresentationHashTheSame() {
        String expected = hash(4);
        assertThat(hash(4L)).isEqualTo(expected);
        assertThat(hash(new BigDecimal("4"))).isEqualTo(expected);
        assertThat(hash(new BigDecimal("4.00"))).isEqualTo(expected);
        assertThat(hash(4.0d)).isEqualTo(expected);
        assertThat(hash(0)).isEqualTo(hash(new BigDecimal("0.000")));
        assertThat(hash(2.5d)).isEqualTo(hash(new BigDecimal("2.50"))).isNotEqualTo(hash(2.6d));
    }

    @Test
    void differentTypesAndValuesNeverCollide() {
        assertThat(hash("4")).isNotEqualTo(hash(4));
        assertThat(hash(true)).isNotEqualTo(hash("true"));
        assertThat(hash((Object) null)).isNotEqualTo(hash("null")).isNotEqualTo(hash(0)).isNotEqualTo(hash(false));
        assertThat(hash(true)).isNotEqualTo(hash(false));
        assertThat(hash("OFTEN")).isNotEqualTo(hash("often"));
    }

    @Test
    void aMissingQuestionIsNotTheSameAsANullAnswer() {
        assertThat(InputHasher.inputHash(SNAPSHOT, List.of(new ScoringInput("Q1", 1))))
                .isNotEqualTo(InputHasher.inputHash(SNAPSHOT, List.of(new ScoringInput("Q1", 1), new ScoringInput("Q2", null))));
    }

    @Test
    void textBoundariesCannotBeShifted() {
        List<ScoringInput> a = List.of(new ScoringInput("ab", "c"));
        List<ScoringInput> b = List.of(new ScoringInput("a", "bc"));
        assertThat(InputHasher.inputHash(SNAPSHOT, a)).isNotEqualTo(InputHasher.inputHash(SNAPSHOT, b));
        assertThat(InputHasher.answersHash(List.of(new ScoringInput("Q1", "a;Q2=n:1")))).isNotEqualTo(
                InputHasher.answersHash(List.of(new ScoringInput("Q1", "a"), new ScoringInput("Q2", 1))));
    }

    @Test
    void theSnapshotIsPartOfTheInputHashButNotOfTheAnswersHash() {
        List<ScoringInput> inputs = List.of(new ScoringInput("Q1", 1));
        assertThat(InputHasher.inputHash(new AssessmentSnapshotId("snp-1"), inputs))
                .isNotEqualTo(InputHasher.inputHash(new AssessmentSnapshotId("snp-2"), inputs));
        assertThat(InputHasher.answersHash(inputs)).isEqualTo(InputHasher.answersHash(List.of(new ScoringInput("Q1", 1L))));
    }

    @Test
    void unsupportedAnswerTypesAreRejected() {
        assertThatThrownBy(() -> hash(List.of(1))).isInstanceOfSatisfying(com.company.leadershipintelligence.domain.exception.ScoringException.class,
                e -> assertThat(e.code()).isEqualTo(com.company.leadershipintelligence.domain.exception.ScoringErrorCode.INVALID_ANSWER_TYPE));
    }
}
