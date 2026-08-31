package com.ustc.learnx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ustc.learnx.common.AccessDeniedException;
import com.ustc.learnx.common.NotFoundException;
import com.ustc.learnx.common.ValidationException;
import com.ustc.learnx.dto.RoutineDtos.LiveClass;
import com.ustc.learnx.dto.RoutineDtos.LiveDay;
import com.ustc.learnx.dto.RoutineDtos.LiveRoutine;
import com.ustc.learnx.dto.RoutineDtos.OverrideRequest;
import com.ustc.learnx.dto.RoutineDtos.OverrideResponse;
import com.ustc.learnx.dto.RoutineDtos.SourceRequest;
import com.ustc.learnx.dto.RoutineDtos.SourceResponse;
import com.ustc.learnx.entity.AuditLog;
import com.ustc.learnx.entity.RoutineOverride;
import com.ustc.learnx.entity.RoutineSource;
import com.ustc.learnx.entity.StudentClass;
import com.ustc.learnx.entity.University;
import com.ustc.learnx.entity.User;
import com.ustc.learnx.repository.AuditLogRepository;
import com.ustc.learnx.repository.RoutineOverrideRepository;
import com.ustc.learnx.repository.RoutineSourceRepository;
import com.ustc.learnx.repository.StudentClassRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The routine as the university publishes it, plus what has changed since.
 *
 * <p>Three things meet here. Where a department's sheet is; what that sheet
 * currently says, read through {@link SheetRoutineClient}; and the additions
 * and cancellations a class representative has posted against it. The first
 * two are shared by everyone in a department, the third by everyone in a class.
 */
@Service
@RequiredArgsConstructor
public class LiveRoutineService {

    private static final Logger log = LoggerFactory.getLogger(LiveRoutineService.class);

    private static final Pattern SHEET_IN_URL = Pattern.compile("/d/([A-Za-z0-9_-]+)");
    private static final Pattern HHMM = Pattern.compile("^(\\d{1,2}):(\\d{2})$");

    private final RoutineSourceRepository routineSourceRepository;
    private final RoutineOverrideRepository routineOverrideRepository;
    private final StudentClassRepository studentClassRepository;
    private final AuditLogRepository auditLogRepository;
    private final CurrentUserService currentUserService;
    private final SheetRoutineClient sheetClient;

    private final ObjectMapper mapper = new ObjectMapper();

    // --- Reading the routine ------------------------------------------------

