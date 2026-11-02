package com.company.leadershipintelligence.service.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * A representative test case of a configuration (specv3.md Section 23.6, decision D56): a named set of organization-level answers and
 * what the scoring must show for them. Unknown fields are rejected so a misspelt expectation is never silently ignored. Expectations
 * are ranges, never exact scores, so a fixture survives a harmless change of a weight and still catches a real one.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FixtureSpec(String name, List<Answer> answers, Expectations expectations) {

    public FixtureSpec {
        answers = answers == null ? List.of() : List.copyOf(answers);
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record Answer(String questionId, Object answer) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Range(BigDecimal min, BigDecimal max) {
    }

    /**
     * What must hold. Every part is optional.
     *
     * @param status           VALID or INSUFFICIENT_DATA
     * @param overall          range of the overall score (only for a VALID result)
     * @param dimensions       range of the adjusted score of each named dimension
     * @param patternsMatched  pattern ids that must be detected
     * @param patternsNotMatched pattern ids that must not be detected
     */
    @JsonIgnoreProperties(ignoreUnknown = false)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Expectations(String status, Range overall, Map<String, Range> dimensions, List<String> patternsMatched,
                               List<String> patternsNotMatched) {

        public Expectations {
            dimensions = dimensions == null ? Map.of() : Map.copyOf(dimensions);
            patternsMatched = patternsMatched == null ? List.of() : List.copyOf(patternsMatched);
            patternsNotMatched = patternsNotMatched == null ? List.of() : List.copyOf(patternsNotMatched);
        }
    }
}
