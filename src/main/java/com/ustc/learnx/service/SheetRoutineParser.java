package com.ustc.learnx.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a weekday tab into classes.
 *
 * <p>The sheet is a human document, not an export: a header row somewhere near
 * the top whose first cell reads "Batch", one column per period labelled with
 * its time range, and one row per section. This walks that shape rather than
 * assuming fixed coordinates, because the rows above the header differ from
 * tab to tab.
 *
 * <p>A cell reads {@code CSE 3101 (302) - TC-1}: the course, the room in
 * brackets, and the teacher's code after it. Codes are resolved to names from
 * the sheet's own teacher tab.
 */
public final class SheetRoutineParser {

    private SheetRoutineParser() {}

    public static final List<String> DAY_KEYS = List.of(
            "SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY");

    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final Pattern ROOM = Pattern.compile("\\(([^)]*)\\)");

    /** One period column, and the times its label carries. */
    private record Column(int index, String startText, String endText, int startMinute, int endMinute) {}

    /** A class as the sheet describes it. */
    public record SheetClass(
            String timeText,
            int startMinute,
            int endMinute,
            int periods,
            String course,
            String room,
            String teacherCode) {}

    /** One weekday tab, parsed for one section. */
    public record ParsedDay(
            String day,
            String semester,
            String session,
            List<SheetClass> classes,
            List<String> sections) {}

    // --- Cell helpers -------------------------------------------------------

    private static String cell(JsonNode row, int index) {
        JsonNode cells = row.path("c");
        if (!cells.isArray() || index >= cells.size()) return "";
        JsonNode value = cells.get(index).path("v");
        return value.isNull() || value.isMissingNode() ? "" : value.asText("");
    }

    private static int cellCount(JsonNode row) {
        JsonNode cells = row.path("c");
        return cells.isArray() ? cells.size() : 0;
    }

    /** Comparison key: spacing and case in a sheet are not meaningful. */
    public static String norm(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase();
    }

    /**
     * Minutes from midnight.
     *
     * <p>The sheet writes times without meridiem: "8:30" is morning and "1:00"
     * is afternoon, because no university teaches at one in the morning. Hours
     * one to six are read as afternoon, exactly as the original tool did.
     */
    static int toMinutes(String text) {
        Matcher matcher = TIME.matcher(String.valueOf(text).replace('.', ':'));
        if (!matcher.find()) return 9999;
        int hour = Integer.parseInt(matcher.group(1));
        int minute = Integer.parseInt(matcher.group(2));
        if (hour >= 1 && hour <= 6) hour += 12;
        return hour * 60 + minute;
    }

    // --- Structure ----------------------------------------------------------

    /** Which weekday this tab is for, from a cell in its first few columns. */
    private static String detectDay(JsonNode rows) {
        for (JsonNode row : rows) {
            for (int c = 0; c < Math.min(3, cellCount(row)); c++) {
                String value = cell(row, c).toUpperCase().trim();
                if (DAY_KEYS.contains(value)) return value;
            }
        }
        return null;
    }

    /** The period columns, read from the row whose first cells say "Batch". */
    private static List<Column> findColumns(JsonNode rows) {
        for (JsonNode row : rows) {
            for (int c = 0; c < Math.min(4, cellCount(row)); c++) {
                if (!"batch".equals(norm(cell(row, c)))) continue;

                List<Column> columns = new ArrayList<>();
                // Two columns along: the one after "Batch" holds the semester.
                for (int i = c + 2; i < cellCount(row); i++) {
                    String label = cell(row, i).trim();
                    if (label.isEmpty()) continue;
                    String[] parts = label.replace('.', ':').split("[-–]");
                    String start = parts.length > 0 ? parts[0].trim() : "";
                    String end = parts.length > 1 ? parts[1].trim() : start;
                    columns.add(new Column(i, start, end, toMinutes(start), toMinutes(end)));
                }
                return columns;
            }
        }
        return List.of();
    }

    /** Every section listed under the "Batch" header, for the section picker. */
    private static List<String> collectSections(JsonNode rows) {
        List<String> found = new ArrayList<>();
        for (int r = 0; r < rows.size(); r++) {
            JsonNode row = rows.get(r);
            for (int c = 0; c < Math.min(4, cellCount(row)); c++) {
                if (!"batch".equals(norm(cell(row, c)))) continue;
                for (int rr = r + 1; rr < rows.size(); rr++) {
                    String value = cell(rows.get(rr), c).trim();
                    if (value.isEmpty()) continue;
                    if (value.toUpperCase().contains("SESSION")) return found;
                    if (DAY_KEYS.contains(value.toUpperCase()) || "batch".equals(norm(value))) continue;
                    if (!found.contains(value)) found.add(value);
                }
                return found;
            }
        }
        return found;
    }

