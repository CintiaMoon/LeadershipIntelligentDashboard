package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.ScoringDimension;

/** What a condition looks at: a derived signal, a raw dimension score, or a question's detected flags. */
public sealed interface Operand {

    /** Stable key used in evidence and traces. */
    String key();

    record SignalOperand(String signalId) implements Operand {
        public SignalOperand {
            ConditionChecks.require(signalId != null && !signalId.isBlank(), "signal id is required");
        }

        @Override
        public String key() {
            return "signal:" + signalId;
        }
    }

    record DimensionOperand(ScoringDimension dimension) implements Operand {
        public DimensionOperand {
            ConditionChecks.require(dimension != null, "dimension is required");
        }

        @Override
        public String key() {
            return "dimension:" + dimension;
        }
    }

    record QuestionSignalsOperand(String questionId) implements Operand {
        public QuestionSignalsOperand {
            ConditionChecks.require(questionId != null && !questionId.isBlank(), "question id is required");
        }

        @Override
        public String key() {
            return "question-signals:" + questionId;
        }
    }
}
