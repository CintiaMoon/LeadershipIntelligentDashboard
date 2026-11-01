package com.company.leadershipintelligence.domain.service;

import com.company.leadershipintelligence.domain.enums.ResponseScope;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.model.ScopedResponse;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.valueobject.AssessmentSnapshotId;
import com.company.leadershipintelligence.repository.persistence.mapper.AnswerJson;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static com.company.leadershipintelligence.domain.Fixtures.assertCode;
import static org.assertj.core.api.Assertions.assertThat;

/** A list of options is a set: its order and repeats never change a hash or a stored answer (specv3.md Section 4.2.1). */
class SelectionAnswerHashTest {

    private static final AssessmentSnapshotId SNAPSHOT = new AssessmentSnapshotId("snp-1");

    private static String hash(Object answer) {
        return InputHasher.inputHash(SNAPSHOT, List.of(new ScoringInput("Q", answer)));
    }

    @Test
    void theOrderOfTheChosenOptionsDoesNotChangeTheHash() {
        List<String> options = new ArrayList<>(List.of("A", "B", "C", "D"));
        String expected = hash(options);
        Random random = new Random(11);
        for (int i = 0; i < 20; i++) {
            Collections.shuffle(options, random);
            assertThat(hash(options)).isEqualTo(expected);
        }
    }

    @Test
    void aRepeatedOptionIsTheSameAsOne() {
        assertThat(hash(List.of("A", "A", "B"))).isEqualTo(hash(List.of("B", "A")));
    }

    @Test
    void differentSelectionsHashDifferentlyAndNeverLikeAScalar() {
        assertThat(hash(List.of("A"))).isNotEqualTo(hash(List.of("B")));
        assertThat(hash(List.of("A"))).isNotEqualTo(hash(List.of("A", "B")));
        assertThat(hash(List.of("A"))).isNotEqualTo(hash("A"));
        assertThat(hash(List.of())).isNotEqualTo(hash(null));
        // the option text is length-prefixed, so splitting or joining options cannot collide
        assertThat(hash(List.of("A,B"))).isNotEqualTo(hash(List.of("A", "B")));
    }

    @Test
    void aListHoldsOnlyOptionIdentifiers() {
        assertCode(() -> hash(List.of("A", 3)), ScoringErrorCode.INVALID_ANSWER_TYPE);
    }

    @Test
    void theScopedHashTreatsSelectionsTheSameWay() {
        ScopedResponse one = ScopedResponse.member("Q", List.of("A", "B"), "alpha");
        ScopedResponse other = ScopedResponse.member("Q", List.of("B", "A", "A"), "alpha");
        assertThat(InputHasher.scopedInputHash(SNAPSHOT, List.of(one), null)).isEqualTo(InputHasher.scopedInputHash(SNAPSHOT, List.of(other), null));
        assertThat(one.scope()).isEqualTo(ResponseScope.MEMBER);
    }

    @Test
    void aSelectionIsStoredAsASortedSetAndReadBackAsAList() {
        assertThat(AnswerJson.write("Q", List.of("B", "A", "B"))).isEqualTo("[\"A\",\"B\"]");
        assertThat(AnswerJson.read("[\"A\",\"B\"]")).isEqualTo(List.of("A", "B"));
        assertThat(AnswerJson.read(AnswerJson.write("Q", List.of("Z", "A")))).isEqualTo(List.of("A", "Z"));
        assertThat(AnswerJson.write("Q", List.of())).isEqualTo("[]");
        assertThat(AnswerJson.read("[]")).isEqualTo(List.of());
    }

    @Test
    void aStoredSelectionRejectsWhatIsNotAnOptionAndTooManyOptions() {
        assertCode(() -> AnswerJson.write("Q", List.of("A", 3)), ScoringErrorCode.INVALID_ANSWER_TYPE);
        List<String> many = new ArrayList<>();
        for (int i = 0; i <= AnswerJson.MAX_SELECTION; i++) {
            many.add("option-" + i);
        }
        assertCode(() -> AnswerJson.write("Q", many), ScoringErrorCode.INVALID_ANSWER_TYPE);
    }

    @Test
    void earlierAnswerFormsAreStoredAndReadAsBefore() {
        assertThat(AnswerJson.write("Q", 4)).isEqualTo("4");
        assertThat(AnswerJson.write("Q", "OFTEN")).isEqualTo("\"OFTEN\"");
        assertThat(AnswerJson.read("true")).isEqualTo(true);
        assertThat(AnswerJson.read("\"x\"")).isEqualTo("x");
    }
}