    /**
     * The week, for one section.
     *
     * <p>A caller may pass a sheet of their own. That is the escape hatch for a
     * department whose administrator has not configured one yet: it is used for
     * that request only and never stored, so one student's experiment cannot
     * change what their classmates see.
     */
    @Transactional(readOnly = true)
    public LiveRoutine live(String section, String sheetOverride, String dayGidsOverride,
                            String teacherGidOverride, boolean refresh) {
        User user = currentUserService.requireCurrentUser();

        String sheetId = null;
        String dayGids = null;
        String teacherGid = null;
        String blockHints = null;

        if (sheetOverride != null && !sheetOverride.isBlank()) {
            sheetId = extractSheetId(sheetOverride);
            dayGids = dayGidsOverride;
            teacherGid = teacherGidOverride;
        } else {
            Optional<RoutineSource> source = resolveSource(user);
            if (source.isPresent()) {
                sheetId = source.get().getSheetId();
                dayGids = source.get().getDayGids();
                teacherGid = source.get().getTeacherGid();
                blockHints = source.get().getBlockHints();
            }
        }

        List<String> gids = splitGids(dayGids);
        if (sheetId == null || gids.isEmpty()) {
            return new LiveRoutine(false, section, "", "", List.of(), List.of(),
                    overridesForWeek(user), null, false, null, 0, 0,
                    "No routine sheet has been set for your department yet.");
        }
        if (!SheetRoutineClient.isValidSheetId(sheetId)) {
            throw new ValidationException("That does not look like a Google Sheet link or id");
        }
        if (refresh) {
            sheetClient.evict(sheetId);
        }

        Map<String, String> teachers = Map.of();
        if (teacherGid != null && !teacherGid.isBlank()) {
            teachers = sheetClient.fetchTab(sheetId, teacherGid.trim())
                    .map(tab -> SheetRoutineParser.parseTeachers(tab.table()))
                    .orElse(Map.of());
        }

        String wanted = section == null || section.isBlank() ? defaultSection(user) : section.trim();
        Map<String, Map<Integer, Integer>> blocks = blocksFor(blockHints, wanted);

        List<LiveDay> days = new ArrayList<>();
        LinkedHashSet<String> sections = new LinkedHashSet<>();
        String semester = "";
        String session = "";
        boolean stale = false;
        Instant fetchedAt = null;
        int loaded = 0;

        for (String gid : gids) {
            Optional<SheetRoutineClient.Tab> tab = sheetClient.fetchTab(sheetId, gid);
            if (tab.isEmpty()) continue;

            JsonNode table = tab.get().table();
            stale = stale || tab.get().stale();
            if (fetchedAt == null || tab.get().fetchedAt().isBefore(fetchedAt)) {
                fetchedAt = tab.get().fetchedAt();
            }

            // Parsed once to learn which weekday this tab is, and again only if
            // that day has merged-cell hints to apply.
            SheetRoutineParser.ParsedDay parsed = SheetRoutineParser.parseDay(table, wanted, Map.of());
            sections.addAll(parsed.sections());
            if (parsed.day() == null) continue;

            loaded++;
            if (semester.isEmpty()) semester = parsed.semester();
            if (session.isEmpty()) session = parsed.session();

            // Re-parse with this day's own block hints now that the day is known.
            Map<Integer, Integer> dayBlocks = blocks.getOrDefault(parsed.day(), Map.of());
            SheetRoutineParser.ParsedDay withBlocks = dayBlocks.isEmpty() ? parsed
                    : SheetRoutineParser.parseDay(table, wanted, dayBlocks);

            List<LiveClass> classes = new ArrayList<>();
            for (SheetRoutineParser.SheetClass item : withBlocks.classes()) {
                classes.add(new LiveClass(
                        keyOf(item.timeText(), item.course()),
                        item.timeText(), item.startMinute(), item.endMinute(), item.periods(),
                        item.course(), item.room(), item.teacherCode(),
                        SheetRoutineParser.teacherName(teachers, item.teacherCode())));
            }
            days.add(new LiveDay(withBlocks.day(), classes));
        }

        days.sort((a, b) -> Integer.compare(
                SheetRoutineParser.DAY_KEYS.indexOf(a.day()),
                SheetRoutineParser.DAY_KEYS.indexOf(b.day())));

        String message = loaded == 0
                ? "The sheet could not be read. Check the link and the tab ids, then try again."
                : null;

        return new LiveRoutine(true, wanted, semester, session,
                List.copyOf(sections), days, overridesForWeek(user),
                "https://docs.google.com/spreadsheets/d/" + sheetId + "/edit",
                stale, fetchedAt, loaded, gids.size(), message);
    }

    // --- Where the sheet is -------------------------------------------------

    /** The caller's department's sheet, or their university's fallback. */
    @Transactional(readOnly = true)
    public Optional<RoutineSource> resolveSource(User user) {
        University university = user.getUniversity();
        if (university == null) return Optional.empty();

        String department = user.getDepartment();
        if (department != null && !department.isBlank()) {
            Optional<RoutineSource> own =
                    routineSourceRepository.findByUniversityAndDepartment(university, department);
            if (own.isPresent()) return own;
        }
        return routineSourceRepository.findByUniversityAndDepartment(university, RoutineSource.FALLBACK);
    }

