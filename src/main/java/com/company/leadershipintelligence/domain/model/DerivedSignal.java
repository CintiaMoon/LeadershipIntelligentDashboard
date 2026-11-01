package com.company.leadershipintelligence.domain.model;

import com.company.leadershipintelligence.domain.enums.SignalDataType;
import com.company.leadershipintelligence.domain.valueobject.NormalizedValue;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * A named observation used by later stages, with provenance (specv3.md Sections 9.1, 9.2). Absence
 * of a signal means it could not be observed; it is never represented as zero or false by default.
 */
public record DerivedSignal(
        String signalId,
        SignalDataType dataType,
        NormalizedValue numericValue,
        Boolean flag,
        Set<String> sourceQuestionIds,
        String correlationGroup,
        String derivationVersion,
        BigDecimal reliability) {

    public DerivedSignal {
        if (dataType == SignalDataType.NUMBER && (numericValue == null || flag != null)) {
            throw new IllegalArgumentException("NUMBER signal needs a numeric value only");
        }
        if (dataType == SignalDataType.BOOLEAN && (flag == null || numericValue != null)) {
            throw new IllegalArgumentException("BOOLEAN signal needs a flag only");
        }
        sourceQuestionIds = Collections.unmodifiableSortedSet(new TreeSet<>(sourceQuestionIds));
    }

    public static DerivedSignal number(String id, NormalizedValue value, Set<String> sources, String group,
                                       String version, BigDecimal reliability) {
        return new DerivedSignal(id, SignalDataType.NUMBER, value, null, sources, group, version, reliability);
    }

    public static DerivedSignal flag(String id, boolean value, Set<String> sources, String group,
                                     String version, BigDecimal reliability) {
        return new DerivedSignal(id, SignalDataType.BOOLEAN, null, value, sources, group, version, reliability);
    }
}
