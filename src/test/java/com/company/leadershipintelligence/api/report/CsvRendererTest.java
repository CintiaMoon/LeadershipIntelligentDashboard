package com.company.leadershipintelligence.api.report;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvRendererTest {

    private static ReportDocument document(List<List<String>> rows) {
        return new ReportDocument("LEADERSHIP_INTELLIGENCE", "Title", false, "Notice, with comma",
                List.of(new ReportDocument.Section("s", "Heading", "A note", List.of("Item", "Value"), rows)));
    }

    private static String render(List<List<String>> rows) {
        return new String(CsvRenderer.render(document(rows)), StandardCharsets.UTF_8);
    }

    @Test
    void startsWithAByteOrderMarkAndUsesCrLf() {
        byte[] bytes = CsvRenderer.render(document(List.of(List.of("a", "1"))));
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("\r\n").doesNotContain("\n\n");
    }

    @Test
    void writesEachSectionAsAHeadingColumnsAndRows() {
        String csv = render(List.of(List.of("Score", "72.5")));
        assertThat(csv).isEqualTo("﻿Title\r\n\"Notice, with comma\"\r\n\r\nHeading\r\nA note\r\nItem,Value\r\nScore,72.5\r\n");
    }

    @Test
    void quotesCommasQuotesAndLineBreaks() {
        assertThat(CsvRenderer.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvRenderer.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvRenderer.cell("two\nlines")).isEqualTo("\"two\nlines\"");
    }

    @Test
    void textThatAFormulaReaderWouldRunIsDefused() {
        for (String risky : List.of("=1+1", "+cmd", "-cmd", "@SUM(A1)", "\tx", "\rx")) {
            assertThat(CsvRenderer.cell(risky)).as(risky).contains("'" + risky);
        }
        assertThat(CsvRenderer.cell("=1+1")).isEqualTo("'=1+1");
    }

    @Test
    void numbersAreNotTouchedEvenWhenNegative() {
        assertThat(CsvRenderer.cell("-3.5")).isEqualTo("-3.5");
        assertThat(CsvRenderer.cell("0")).isEqualTo("0");
        assertThat(CsvRenderer.cell("-cmd")).isEqualTo("'-cmd");
    }

    @Test
    void nullAndEmptyCellsAreEmpty() {
        assertThat(CsvRenderer.cell(null)).isEmpty();
        assertThat(CsvRenderer.cell("")).isEmpty();
    }

    @Test
    void sameDocumentGivesIdenticalBytes() {
        List<List<String>> rows = List.of(List.of("a", "1"), List.of("b", "2"));
        assertThat(CsvRenderer.render(document(rows))).isEqualTo(CsvRenderer.render(document(rows)));
    }
}
