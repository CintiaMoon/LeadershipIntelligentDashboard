package com.company.leadershipintelligence.repository.configuration;

import org.springframework.stereotype.Component;
import com.company.leadershipintelligence.domain.enums.AggregationMethod;
import com.company.leadershipintelligence.domain.enums.ConditionOperator;
import com.company.leadershipintelligence.domain.enums.EvaluationBasis;
import com.company.leadershipintelligence.domain.enums.GapDirection;
import com.company.leadershipintelligence.domain.enums.MissingDataPolicy;
import com.company.leadershipintelligence.domain.enums.OutOfRangePolicy;
import com.company.leadershipintelligence.domain.enums.PatternCategory;
import com.company.leadershipintelligence.domain.enums.PenaltyStrategyType;
import com.company.leadershipintelligence.domain.enums.PenaltyTarget;
import com.company.leadershipintelligence.domain.enums.QuestionType;
import com.company.leadershipintelligence.domain.enums.ScaleDirection;
import com.company.leadershipintelligence.domain.enums.ScoringDimension;
import com.company.leadershipintelligence.domain.enums.Severity;
import com.company.leadershipintelligence.domain.enums.SignalDataType;
import com.company.leadershipintelligence.domain.enums.StackingPolicy;
import com.company.leadershipintelligence.domain.enums.TextMatchMode;
import com.company.leadershipintelligence.domain.enums.UnknownOptionPolicy;
import com.company.leadershipintelligence.domain.exception.ScoringErrorCode;
import com.company.leadershipintelligence.domain.exception.ScoringException;
import com.company.leadershipintelligence.domain.model.QuestionDefinition;
import com.company.leadershipintelligence.domain.model.ScoringConfigurationSnapshot;
import com.company.leadershipintelligence.domain.rule.BooleanConfig;
import com.company.leadershipintelligence.domain.rule.Breakpoint;
import com.company.leadershipintelligence.domain.rule.Condition;
import com.company.leadershipintelligence.domain.rule.ContributionRule;
import com.company.leadershipintelligence.domain.rule.DimensionScoringConfig;
import com.company.leadershipintelligence.domain.rule.DimensionSettings;
import com.company.leadershipintelligence.domain.rule.EvidenceRequirements;
import com.company.leadershipintelligence.domain.rule.ImbalanceRule;
import com.company.leadershipintelligence.domain.rule.InterpretationConfig;
import com.company.leadershipintelligence.domain.rule.InterpretationRange;
import com.company.leadershipintelligence.domain.rule.MultipleChoiceConfig;
import com.company.leadershipintelligence.domain.rule.MultiSelectConfig;
import com.company.leadershipintelligence.domain.rule.NormalizationConfig;
import com.company.leadershipintelligence.domain.rule.NumberConfig;
import com.company.leadershipintelligence.domain.rule.Operand;
import com.company.leadershipintelligence.domain.rule.OverallScoringConfig;
import com.company.leadershipintelligence.domain.rule.OverallWeights;
import com.company.leadershipintelligence.domain.rule.PatternDefinition;
import com.company.leadershipintelligence.domain.rule.PresentationCatalog;
import com.company.leadershipintelligence.domain.rule.RecommendationDefinition;
import com.company.leadershipintelligence.domain.rule.ResponseSettings;
import com.company.leadershipintelligence.domain.rule.PenaltyDefinition;
import com.company.leadershipintelligence.domain.rule.ScaleConfig;
import com.company.leadershipintelligence.domain.rule.ScoreBase;
import com.company.leadershipintelligence.domain.rule.SignalDefinition;
import com.company.leadershipintelligence.domain.rule.StrategyConfig;
import com.company.leadershipintelligence.domain.rule.TextConfig;
import com.company.leadershipintelligence.domain.rule.TextSignalRule;
import com.company.leadershipintelligence.domain.rule.ThresholdBand;
import com.company.leadershipintelligence.domain.valueobject.ConfidenceValue;
import com.company.leadershipintelligence.domain.valueobject.ConfigurationVersion;
import com.company.leadershipintelligence.domain.valueobject.NormalizedValue;
import com.company.leadershipintelligence.domain.valueobject.PenaltyAmount;
import com.company.leadershipintelligence.domain.valueobject.ScoreWeight;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a configuration document (JSON) into the immutable domain snapshot (specv3.md Section 23). Parsing is
 * strict: unknown fields, duplicate keys, trailing content, wrong types and unknown enum values are all
 * rejected with the JSON path, and every domain validation rule runs when the snapshot is built. Business
 * parameters live in the document, never in this class. The first problem found is reported.
 */