    /** The course, its room and the teacher's code, from one cell. */
    private static SheetClass parseCell(String raw, Column column, Column endColumn, int periods) {
        String text = raw.replaceAll("\\s+", " ").trim();
        if (text.isEmpty() || "-".equals(text) || "—".equals(text)) return null;

        String course = text;
        String room = "";
        String teacher = "";

        Matcher matcher = ROOM.matcher(text);
        if (matcher.find()) {
            room = matcher.group(1).trim();
            course = text.substring(0, matcher.start()).trim();
            teacher = text.substring(matcher.end()).trim().replaceAll("^[-–\\s]+", "");
        }

        return new SheetClass(
                column.startText() + "–" + endColumn.endText(),
                column.startMinute(), endColumn.endMinute(), periods,
                course.isEmpty() ? text : course, room, teacher);
    }

    /**
     * One weekday tab, read for one section.
     *
     * <p>{@code blocks} maps a column index to the number of periods a class
     * starting there runs for. A merged cell arrives as one value followed by
     * empty cells, which is indistinguishable from a free period, so a span can
     * only come from configuration -- but it is still verified here: the
     * following columns must actually be empty before they are swallowed.
     */
    public static ParsedDay parseDay(JsonNode table, String section, Map<Integer, Integer> blocks) {
        JsonNode rows = table.path("rows");
        if (!rows.isArray()) {
            return new ParsedDay(null, "", "", List.of(), List.of());
        }

        String day = detectDay(rows);
        List<Column> columns = findColumns(rows);
        Map<Integer, Column> byIndex = new LinkedHashMap<>();
        columns.forEach(column -> byIndex.put(column.index(), column));

        String session = "";
        for (JsonNode row : rows) {
            String first = cell(row, 0);
            if (first.toUpperCase().contains("SESSION")) {
                int colon = first.indexOf(':');
                if (colon >= 0) session = first.substring(colon + 1).trim();
                break;
            }
        }

        String semester = "";
        List<SheetClass> classes = new ArrayList<>();

        for (JsonNode row : rows) {
            int batchColumn = -1;
            for (int c = 0; c < Math.min(4, cellCount(row)); c++) {
                if (norm(cell(row, c)).equals(norm(section))) { batchColumn = c; break; }
            }
            if (batchColumn < 0) continue;

            semester = cell(row, batchColumn + 1).trim();

            LinkedHashSet<Integer> consumed = new LinkedHashSet<>();
            for (Column column : columns) {
                if (consumed.contains(column.index())) continue;

                int span = blocks.getOrDefault(column.index(), 1);
                int periods = 1;
                for (int s = 1; s < span; s++) {
                    Column next = byIndex.get(column.index() + s);
                    if (next == null || !cell(row, next.index()).trim().isEmpty()) break;
                    periods++;
                }

                Column endColumn = byIndex.getOrDefault(column.index() + periods - 1, column);
                SheetClass parsed = parseCell(cell(row, column.index()), column, endColumn, periods);
                if (parsed == null) continue;

                for (int s = 0; s < periods; s++) consumed.add(column.index() + s);
                classes.add(parsed);
            }
            break;
        }

        classes.sort((a, b) -> Integer.compare(a.startMinute(), b.startMinute()));
        return new ParsedDay(day, semester, session, classes, collectSections(rows));
    }

    /**
     * Teacher codes to names, from the sheet's teacher tab.
     *
     * <p>Rows there are numbered, so a row whose second cell is a number is a
     * teacher and anything else is a heading. A code written "TC-1" is also
     * registered as "TC", since the cells use both.
     */
    public static Map<String, String> parseTeachers(JsonNode table) {
        Map<String, String> names = new LinkedHashMap<>();
        JsonNode rows = table.path("rows");
        if (!rows.isArray()) return names;

        for (JsonNode row : rows) {
            if (!cell(row, 1).trim().matches("\\d+")) continue;
            String code = cell(row, 2).trim();
            String name = cell(row, 3).trim();
            if (code.isEmpty() || name.isEmpty()) continue;
            names.put(norm(code), name);
            if (code.contains("-")) names.putIfAbsent(norm(code.split("-")[0]), name);
        }
        return names;
    }

    /** The name behind a code, falling back to the code as written. */
    public static String teacherName(Map<String, String> names, String code) {
        if (code == null || code.isBlank()) return "";
        String exact = names.get(norm(code));
        if (exact != null) return exact;
        String base = code.split("[-\\s]")[0];
        return names.getOrDefault(norm(base), code);
    }
}
