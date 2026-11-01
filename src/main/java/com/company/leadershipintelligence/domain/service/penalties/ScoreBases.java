package com.company.leadershipintelligence.domain.service.penalties;

import com.company.leadershipintelligence.domain.model.EvaluationContext;
import com.company.leadershipintelligence.domain.rule.ScoreBase;
import com.company.leadershipintelligence.domain.valueobject.ScoreValue;

import java.math.BigDecimal;
import java.util.Optional;

/** Resolves an explicit score base against RAW dimension scores. A missing dimension yields empty, never zero. */
final class ScoreBases {

    private ScoreBases() {
    }

    static Optional<BigDecimal> resolve(ScoreBase base, EvaluationContext context) {
        return switch (base) {
            case ScoreBase.RawDimension r -> context.rawDimension(r.dimension()).map(ScoreValue::value);
            case ScoreBase.DimensionGap g -> {
                Optional<BigDecimal> left = context.rawDimension(g.left()).map(ScoreValue::value);
                Optional<BigDecimal> right = context.rawDimension(g.right()).map(ScoreValue::value);
                if (left.isEmpty() || right.isEmpty()) {
                    yield Optional.empty();
                }
                BigDecimal difference = left.get().subtract(right.get());
                yield Optional.of(switch (g.direction()) {
                    case LEFT_MINUS_RIGHT -> difference;
                    case RIGHT_MINUS_LEFT -> difference.negate();
                    case ABSOLUTE -> difference.abs();
                });
            }
        };
    }
}
