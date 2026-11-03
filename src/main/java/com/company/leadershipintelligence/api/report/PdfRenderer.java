package com.company.leadershipintelligence.api.report;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfString;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Draws a report as a plain PDF (decision D62): a title, the intended-use notice, then each section as a heading, its
 * note and a table whose header row repeats on every page. The document is tagged, has a title and a language, and
 * uses only the built-in fonts, so no font is fetched or embedded; characters outside Latin-1 are not drawn. The text of
 * the PDF is exactly the text of the report document. The file also carries a creation time, so the bytes of two
 * renderings of the same report can differ while their content does not.
 */
public final class PdfRenderer {

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
    private static final Font NOTICE = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Font.NORMAL, new Color(0x44, 0x44, 0x44));
    private static final Font HEADING = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
    private static final Font NOTE = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, new Color(0x44, 0x44, 0x44));
    private static final Font HEAD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Font.NORMAL, Color.WHITE);
    private static final Font CELL = FontFactory.getFont(FontFactory.HELVETICA, 8);
    private static final Color HEAD_BACKGROUND = new Color(0x1F, 0x29, 0x37);
    private static final Color RULE = new Color(0xCC, 0xCC, 0xCC);

    private PdfRenderer() {
    }

    public static byte[] render(ReportDocument report) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 48);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setTagged();
            writer.setViewerPreferences(PdfWriter.DisplayDocTitle);
            writer.setPageEvent(new Footer(report.title()));
            document.addTitle(report.title());
            document.addSubject("Leadership Intelligence Scoring Engine report");
            document.addCreator("Leadership Intelligent Dashboard");
            document.open();
            writer.getExtraCatalog().put(PdfName.LANG, new PdfString("en"));

            document.add(new Paragraph(report.title(), TITLE));
            Paragraph notice = new Paragraph(report.notice(), NOTICE);
            notice.setSpacingBefore(4);
            document.add(notice);
            for (ReportDocument.Section section : report.sections()) {
                Paragraph heading = new Paragraph(section.heading(), HEADING);
                heading.setSpacingBefore(14);
                heading.setKeepTogether(true);
                document.add(heading);
                if (section.note() != null && !section.note().isBlank()) {
                    Paragraph note = new Paragraph(section.note(), NOTE);
                    note.setSpacingBefore(2);
                    document.add(note);
                }
                if (!section.rows().isEmpty()) {
                    document.add(table(section));
                }
            }
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("the report could not be drawn as a PDF", e);
        }
        return out.toByteArray();
    }

    private static PdfPTable table(ReportDocument.Section section) throws DocumentException {
        List<String> columns = section.columns();
        PdfPTable table = new PdfPTable(columns.size());
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);
        if (columns.size() == 2) {
            table.setWidths(new float[] {1f, 3f});
        }
        table.setHeaderRows(1);
        for (String column : columns) {
            PdfPCell cell = new PdfPCell(new Phrase(column, HEAD));
            cell.setBackgroundColor(HEAD_BACKGROUND);
            cell.setPadding(4);
            table.addCell(cell);
        }
        for (List<String> row : section.rows()) {
            for (int i = 0; i < columns.size(); i++) {
                PdfPCell cell = new PdfPCell(new Phrase(i < row.size() ? row.get(i) : "", CELL));
                cell.setPadding(4);
                cell.setBorderColor(RULE);
                table.addCell(cell);
            }
        }
        return table;
    }

    /** Page number and report title at the foot of every page. */
    private static final class Footer extends PdfPageEventHelper {
        private final String title;

        Footer(String title) {
            this.title = title;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            Rectangle page = document.getPageSize();
            Phrase left = new Phrase(title, NOTE);
            Phrase right = new Phrase("Page " + writer.getPageNumber(), NOTE);
            ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, left, document.leftMargin(), page.getBottom(24), 0);
            ColumnText.showTextAligned(canvas, Element.ALIGN_RIGHT, right, page.getRight(document.rightMargin()), page.getBottom(24), 0);
        }
    }
}
