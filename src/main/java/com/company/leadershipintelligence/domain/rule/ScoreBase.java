package com.company.leadershipintelligence.domain.rule;

import com.company.leadershipintelligence.domain.enums.GapDirection;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;

/**
 * The explicit quantity a PROPORTIONAL or THRESHOLD_BASED penalty is computed from (specv3.md Section 15.3).
 * It is never inferred. Only values available before penalties are applied (RAW dimension scores) are allowed.
 */
public sealed interface ScoreBase {

    String describe();

    record RawDimension(ScoringDimension dimension) implements ScoreBase {
        public RawDimension {
            PenaltyChecks.require(dimension != null, "score base dimension is required");
        }

        @Override
        public String describe() {
            return "raw " + dimension;
        }
    }

    record DimensionGap(ScoringDimension left, ScoringDimension right, GapDirection direction) implements ScoreBase {
        public DimensionGap {
            PenaltyChecks.require(left != null && right != null && direction != null, "gap needs two dimensions and a direction");
            PenaltyChecks.require(left != right, "gap dimensions must differ");
        }

        @Override
        public String describe() {
            return "gap " + left + " vs " + right + " (" + direction + ")";
        }
    }
}
