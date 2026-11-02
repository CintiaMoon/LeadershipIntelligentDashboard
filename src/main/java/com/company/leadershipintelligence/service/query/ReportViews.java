package com.company.leadershipintelligence.service.query;

import java.util.List;

/** Facts about a run's configuration that a report states (decisions D57, D59 to D64). */
public final class ReportViews {

    private ReportViews() {
    }

    public record LayerCalibration(String scope, int version, String calibrationReference, String knownLimitations) {
    }

    public record Calibration(boolean calibrated, List<LayerCalibration> layers) {
    }
}
