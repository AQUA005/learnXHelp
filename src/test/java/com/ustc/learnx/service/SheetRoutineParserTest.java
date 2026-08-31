package com.ustc.learnx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sheet is a human document, and this is what reading it has to survive.
 *
 * <p>The fixture below is the shape the university actually publishes: some
 * preamble, a header row that starts with "Batch", one column per period
 * labelled with its times, and one row per section. Everything asserted here
 * was a rule in the original routine tool; the point of the test is that the
 * rules survived the move to the server.
 */
class SheetRoutineParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Builds the {@code table} object Google's visualization endpoint returns. */
    private JsonNode table(String... rows) throws Exception {
        StringBuilder json = new StringBuilder("{\"rows\":[");
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) json.append(',');
            json.append("{\"c\":[").append(rows[i]).append("]}");
        }
        return mapper.readTree(json.append("]}").toString());
    }

    private static String cells(String... values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append(',');
            out.append(values[i] == null ? "null" : "{\"v\":\"" + values[i] + "\"}");
        }
        return out.toString();
    }

    private JsonNode sundayTab() throws Exception {
        return table(
                cells("Department of CSE", null, null, null, null, null),
                cells("SUNDAY", null, null, null, null, null),
                cells("Batch", "Sem", "8:30-10:00", "10:15-11:45", "12:00-1:30", "1:45-3:15"),
                cells("45(a)", "3rd", "CSE 3101 (302) - TC-1", null, "CSE 3103 (302) - TC-2", null),
                cells("45(b)", "5th", "CSE 3105 (402) - TC-3", "CSE 3107 (402) - TC-1", null, null),
                cells("SESSION : 2024-2025", null, null, null, null, null));
    }

    @Test
    void readsTheClassesOfOneSectionOnly() throws Exception {
        var day = SheetRoutineParser.parseDay(sundayTab(), "45(b)", Map.of());

        assertThat(day.day()).isEqualTo("SUNDAY");
        assertThat(day.semester()).isEqualTo("5th");
        assertThat(day.session()).isEqualTo("2024-2025");
        assertThat(day.classes()).hasSize(2);

        var first = day.classes().get(0);
        assertThat(first.course()).isEqualTo("CSE 3105");
        assertThat(first.room()).isEqualTo("402");
        assertThat(first.teacherCode()).isEqualTo("TC-3");
        assertThat(first.timeText()).isEqualTo("8:30–10:00");
        assertThat(first.periods()).isEqualTo(1);
    }

    /**
     * The sheet writes times without a meridiem, because nobody teaches at one
     * in the morning. Hours one to six are afternoons.
     */
    @Test
    void readsAfternoonTimesWithoutAMeridiem() throws Exception {
        var day = SheetRoutineParser.parseDay(sundayTab(), "45(a)", Map.of());

        var afternoon = day.classes().get(1);
        assertThat(afternoon.course()).isEqualTo("CSE 3103");
        assertThat(afternoon.startMinute()).isEqualTo(12 * 60);
        assertThat(afternoon.endMinute()).isEqualTo(13 * 60 + 30);
    }

    /**
     * A merged cell arrives as one value followed by empty ones, which reads
     * exactly like a free period -- so a span is configuration. The hint is
     * still checked against the data: it may only swallow columns that really
     * are empty.
     */
    @Test
    void mergesAMultiPeriodClassWhenToldTo() throws Exception {
        var day = SheetRoutineParser.parseDay(sundayTab(), "45(b)", Map.of(2, 2));

        var merged = day.classes().get(0);
        assertThat(merged.periods()).isEqualTo(1);
        assertThat(merged.timeText())
                .as("column 3 has its own class, so the hint must not swallow it")
                .isEqualTo("8:30–10:00");

        var wider = SheetRoutineParser.parseDay(sundayTab(), "45(a)", Map.of(2, 2));
        assertThat(wider.classes().get(0).periods()).isEqualTo(2);
        assertThat(wider.classes().get(0).timeText()).isEqualTo("8:30–11:45");
    }

    @Test
    void listsEverySectionInTheTab() throws Exception {
        var day = SheetRoutineParser.parseDay(sundayTab(), "45(b)", Map.of());
        assertThat(day.sections()).containsExactly("45(a)", "45(b)");
    }

    @Test
    void aSectionThatIsNotListedSimplyHasNoClasses() throws Exception {
        var day = SheetRoutineParser.parseDay(sundayTab(), "44(c)", Map.of());
        assertThat(day.day()).isEqualTo("SUNDAY");
        assertThat(day.classes()).isEmpty();
    }

    @Test
    void resolvesTeacherCodesToNames() throws Exception {
        JsonNode teachers = table(
                cells("Teachers", null, null, null),
                cells(null, "1", "ARH-1", "Dr Ayesha Rahman"),
                cells(null, "2", "KMH-1", "Prof Kamal Hossain"),
                cells("not a teacher row", "x", "SLA-1", "Nobody"));

        var names = SheetRoutineParser.parseTeachers(teachers);

        assertThat(SheetRoutineParser.teacherName(names, "ARH-1")).isEqualTo("Dr Ayesha Rahman");
        assertThat(SheetRoutineParser.teacherName(names, "kmh-1")).isEqualTo("Prof Kamal Hossain");
        assertThat(SheetRoutineParser.teacherName(names, "ARH"))
                .as("cells write the same teacher with and without the suffix")
                .isEqualTo("Dr Ayesha Rahman");
        assertThat(SheetRoutineParser.teacherName(names, "SLA-1"))
                .as("a row without a number is a heading, not a teacher")
                .isEqualTo("SLA-1");
        assertThat(SheetRoutineParser.teacherName(names, "")).isEmpty();
    }

    @Test
    void takesTheSheetIdOutOfAPastedLink() {
        assertThat(LiveRoutineService.extractSheetId(
                "https://docs.google.com/spreadsheets/d/1vPpOGcZH6LIJ23h3/edit#gid=0"))
                .isEqualTo("1vPpOGcZH6LIJ23h3");
        assertThat(LiveRoutineService.extractSheetId("  1vPpOGcZH6LIJ23h3  "))
                .isEqualTo("1vPpOGcZH6LIJ23h3");
    }
}
