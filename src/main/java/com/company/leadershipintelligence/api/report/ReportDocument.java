package com.company.leadershipintelligence.api.report;

import java.util.List;

/**
 * A report as data (decisions D59 to D64): titled sections of plain tables, built only from stored facts. The same
 * document is returned as JSON, written as CSV and drawn as PDF, so the formats cannot disagree. It carries no
 * generation time, so the same run always yields the same document.
 */
public record ReportDocument(String type, String title, boolean redacted, String notice, List<Section> sections) {

    /** A table with a heading and an optional explanation. A section with no rows says why in its note. */
    public record Section(String id, String heading, String note, List<String> columns, List<List<String>> rows) {
    }
}
