package com.company.leadershipintelligence.repository.persistence.mapper;

import com.company.leadershipintelligence.domain.model.PenaltyResult;
import com.company.leadershipintelligence.domain.model.ScoringTrace;
import com.company.leadershipintelligence.domain.model.TraceStep;
import com.company.leadershipintelligence.domain.valueobject.ConfidenceValue;
import com.company.leadershipintelligence.domain.valueobject.NormalizedValue;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;
import com.company.leadershipintelligence.domain.valueobject.ScoreValue;
import com.company.leadershipintelligence.domain.valueobject.ScoreWeight;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;

/**
 * Serializes the explanation trace to JSON (decision D24). The domain stays free of Jackson: polymorphism and
 * value-object shapes are described here with mixins. Output is deterministic (record order, sorted map keys,
 * plain decimals) and contains no raw answers. The stored text is returned unchanged by the explanation endpoint;
 * nothing is recalculated.
 */
public final class TraceJson {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "stage")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = TraceStep.Normalization.class, name = "NORMALIZATION"),
            @JsonSubTypes.Type(value = TraceStep.Signals.class, name = "SIGNALS"),
            @JsonSubTypes.Type(value = TraceStep.RawDimensions.class, name = "RAW_DIMENSIONS"),
            @JsonSubTypes.Type(value = TraceStep.Imbalances.class, name = "IMBALANCES"),
            @JsonSubTypes.Type(value = TraceStep.Patterns.class, name = "PATTERNS"),
            @JsonSubTypes.Type(value = TraceStep.Penalties.class, name = "PENALTIES"),
            @JsonSubTypes.Type(value = TraceStep.AdjustedDimensions.class, name = "ADJUSTED_DIMENSIONS"),
            @JsonSubTypes.Type(value = TraceStep.Overall.class, name = "OVERALL")})
    abstract static class StepMixin {
    }

    abstract static class DecimalValueMixin {
        @JsonValue
        abstract Object value();
    }

    /** A penalty result without its full definition: the fields an auditor needs, flat. */
    private static final class PenaltyResultSerializer extends StdSerializer<PenaltyResult> {

        PenaltyResultSerializer() {
            super(PenaltyResult.class);
        }

        @Override
        public void serialize(PenaltyResult r, JsonGenerator g, SerializerProvider provider) throws IOException {
            var d = r.definition();
            g.writeStartObject();
            g.writeStringField("penaltyId", d.penaltyId());
            g.writeStringField("code", d.code());
            g.writeNumberField("version", d.version());
            g.writeStringField("target", d.target().name());
            g.writeStringField("strategy", d.strategy().type().name());
            g.writeStringField("category", d.category());
            g.writeStringField("exclusiveGroup", d.exclusiveGroup());
            g.writeNumberField("priority", d.priority());
            g.writeNumberField("ruleOrder", d.ruleOrder());
            g.writeStringField("stackingPolicy", r.policy().name());
            g.writeStringField("triggerPatternId", d.triggerPatternId());
            g.writeNumberField("triggerPatternVersion", d.triggerPatternVersion());
            g.writeStringField("status", r.status().name());
            g.writeStringField("reason", r.reason().name());
            g.writeNumberField("candidateDeduction", r.candidateDeduction().value());
            g.writeNumberField("effectiveDeduction", r.effectiveDeduction().value());
            g.writeNumberField("suppressedAmount", r.suppressedAmount().value());
            g.writeStringField("winningPenaltyId", r.winningPenaltyId());
            g.writeStringField("scoreBase", r.scoreBase());
            if (r.scoreBaseValue() == null) {
                g.writeNullField("scoreBaseValue");
            } else {
                g.writeNumberField("scoreBaseValue", r.scoreBaseValue());
            }
            g.writeObjectField("evidenceRefs", r.evidenceRefs());
            g.writeStringField("explanationKey", d.explanationKey());
            g.writeEndObject();
        }
    }

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .addMixIn(TraceStep.class, StepMixin.class)
            .addMixIn(ScoreValue.class, DecimalValueMixin.class)
            .addMixIn(NormalizedValue.class, DecimalValueMixin.class)
            .addMixIn(ConfidenceValue.class, DecimalValueMixin.class)
            .addMixIn(PenaltyAmount.class, DecimalValueMixin.class)
            .addMixIn(ScoreWeight.class, DecimalValueMixin.class)
            .addModule(new SimpleModule().addSerializer(new PenaltyResultSerializer()))
            .build();

    private TraceJson() {
    }

    public static String write(ScoringTrace trace) {
        try {
            return MAPPER.writeValueAsString(trace);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("the trace could not be serialized", e);
        }
    }

    /** Serializes any plain value (lists of strings, small records) with the same settings. */
    public static String writeValue(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("the value could not be serialized", e);
        }
    }
}