    /** What the caller's screen should use, for the setup panel to show. */
    @Transactional(readOnly = true)
    public SourceResponse mySource() {
        User user = currentUserService.requireCurrentUser();
        return resolveSource(user).map(LiveRoutineService::toSourceResponse).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<SourceResponse> listSources() {
        User user = currentUserService.requireCurrentUser();
        return routineSourceRepository.findByUniversityOrderByDepartmentAsc(user.getUniversity())
                .stream().map(LiveRoutineService::toSourceResponse).toList();
    }

    /**
     * Saves one department's sheet, or the university's fallback.
     *
     * <p>An empty department is the fallback rather than a mistake, so the two
     * cases share this path and the unique index keeps a department from
     * gaining two rows.
     */
    @Transactional
    public SourceResponse saveSource(SourceRequest request) {
        User user = currentUserService.requireCurrentUser();
        University university = user.getUniversity();
        if (university == null) {
            throw new ValidationException("Your account is not attached to a university");
        }

        String sheetId = extractSheetId(request.sheet());
        if (!SheetRoutineClient.isValidSheetId(sheetId)) {
            throw new ValidationException("That does not look like a Google Sheet link or id");
        }
        List<String> gids = splitGids(request.dayGids());
        if (gids.isEmpty()) {
            throw new ValidationException("Enter at least one weekday tab id");
        }
        String teacherGid = request.teacherGid() == null ? null : request.teacherGid().trim();
        if (teacherGid != null && !teacherGid.isBlank() && !SheetRoutineClient.isValidGid(teacherGid)) {
            throw new ValidationException("A tab id is the number after #gid= in the sheet's address");
        }
        if (request.blockHints() != null && !request.blockHints().isBlank()) {
            try {
                mapper.readTree(request.blockHints());
            } catch (Exception ex) {
                throw new ValidationException("The merged-cell hints are not valid JSON");
            }
        }

        String department = request.department() == null ? RoutineSource.FALLBACK : request.department().trim();

        boolean creating = routineSourceRepository
                .findByUniversityAndDepartment(university, department).isEmpty();

        RoutineSource source = routineSourceRepository
                .findByUniversityAndDepartment(university, department)
                .orElseGet(() -> RoutineSource.builder()
                        .university(university)
                        .department(department)
                        .build());

        source.setSheetId(sheetId);
        source.setDayGids(String.join(",", gids));
        source.setTeacherGid(teacherGid == null || teacherGid.isBlank() ? null : teacherGid);
        source.setBlockHints(request.blockHints());
        source.setUpdatedAt(LocalDateTime.now());
        source.setUpdatedBy(user.getUsername());

        RoutineSource saved = routineSourceRepository.save(source);
        sheetClient.evict(sheetId);

        record_("ROUTINE_SOURCE", saved.getId(), creating ? "CREATE" : "UPDATE", user,
                String.format("Routine sheet for '%s' set to %s (tabs %s)",
                        department.isEmpty() ? "the whole university" : department,
                        sheetId, saved.getDayGids()));

        return toSourceResponse(saved);
    }

    @Transactional
    public void deleteSource(Long id) {
        User user = currentUserService.requireCurrentUser();
        RoutineSource source = routineSourceRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No such routine source"));
        currentUserService.assertSameUniversity(source.getUniversity());

        routineSourceRepository.delete(source);
        record_("ROUTINE_SOURCE", id, "DELETE", user, String.format(
                "Removed the routine sheet for '%s'",
                source.getDepartment().isEmpty() ? "the whole university" : source.getDepartment()));
    }

    // --- Additions and cancellations ---------------------------------------

    /** Everything posted for the caller's class, from a week ago onwards. */
    @Transactional(readOnly = true)
    public List<OverrideResponse> overridesForWeek(User user) {
        StudentClass target = user.getStudentClass();
        if (target == null) return List.of();
        LocalDate today = LocalDate.now();
        return routineOverrideRepository
                .findByStudentClassAndOnDateBetweenOrderByOnDateAscStartMinuteAsc(
                        target, today.minusWeeks(1), today.plusWeeks(4))
                .stream().map(LiveRoutineService::toOverrideResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OverrideResponse> listOverrides(LocalDate from, LocalDate to) {
        User user = currentUserService.requireCurrentUser();
        StudentClass target = user.getStudentClass();
        if (target == null) return List.of();
        LocalDate start = from == null ? LocalDate.now().minusWeeks(1) : from;
        LocalDate end = to == null ? start.plusWeeks(5) : to;
        return routineOverrideRepository
                .findByStudentClassAndOnDateBetweenOrderByOnDateAscStartMinuteAsc(target, start, end)
                .stream().map(LiveRoutineService::toOverrideResponse).toList();
    }

    /**
     * Posts an addition or a cancellation for a whole class.
     *
     * <p>A class representative may only post for their own class. A teacher or
     * administrator names the class they mean, because they belong to none.
     */
    @Transactional
    public OverrideResponse addOverride(OverrideRequest request) {
        User user = currentUserService.requireCurrentUser();
        StudentClass target = resolveWritableClass(user, request.studentClassId());

        RoutineOverride.Kind kind;
        try {
            kind = RoutineOverride.Kind.valueOf(request.kind().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException("An override is either ADDED or CANCELLED");
        }

        RoutineOverride.RoutineOverrideBuilder builder = RoutineOverride.builder()
                .university(target.getUniversity())
                .studentClass(target)
                .onDate(request.date())
                .kind(kind)
                .note(blankToNull(request.note()))
                .createdBy(user.getUsername())
                .createdAt(LocalDateTime.now());

        String details;
        if (kind == RoutineOverride.Kind.CANCELLED) {
            if (request.targetKey() == null || request.targetKey().isBlank()) {
                throw new ValidationException("Say which class is cancelled");
            }
            builder.targetKey(request.targetKey().trim())
                    .courseName(blankToNull(request.course()));
            details = String.format("Cancelled '%s' on %s", request.targetKey().trim(), request.date());
        } else {
            if (request.course() == null || request.course().isBlank()) {
                throw new ValidationException("Enter a course name");
            }
            Integer start = toMinutes(request.start());
            Integer end = toMinutes(request.end());
            if (start == null || end == null) {
                throw new ValidationException("Enter a start and an end time");
            }
            if (end <= start) {
                throw new ValidationException("The end time must be after the start time");
            }
            builder.courseName(request.course().trim())
                    .roomNo(blankToNull(request.room()))
                    .teacherName(blankToNull(request.teacher()))
                    .startMinute(start)
                    .endMinute(end);
            details = String.format("Added '%s' on %s at %s", request.course().trim(),
                    request.date(), request.start());
        }

        RoutineOverride saved = routineOverrideRepository.save(builder.build());
        record_("ROUTINE_OVERRIDE", saved.getId(), "CREATE", user,
                details + " for " + target.getClassName());
        return toOverrideResponse(saved);
    }

    @Transactional
    public void deleteOverride(Long id) {
        User user = currentUserService.requireCurrentUser();
        RoutineOverride existing = routineOverrideRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("No such change"));
        currentUserService.assertSameUniversity(existing.getUniversity());

        if (user.getRole() == User.Role.CR || user.getRole() == User.Role.STUDENT) {
            Long own = user.getStudentClass() == null ? null : user.getStudentClass().getId();
            if (own == null || !existing.getStudentClass().getId().equals(own)) {
                throw new AccessDeniedException("You cannot change another class's routine");
            }
        }

        routineOverrideRepository.delete(existing);
        record_("ROUTINE_OVERRIDE", id, "DELETE", user, String.format(
                "Removed the %s of '%s' on %s",
                existing.getKind() == RoutineOverride.Kind.CANCELLED ? "cancellation" : "addition",
                existing.getCourseName() == null ? existing.getTargetKey() : existing.getCourseName(),
                existing.getOnDate()));
    }

    // --- Helpers ------------------------------------------------------------

    private StudentClass resolveWritableClass(User user, Long requestedId) {
        if (user.getRole() == User.Role.CR || user.getRole() == User.Role.STUDENT) {
            if (user.getStudentClass() == null) {
                throw new ValidationException("Your account is not assigned to a class yet");
            }
            return user.getStudentClass();
        }
        if (requestedId == null) {
            throw new ValidationException("Choose the class this applies to");
        }
        StudentClass target = studentClassRepository.findById(requestedId)
                .orElseThrow(() -> new NotFoundException("Class not found"));
        currentUserService.assertSameUniversity(target.getUniversity());
        return target;
    }

    /**
     * The section to read when the caller has not chosen one.
     *
     * <p>The sheet writes a section as "45(b)" -- the batch with the section in
     * brackets -- while LearnX stores the two separately and often with a word
     * in front: "Batch 45" and "Section B". The words are dropped so the two
     * naming habits meet in the middle. This is a guess, and the screen says so
     * when it does not match anything the sheet lists.
     */
    private String defaultSection(User user) {
        String batch = shortLabel(user.getBatch());
        String section = shortLabel(user.getSection());
        if (!batch.isEmpty() && !section.isEmpty()) {
            return (batch + "(" + section + ")").toLowerCase();
        }
        return batch.toLowerCase();
    }

    /** "Batch 45" becomes "45", "Section B" becomes "B". */
    private static String shortLabel(String value) {
        if (value == null) return "";
        return value.replaceAll("(?i)^\\s*(batch|section|semester)\\s+", "").trim();
    }

    /** Merged-cell hints for one section, as day to column to span. */
    private Map<String, Map<Integer, Integer>> blocksFor(String json, String section) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode forSection = null;
            var fields = root.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                if (SheetRoutineParser.norm(entry.getKey()).equals(SheetRoutineParser.norm(section))) {
                    forSection = entry.getValue();
                    break;
                }
            }
            if (forSection == null) return Map.of();

            Map<String, Map<Integer, Integer>> byDay = new LinkedHashMap<>();
            var days = forSection.fields();
            while (days.hasNext()) {
                var day = days.next();
                Map<Integer, Integer> columns = new LinkedHashMap<>();
                var cells = day.getValue().fields();
                while (cells.hasNext()) {
                    var cellEntry = cells.next();
                    try {
                        columns.put(Integer.parseInt(cellEntry.getKey()), cellEntry.getValue().asInt(1));
                    } catch (NumberFormatException ignored) {
                        // A hint keyed by something other than a column index is
                        // ignored rather than failing the whole screen.
                    }
                }
                byDay.put(day.getKey().toUpperCase(), columns);
            }
            return byDay;
        } catch (Exception ex) {
            log.warn("Ignoring unreadable merged-cell hints: {}", ex.toString());
            return Map.of();
        }
    }