@Component
public final class ConfigurationDocumentParser {

    static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .build();

    public JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw DocumentFields.error("the configuration is not valid JSON: " + e.getOriginalMessage());
        }
    }

    public ScoringConfigurationSnapshot parse(JsonNode document, ConfigurationVersion version) {
        try {
            return build(document, version);
        } catch (ScoringException e) {
            throw e;
        } catch (IllegalArgumentException | NullPointerException e) {
            throw DocumentFields.error("invalid configuration value: " + e.getMessage());
        }
    }

    public ScoringConfigurationSnapshot parse(String json, ConfigurationVersion version) {
        return parse(read(json), version);
    }

    private ScoringConfigurationSnapshot build(JsonNode document, ConfigurationVersion version) {
        DocumentFields root = DocumentFields.of(document, "$");
        Map<String, QuestionDefinition> questions = new LinkedHashMap<>();
        for (DocumentFields f : root.objects("questions")) {
            QuestionDefinition question = question(f);
            if (questions.put(question.questionId(), question) != null) {
                throw DocumentFields.error("duplicate question " + question.questionId());
            }
        }
        Map<String, SignalDefinition> signals = new LinkedHashMap<>();
        for (DocumentFields f : root.optionalObjects("signals")) {
            SignalDefinition signal = signal(f);
            if (signals.put(signal.signalId(), signal) != null) {
                throw DocumentFields.error("duplicate signal " + signal.signalId());
            }
        }
        DimensionScoringConfig dimensions = dimensions(root.object("dimensions"));
        List<PatternDefinition> patterns = new ArrayList<>();
        root.optionalObjects("patterns").forEach(f -> patterns.add(pattern(f)));
        List<PenaltyDefinition> penalties = new ArrayList<>();
        root.optionalObjects("penalties").forEach(f -> penalties.add(penalty(f)));
        List<ImbalanceRule> imbalances = new ArrayList<>();
        root.optionalObjects("imbalances").forEach(f -> imbalances.add(imbalance(f)));
        OverallScoringConfig overall = overall(root.object("overall"));
        Map<String, RecommendationDefinition> recommendations = new LinkedHashMap<>();
        for (DocumentFields f : root.optionalObjects("recommendations")) {
            RecommendationDefinition recommendation = new RecommendationDefinition(f.text("key"), f.text("title"), f.text("description"),
                    f.optionalInteger("priority", 100));
            f.done();
            if (recommendations.put(recommendation.key(), recommendation) != null) {
                throw DocumentFields.error("duplicate recommendation " + recommendation.key());
            }
        }
        PresentationCatalog presentation = root.optionalObject("presentation").map(this::presentation).orElse(PresentationCatalog.empty());
        ResponseSettings responses = root.optionalObject("responses").map(this::responses).orElse(ResponseSettings.defaults());
        root.done();
        return new ScoringConfigurationSnapshot(version, questions, signals, dimensions, patterns, penalties, imbalances, overall,
                recommendations, presentation, responses);
    }

    // ---------------- responses (decision D45) ----------------

    private ResponseSettings responses(DocumentFields f) {
        ResponseSettings defaults = ResponseSettings.defaults();
        ResponseSettings settings = new ResponseSettings(
                f.optionalEnumeration(AggregationMethod.class, "defaultMethod").orElse(defaults.defaultMethod()),
                f.optionalInteger("minResponses", defaults.minResponses()),
                f.optionalDecimal("signalShare").orElse(defaults.signalShare()),
                f.optionalInteger("fullConfidenceResponses", defaults.fullConfidenceResponses()));
        f.done();
        return settings;
    }

    // ---------------- presentation ----------------

    private PresentationCatalog presentation(DocumentFields f) {
        PresentationCatalog catalog = new PresentationCatalog(presentationSection(f, "patterns"), presentationSection(f, "penalties"),
                presentationSection(f, "dimensions"), presentationSection(f, "signals"), presentationSection(f, "questions"));
        f.done();
        return catalog;
    }

    private Map<String, PresentationCatalog.Entry> presentationSection(DocumentFields f, String name) {
        Map<String, PresentationCatalog.Entry> entries = new LinkedHashMap<>();
        if (!f.has(name)) {
            return entries;
        }
        f.objectMap(name).forEach((key, fields) -> {
            PresentationCatalog.Entry entry = new PresentationCatalog.Entry(fields.text("name"), fields.text("description"),
                    fields.optionalText("explanation").orElse(null));
            fields.done();
            entries.put(key, entry);
        });
        return entries;
    }

    // ---------------- questions and signals ----------------

    private QuestionDefinition question(DocumentFields f) {
        String id = f.text("id");
        QuestionType type = f.enumeration(QuestionType.class, "type");
        MissingDataPolicy policy = f.enumeration(MissingDataPolicy.class, "missingPolicy");
        NormalizedValue neutral = f.optionalDecimal("neutralValue").map(NormalizedValue::new).orElse(null);
        String riskSignal = f.optionalText("riskSignal").orElse(null);
        AggregationMethod aggregation = f.optionalEnumeration(AggregationMethod.class, "aggregation").orElse(null);
        NormalizationConfig normalization = normalization(type, f.object("normalization"));
        f.done();
        return new QuestionDefinition(id, type, normalization, policy, neutral, riskSignal, aggregation);
    }

    private NormalizationConfig normalization(QuestionType type, DocumentFields f) {
        NormalizationConfig config = switch (type) {
            case SCALE -> new ScaleConfig(f.decimal("min"), f.decimal("max"), f.enumeration(ScaleDirection.class, "direction"),
                    f.enumeration(OutOfRangePolicy.class, "outOfRangePolicy"), f.optionalBool("allowDecimals", true));
            case NUMBER -> {
                BigDecimal min = f.decimal("min");
                BigDecimal max = f.decimal("max");
                ScaleDirection direction = f.enumeration(ScaleDirection.class, "direction");
                OutOfRangePolicy outOfRange = f.enumeration(OutOfRangePolicy.class, "outOfRangePolicy");
                List<Breakpoint> breakpoints = new ArrayList<>();
                for (DocumentFields b : f.optionalObjects("breakpoints")) {
                    breakpoints.add(new Breakpoint(b.decimal("input"), new NormalizedValue(b.decimal("output"))));
                    b.done();
                }
                yield new NumberConfig(min, max, direction, outOfRange, breakpoints);
            }
            case MULTIPLE_CHOICE -> {
                Map<String, NormalizedValue> values = new LinkedHashMap<>();
                f.decimalMap("values").forEach((option, value) -> values.put(option, new NormalizedValue(value)));
                yield new MultipleChoiceConfig(values, f.enumeration(UnknownOptionPolicy.class, "unknownOption"));
            }
            case MULTI_SELECT -> {
                Map<String, NormalizedValue> values = new LinkedHashMap<>();
                f.decimalMap("values").forEach((option, value) -> values.put(option, new NormalizedValue(value)));
                yield new MultiSelectConfig(values, f.enumeration(UnknownOptionPolicy.class, "unknownOption"));
            }
            case BOOLEAN -> new BooleanConfig(new NormalizedValue(f.decimal("trueValue")), new NormalizedValue(f.decimal("falseValue")));
            case TEXT -> {
                String matcherVersion = f.text("matcherVersion");
                List<TextSignalRule> rules = new ArrayList<>();
                for (DocumentFields r : f.objects("signals")) {
                    rules.add(new TextSignalRule(r.text("id"), r.enumeration(TextMatchMode.class, "matchMode"), r.texts("keywords")));
                    r.done();
                }
                yield new TextConfig(matcherVersion, rules);
            }
        };
        f.done();
        return config;
    }

    private SignalDefinition signal(DocumentFields f) {
        SignalDefinition signal = new SignalDefinition(f.text("id"), f.enumeration(SignalDataType.class, "dataType"),
                new java.util.HashSet<>(f.texts("sources")), f.optionalText("correlationGroup").orElse(null),
                f.text("derivationVersion"), f.optionalDecimal("reliability").orElse(BigDecimal.ONE));
        f.done();
        return signal;
    }

    // ---------------- dimensions ----------------

    private DimensionScoringConfig dimensions(DocumentFields f) {
        Map<ScoringDimension, DimensionSettings> settings = new EnumMap<>(ScoringDimension.class);
        f.objectMap("settings").forEach((name, s) -> {
            settings.put(dimensionKey(name), new DimensionSettings(s.decimal("requiredCoverage")));
            s.done();
        });
        List<ContributionRule> rules = new ArrayList<>();
        for (DocumentFields m : f.objects("mappings")) {
            rules.add(new ContributionRule(m.text("question"), m.enumeration(ScoringDimension.class, "dimension"),
                    new ScoreWeight(m.decimal("weight"))));
            m.done();
        }
        int max = f.optionalInteger("maxDimensionsPerQuestion", 4);
        f.done();
        return new DimensionScoringConfig(settings, rules, max);
    }

    private ScoringDimension dimensionKey(String name) {
        try {
            return ScoringDimension.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw DocumentFields.error("unknown dimension '" + name + "'");
        }
    }

    // ---------------- patterns and conditions ----------------

    private PatternDefinition pattern(DocumentFields f) {
        String id = f.text("id");
        String code = f.text("code");
        int version = f.integer("version");
        PatternCategory category = f.enumeration(PatternCategory.class, "category");
        Severity severity = f.enumeration(Severity.class, "severity");
        boolean enabled = f.bool("enabled");
        boolean penaltyEligible = f.bool("penaltyEligible");
        DocumentFields evidence = f.object("evidence");
        EvidenceRequirements requirements = new EvidenceRequirements(evidence.integer("minimumSignals"),
                new ConfidenceValue(evidence.decimal("minimumConfidence")));
        evidence.done();
        List<String> actions = f.optionalTexts("actions");
        Condition condition = condition(f.object("condition"));
        f.done();
        return new PatternDefinition(id, code, version, category, severity, enabled, condition, requirements, penaltyEligible, actions);
    }

    private Condition condition(DocumentFields f) {
        ConditionOperator op = f.enumeration(ConditionOperator.class, "operator");
        Condition condition = switch (op) {
            case AND -> new Condition.And(conditions(f.objects("conditions")));
            case OR -> new Condition.Or(conditions(f.objects("conditions")));
            case NOT -> new Condition.Not(condition(f.object("condition")));
            case EQ, NE -> {
                if (f.has("value") && isBoolean(f)) {
                    yield new Condition.BooleanComparison(operand(f), op, f.bool("value"));
                }
                yield new Condition.NumericComparison(operand(f), op, f.decimal("value"));
            }
            case GT, GTE, LT, LTE -> new Condition.NumericComparison(operand(f), op, f.decimal("value"));
            case BETWEEN -> new Condition.Between(operand(f), f.decimal("low"), f.decimal("high"));
            case IN, NOT_IN -> new Condition.InList(operand(f), op == ConditionOperator.NOT_IN, f.decimals("values"));
            case EXISTS, NOT_EXISTS -> new Condition.Existence(operand(f), op == ConditionOperator.NOT_EXISTS);
            case CONTAINS -> new Condition.Contains(new Operand.QuestionSignalsOperand(f.text("question")), f.text("signal"));
            case GAP_GT, GAP_GTE -> {
                DocumentFields left = f.object("left");
                Operand l = operand(left);
                left.done();
                DocumentFields right = f.object("right");
                Operand r = operand(right);
                right.done();
                yield new Condition.Gap(l, r, f.enumeration(GapDirection.class, "direction"), op, f.decimal("value"));
            }
        };
        f.done();
        return condition;
    }

    private boolean isBoolean(DocumentFields f) {
        return f.rawIsBoolean("value");
    }

    private List<Condition> conditions(List<DocumentFields> list) {
        List<Condition> result = new ArrayList<>();
        list.forEach(f -> result.add(condition(f)));
        return result;
    }

    private Operand operand(DocumentFields f) {
        int given = (f.has("signal") ? 1 : 0) + (f.has("dimension") ? 1 : 0) + (f.has("question") ? 1 : 0);
        if (given != 1) {
            throw DocumentFields.error(f.path() + " needs exactly one of signal, dimension or question");
        }
        if (f.has("signal")) {
            return new Operand.SignalOperand(f.text("signal"));
        }
        if (f.has("dimension")) {
            return new Operand.DimensionOperand(f.enumeration(ScoringDimension.class, "dimension"));
        }
        return new Operand.QuestionSignalsOperand(f.text("question"));
    }

    // ---------------- penalties ----------------

    private PenaltyDefinition penalty(DocumentFields f) {
        String id = f.text("id");
        String code = f.text("code");
        int version = f.integer("version");
        DocumentFields trigger = f.object("trigger");
        String triggerPattern = trigger.text("pattern");
        int triggerVersion = trigger.integer("version");
        trigger.done();
        PenaltyTarget target = f.enumeration(PenaltyTarget.class, "target");
        StrategyConfig strategy = strategy(f.object("strategy"));
        PenaltyAmount maximum = new PenaltyAmount(f.decimal("maximumDeduction"));
        String category = f.text("category");
        String group = f.optionalText("exclusiveGroup").orElse(null);
        int priority = f.integer("priority");
        int order = f.integer("order");
        StackingPolicy policy = f.enumeration(StackingPolicy.class, "stackingPolicy");
        PenaltyAmount cumulative = f.optionalDecimal("maximumCumulativeDeduction").map(PenaltyAmount::new).orElse(null);
        ConfidenceValue minimumConfidence = new ConfidenceValue(f.optionalDecimal("minimumConfidence").orElse(BigDecimal.ZERO));
        boolean enabled = f.bool("enabled");
        String rationale = f.optionalText("dualApplicationRationale").orElse(null);
        String explanationKey = f.optionalText("explanationKey").orElse(null);
        f.done();
        return new PenaltyDefinition(id, code, version, triggerPattern, triggerVersion, target, strategy, maximum, category, group,
                priority, order, policy, cumulative, minimumConfidence, enabled, rationale, explanationKey);
    }

    private StrategyConfig strategy(DocumentFields f) {
        PenaltyStrategyType type = f.enumeration(PenaltyStrategyType.class, "type");
        StrategyConfig strategy = switch (type) {
            case FIXED -> new StrategyConfig.Fixed(new PenaltyAmount(f.decimal("deduction")));
            case PROPORTIONAL -> new StrategyConfig.Proportional(scoreBase(f.object("base")), f.decimal("rate"));
            case SEVERITY_BASED -> {
                PenaltyAmount base = new PenaltyAmount(f.decimal("baseDeduction"));
                Map<Severity, BigDecimal> multipliers = new EnumMap<>(Severity.class);
                f.decimalMap("multipliers").forEach((name, value) -> {
                    try {
                        multipliers.put(Severity.valueOf(name), value);
                    } catch (IllegalArgumentException e) {
                        throw DocumentFields.error("unknown severity '" + name + "'");
                    }
                });
                yield new StrategyConfig.SeverityBased(base, multipliers);
            }
            case THRESHOLD_BASED -> {
                ScoreBase metric = scoreBase(f.object("metric"));
                List<ThresholdBand> bands = new ArrayList<>();
                for (DocumentFields b : f.objects("bands")) {
                    bands.add(new ThresholdBand(b.decimal("from"), b.bool("fromInclusive"), b.optionalDecimal("to").orElse(null),
                            b.optionalBool("toInclusive", false), new PenaltyAmount(b.decimal("deduction"))));
                    b.done();
                }
                yield new StrategyConfig.ThresholdBased(metric, bands);
            }
            case CONDITIONAL -> new StrategyConfig.Conditional(condition(f.object("condition")), strategy(f.object("then")));
        };
        f.done();
        return strategy;
    }

    private ScoreBase scoreBase(DocumentFields f) {
        String type = f.text("type");
        ScoreBase base = switch (type) {
            case "RAW_DIMENSION" -> new ScoreBase.RawDimension(f.enumeration(ScoringDimension.class, "dimension"));
            case "DIMENSION_GAP" -> new ScoreBase.DimensionGap(f.enumeration(ScoringDimension.class, "left"),
                    f.enumeration(ScoringDimension.class, "right"), f.enumeration(GapDirection.class, "direction"));
            default -> throw DocumentFields.error(f.path() + ".type has unknown value '" + type + "'");
        };
        f.done();
        return base;
    }

    // ---------------- imbalances and overall ----------------

    private ImbalanceRule imbalance(DocumentFields f) {
        ImbalanceRule rule = new ImbalanceRule(f.text("id"), f.integer("version"), f.enumeration(ScoringDimension.class, "left"),
                f.enumeration(ScoringDimension.class, "right"), f.enumeration(GapDirection.class, "direction"),
                f.enumeration(ConditionOperator.class, "operator"), f.decimal("threshold"), f.enumeration(Severity.class, "severity"),
                f.enumeration(EvaluationBasis.class, "basis"), f.bool("enabled"), f.optionalText("explanationKey").orElse(null),
                f.optionalText("recommendationKey").orElse(null));
        f.done();
        return rule;
    }

    private OverallScoringConfig overall(DocumentFields f) {
        Map<ScoringDimension, ScoreWeight> weights = new EnumMap<>(ScoringDimension.class);
        f.decimalMap("weights").forEach((name, value) -> weights.put(dimensionKey(name), new ScoreWeight(value)));
        OverallWeights overallWeights = new OverallWeights(weights, f.decimal("tolerance"));
        InterpretationConfig lis = interpretation(f.objects("lisInterpretation"));
        List<DocumentFields> dimensionRanges = f.optionalObjects("dimensionInterpretation");
        InterpretationConfig dimension = dimensionRanges.isEmpty() ? null : interpretation(dimensionRanges);
        f.done();
        return new OverallScoringConfig(overallWeights, lis, dimension);
    }

    private InterpretationConfig interpretation(List<DocumentFields> ranges) {
        List<InterpretationRange> list = new ArrayList<>();
        for (DocumentFields r : ranges) {
            list.add(new InterpretationRange(r.decimal("min"), r.decimal("max"), r.text("label")));
            r.done();
        }
        return new InterpretationConfig(list);
    }
}
