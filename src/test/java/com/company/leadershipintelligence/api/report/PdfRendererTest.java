package com.company.leadershipintelligence.api.report;

import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfRendererTest {

    private static ReportDocument document(List<List<String>> rows) {
        return new ReportDocument("LEADERSHIP_INTELLIGENCE", "Leadership Intelligence Report", false, "Not a performance rating.",
                List.of(new ReportDocument.Section("s", "Summary", "A note", List.of("Item", "Value"), rows),
                        new ReportDocument.Section("e", "Empty section", "Nothing to show.", List.of("A", "B"), List.of())));
    }

    private static String text(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder out = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                out.append(extractor.getTextFromPage(page)).append('\n');
            }
            return out.toString();
        } finally {
            reader.close();
        }
    }

    @Test
    void drawsTheTitleNoticeHeadingsNotesAndCells() throws Exception {
        byte[] pdf = PdfRenderer.render(document(List.of(List.of("Score", "72.5"), List.of("Interpretation", "Healthy"))));
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        String text = text(pdf);
        assertThat(text).contains("Leadership Intelligence Report", "Not a performance rating.", "Summary", "A note", "Score", "72.5",
                "Interpretation", "Healthy", "Empty section", "Nothing to show.", "Page 1");
    }

    @Test
    void isTaggedWithATitleAndALanguage() throws Exception {
        PdfReader reader = new PdfReader(PdfRenderer.render(document(List.of(List.of("a", "1")))));
        try {
            assertThat(reader.getCatalog().get(PdfName.LANG).toString()).isEqualTo("en");
            assertThat(reader.getCatalog().get(PdfName.MARKINFO)).isNotNull();
            assertThat(reader.getInfo().get("Title")).isEqualTo("Leadership Intelligence Report");
        } finally {
            reader.close();
        }
    }

    @Test
    void aLongTableRunsOverPagesAndRepeatsItsHeader() throws Exception {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            rows.add(List.of("Row " + i, "Value " + i));
        }
        byte[] pdf = PdfRenderer.render(document(rows));
        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isGreaterThan(2);
            String second = new PdfTextExtractor(reader).getTextFromPage(2);
            assertThat(second).contains("Item", "Value").contains("Page 2");
        } finally {
            reader.close();
        }
        assertThat(text(pdf)).contains("Row 0", "Row 119");
    }

    @Test
    void accentsAndVeryLongWordsAreDrawn() throws Exception {
        String hash = "a".repeat(64);
        String text = text(PdfRenderer.render(document(List.of(List.of("Café", "Équipe"), List.of("Configuration hash", hash)))));
        assertThat(text).contains("Café", "Équipe");
        assertThat(text.replace("\n", "").replace(" ", "")).contains(hash);
    }

    @Test
    void anEmptyDocumentStillRenders() throws Exception {
        byte[] pdf = PdfRenderer.render(new ReportDocument("X", "Only a title", false, "Notice", List.of()));
        assertThat(text(pdf)).contains("Only a title", "Notice");
    }
}