    /** How a cancellation names a class in the sheet. */
    public static String keyOf(String timeText, String course) {
        return timeText + "|" + SheetRoutineParser.norm(course);
    }

    static String extractSheetId(String value) {
        if (value == null) return null;
        Matcher matcher = SHEET_IN_URL.matcher(value);
        return matcher.find() ? matcher.group(1) : value.trim();
    }

    private static List<String> splitGids(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("[^0-9]+"))
                .filter(part -> !part.isBlank())
                .filter(SheetRoutineClient::isValidGid)
                .toList();
    }

    private static Integer toMinutes(String hhmm) {
        if (hhmm == null) return null;
        Matcher matcher = HHMM.matcher(hhmm.trim());
        if (!matcher.matches()) return null;
        return Integer.parseInt(matcher.group(1)) * 60 + Integer.parseInt(matcher.group(2));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String timeTextOf(RoutineOverride override) {
        if (override.getStartMinute() == null || override.getEndMinute() == null) return "";
        return format(override.getStartMinute()) + "–" + format(override.getEndMinute());
    }

    private static String format(int minutes) {
        int hour = (minutes / 60) % 12;
        if (hour == 0) hour = 12;
        return hour + ":" + String.format("%02d", minutes % 60);
    }

    private static SourceResponse toSourceResponse(RoutineSource source) {
        return new SourceResponse(
                source.getId(),
                source.getDepartment(),
                source.getSheetId(),
                "https://docs.google.com/spreadsheets/d/" + source.getSheetId() + "/edit",
                source.getDayGids(),
                source.getTeacherGid(),
                source.getBlockHints(),
                source.getUpdatedBy(),
                source.getUpdatedAt());
    }

    private static OverrideResponse toOverrideResponse(RoutineOverride override) {
        return new OverrideResponse(
                override.getId(),
                override.getOnDate(),
                override.getKind().name(),
                override.getTargetKey(),
                override.getCourseName(),
                override.getRoomNo(),
                override.getTeacherName(),
                override.getStartMinute(),
                override.getEndMinute(),
                timeTextOf(override),
                override.getNote(),
                override.getCreatedBy(),
                override.getCreatedAt(),
                override.getStudentClass() == null ? null : override.getStudentClass().getId(),
                override.getStudentClass() == null ? null : override.getStudentClass().getClassName());
    }

    private void record_(String entityType, Long entityId, String action, User user, String details) {
        auditLogRepository.save(AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .changedBy(user.getUsername())
                .timestamp(LocalDateTime.now())
                .details(details)
                .university(user.getUniversity())
                .build());
    }
}
