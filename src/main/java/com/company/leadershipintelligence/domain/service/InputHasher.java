package com.company.leadershipintelligence.domain.service;

import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.ScopedResponse;
import com.company.leadershipintelligence.domain.model.ScoringInput;
import com.company.leadershipintelligence.domain.valueobject.AssessmentSnapshotId;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Canonical input hash (specv3.md Section 27.2). Answers are sorted by question id; numbers are normalized
 * (4, 4.0 and 4.00 are the same); every value carries its type and every text a length prefix, so no two
 * different inputs can produce the same canonical text. The hash does not depend on the order in which inputs
 * were supplied.
 */
public final class InputHasher {

    private static final String VERSION = "input-hash-v1";
    private static final String VERSION_SCOPED = "input-hash-v2";

    private InputHasher() {
    }

    /** Hash of the snapshot identity and its answers. */
    public static String inputHash(AssessmentSnapshotId snapshot, List<ScoringInput> inputs) {
        return sha256(VERSION + "|snapshot=" + text(snapshot.value()) + "|" + canonical(inputs));
    }

    /** Hash of the answers alone, used to recognise a repeated request. */
    public static String answersHash(List<ScoringInput> inputs) {
        return sha256(VERSION + "|" + canonical(inputs));
    }

    /**
     * Hash of the snapshot identity and the answers of one scoring unit (decision D45): the organization when
     * {@code teamId} is null, otherwise that team. Each answer is written with its scope, team and declared count, and
     * the entries are sorted, so the hash does not depend on the order of the answers. Answers that only a different
     * unit uses do not change it. Earlier, organization-only requests keep using {@link #inputHash}.
     */
    public static String scopedInputHash(AssessmentSnapshotId snapshot, List<ScopedResponse> responses, String teamId) {
        return sha256(VERSION_SCOPED + "|snapshot=" + text(snapshot.value()) + "|unit=" + text(teamId == null ? "" : teamId) + "|"
                + canonicalScoped(responses, teamId));
    }

    /** Hash of all scoped answers alone, used to recognise a repeated request. */
    public static String scopedAnswersHash(List<ScopedResponse> responses) {
        return sha256(VERSION_SCOPED + "|" + canonicalScoped(responses, null));
    }

    static String canonicalScoped(List<ScopedResponse> responses, String teamId) {
        return responses.stream().filter(r -> r.belongsToUnit(teamId)).map(r -> {
            String answer;
            try {
                answer = value(r.answer());
            } catch (IllegalArgumentException e) {
                throw new ScoringException(ScoringErrorCode.INVALID_ANSWER_TYPE,
                        "the answer for question " + r.questionId() + " has an unsupported type", Map.of("questionId", r.questionId()));
            }
            return text(r.questionId()) + "|" + r.scope() + "|" + text(r.teamId() == null ? "" : r.teamId()) + "|"
                    + (r.declaredRespondents() == null ? "-" : r.declaredRespondents()) + "=" + answer + ";";
        }).sorted().collect(java.util.stream.Collectors.joining());
    }

    public static String sha256(String text) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    static String canonical(List<ScoringInput> inputs) {
        StringBuilder out = new StringBuilder();
        inputs.stream().sorted(Comparator.comparing(ScoringInput::questionId)).forEach(input ->
                out.append(text(input.questionId())).append('=').append(valueOf(input)).append(';'));
        return out.toString();
    }

    private static String valueOf(ScoringInput input) {
        try {
            return value(input.answer());
        } catch (IllegalArgumentException e) {
            throw new ScoringException(ScoringErrorCode.INVALID_ANSWER_TYPE,
                    "the answer for question " + input.questionId() + " has an unsupported type", Map.of("questionId", input.questionId()));
        }
    }

    private static String value(Object answer) {
        if (answer == null) {
            return "null";
        }
        if (answer instanceof Boolean b) {
            return "b:" + b;
        }
        if (answer instanceof BigDecimal d) {
            return number(d);
        }
        if (answer instanceof BigInteger i) {
            return number(new BigDecimal(i));
        }
        if (answer instanceof Integer || answer instanceof Long || answer instanceof Short || answer instanceof Byte) {
            return number(BigDecimal.valueOf(((Number) answer).longValue()));
        }
        if (answer instanceof Double || answer instanceof Float) {
            return number(new BigDecimal(answer.toString()));
        }
        if (answer instanceof CharSequence s) {
            return "s:" + text(s.toString());
        }
        if (answer instanceof java.util.Collection<?> selection) {
            // a selection is a set of options: order does not matter and a repeated option counts once (specv3.md Section 4.2.1)
            java.util.List<String> parts = new java.util.ArrayList<>();
            for (Object element : selection) {
                if (!(element instanceof CharSequence option)) {
                    throw new IllegalArgumentException("a selection holds only option identifiers");
                }
                parts.add("s:" + text(option.toString()));
            }
            return "l:[" + parts.stream().distinct().sorted().collect(java.util.stream.Collectors.joining(",")) + "]";
        }
        throw new IllegalArgumentException("unsupported answer type " + answer.getClass().getSimpleName());
    }

    private static String number(BigDecimal value) {
        return "n:" + (value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString());
    }

    private static String text(String value) {
        return value.length() + ":" + value;
    }
}
