package com.company.leadershipintelligence.api.report;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Writes a report as CSV (decision D62): RFC 4180 quoting, CRLF line ends, UTF-8 with a byte-order mark so spreadsheets
 * read accents correctly. Each section is a block: its heading, its column names, its rows, then an empty line. A text
 * cell that a spreadsheet could read as a formula (it starts with =, +, - or @, a tab or a carriage return) is prefixed
 * with an apostrophe; plain numbers, including negative ones, are left alone.
 */
public final class CsvRenderer {

    private static final Pattern NUMBER = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final String EOL = "\r\n";

    private CsvRenderer() {
    }

    public static byte[] render(ReportDocument report) {
        StringBuilder out = new StringBuilder("﻿");
        line(out, List.of(report.title()));
        line(out, List.of(report.notice()));
        for (ReportDocument.Section section : report.sections()) {
            out.append(EOL);
            line(out, List.of(section.heading()));
            if (section.note() != null && !section.note().isBlank()) {
                line(out, List.of(section.note()));
            }
            line(out, section.columns());
            for (List<String> row : section.rows()) {
                line(out, row);
            }
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String cell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && !NUMBER.matcher(text).matches() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static void line(StringBuilder out, List<String> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(cell(cells.get(i)));
        }
        out.append(EOL);
    }
}
