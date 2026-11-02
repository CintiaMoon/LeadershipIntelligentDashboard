package com.company.leadershipintelligence.service;

import com.company.leadershipintelligence.domain.valueobject.TenantId;
import com.company.leadershipintelligence.repository.persistence.repository.ConfigurationReviewStore;
import com.company.leadershipintelligence.service.command.OrganizationScope;
import com.company.leadershipintelligence.service.query.ReportViews.Calibration;
import com.company.leadershipintelligence.service.query.ReportViews.LayerCalibration;
import com.company.leadershipintelligence.service.query.ScoringRunView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a report needs beyond the stored run (decisions D59 to D64): whether the configuration the run was scored with
 * carries a calibration record (decision D57). Reports are rendered from stored facts; nothing here scores or changes
 * anything, and a missing record is reported as unvalidated, never assumed.
 */
@Service
@Transactional(readOnly = true)
public class ReportService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ScoreAssessmentService scoring;
    private final ConfigurationReviewStore reviews;

    public ReportService(ScoreAssessmentService scoring, ConfigurationReviewStore reviews) {
        this.scoring = scoring;
        this.reviews = reviews;
    }

    /** The run, if the caller may read it (a foreign tenant or organization looks like a missing run). */
    public ScoringRunView run(TenantId tenant, OrganizationScope scope, UUID runId) {
        return scoring.get(tenant, runId, scope);
    }

    /**
     * Calibration of every layer version the run was scored with. The configuration counts as calibrated only when every
     * layer carries a calibration reference; a layer without a review record (a questionnaire template, the initial
     * file) is unvalidated.
     */
    public Calibration calibration(ScoringRunView run) {
        List<LayerCalibration> layers = new ArrayList<>();
        if (run.layersJson() != null) {
            try {
                for (JsonNode layer : JSON.readTree(run.layersJson())) {
                    UUID versionId = UUID.fromString(layer.path("versionId").asText());
                    var review = reviews.review(versionId);
                    String reference = review.map(ConfigurationReviewStore.ReviewRow::calibrationReference).filter(r -> !r.isBlank()).orElse(null);
                    String limitations = review.map(ConfigurationReviewStore.ReviewRow::knownLimitations).filter(r -> !r.isBlank()).orElse(null);
                    layers.add(new LayerCalibration(layer.path("scope").asText(""), layer.path("version").asInt(0), reference, limitations));
                }
            } catch (JsonProcessingException | IllegalArgumentException e) {
                throw new IllegalStateException("the stored layers of the run could not be read", e);
            }
        }
        boolean calibrated = !layers.isEmpty() && layers.stream().allMatch(l -> l.calibrationReference() != null);
        return new Calibration(calibrated, List.copyOf(layers));
    }

    /** Records that a caller generated a report (who, when, which run, type and format; never the content). */
    @Transactional
    public void audit(TenantId tenant, String actor, UUID runId, String type, String format, boolean redacted) {
        scoring.auditReport(tenant, actor, runId, type, format, redacted);
    }
}
