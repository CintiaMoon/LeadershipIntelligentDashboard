package com.company.leadershipintelligence.domain.service.signals;

import com.company.leadershipintelligence.domain.enums.NormalizationStatus;
import com.company.leadershipintelligence.domain.enums.SignalDataType;
import com.company.leadershipintelligence.domain.model.DerivedSignal;
import com.company.leadershipintelligence.domain.model.NormalizedInput;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.rule.SignalDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Derives registered signals from normalized inputs (specv3.md Section 9). A NUMBER signal copies its
 * single source value. A BOOLEAN signal is true when a source flagged it, false when an answered text
 * source did not, and absent when no source was answered. Output is in signal-id order.
 */
public class SignalDerivationService {

    public List<DerivedSignal> derive(List<NormalizedInput> inputs, ScoringConfigurationSnapshot configuration) {
        Map<String, NormalizedInput> byQuestion = new TreeMap<>();
        for (NormalizedInput input : inputs) {
            byQuestion.put(input.questionId(), input);
        }
        List<DerivedSignal> derived = new ArrayList<>();
        for (SignalDefinition definition : configuration.signals().values()) {
            DerivedSignal signal = definition.dataType() == SignalDataType.NUMBER
                    ? deriveNumber(definition, byQuestion)
                    : deriveFlag(definition, byQuestion);
            if (signal != null) {
                derived.add(signal);
            }
        }
        return List.copyOf(derived);
    }

    private static DerivedSignal deriveNumber(SignalDefinition definition, Map<String, NormalizedInput> byQuestion) {
        String source = definition.sourceQuestionIds().iterator().next();
        NormalizedInput input = byQuestion.get(source);
        if (input == null || input.value().isEmpty()) {
            return null;
        }
        return DerivedSignal.number(definition.signalId(), input.normalizedValue(), Set.of(source),
                definition.correlationGroup(), definition.derivationVersion(), definition.reliability());
    }

    private static DerivedSignal deriveFlag(SignalDefinition definition, Map<String, NormalizedInput> byQuestion) {
        Set<String> detecting = new TreeSet<>();
        Set<String> observedAbsent = new TreeSet<>();
        for (String source : definition.sourceQuestionIds()) {
            NormalizedInput input = byQuestion.get(source);
            if (input == null) {
                continue;
            }
            if (input.detectedSignals().contains(definition.signalId())) {
                detecting.add(source);
            } else if (input.status() == NormalizationStatus.SIGNALS_ONLY) {
                observedAbsent.add(source);
            }
        }
        if (!detecting.isEmpty()) {
            return DerivedSignal.flag(definition.signalId(), true, detecting,
                    definition.correlationGroup(), definition.derivationVersion(), definition.reliability());
        }
        if (!observedAbsent.isEmpty()) {
            return DerivedSignal.flag(definition.signalId(), false, observedAbsent,
                    definition.correlationGroup(), definition.derivationVersion(), definition.reliability());
        }
        return null;
    }
}
