package com.example.demo.service;

import com.example.demo.entity.ClassTest;
import com.example.demo.entity.ScheduleItem;
import com.example.demo.repository.ClassTestRepository;
import com.example.demo.repository.ScheduleItemRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@AllArgsConstructor
public class SlotDetectionService {

    private final ScheduleItemRepository scheduleItemRepository;
    private final ClassTestRepository classTestRepository;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeInterval {
        private LocalTime start;
        private LocalTime end;
    }

    public List<TimeInterval> detectFreeSlots(LocalDate date, int durationMinutes) {
        // 1. Get Day of Week
        String dayOfWeekStr = date.getDayOfWeek().name(); // e.g. "MONDAY"

        // 2. Fetch routines for this day of week
        List<ScheduleItem> routines = scheduleItemRepository.findByDayOfWeekOrderByStartTimeAsc(dayOfWeekStr);

        // 3. Fetch exams scheduled on this date
        List<ClassTest> tests = classTestRepository.findAll();
        List<ClassTest> testsOnDate = tests.stream()
                .filter(t -> t.getDateTime().toLocalDate().isEqual(date))
                .toList();

        // 4. Create a list of busy intervals
        List<TimeInterval> busyIntervals = new ArrayList<>();

        for (ScheduleItem routine : routines) {
            busyIntervals.add(new TimeInterval(routine.getStartTime(), routine.getEndTime()));
        }

        for (ClassTest test : testsOnDate) {
            LocalTime testStart = test.getDateTime().toLocalTime();
            LocalTime testEnd = testStart.plusMinutes(test.getDurationMinutes());
            busyIntervals.add(new TimeInterval(testStart, testEnd));
        }

        // 5. Define Academic boundaries: 08:00 to 18:00
        LocalTime dayStart = LocalTime.of(8, 0);
        LocalTime dayEnd = LocalTime.of(18, 0);

        // 6. Merge overlapping busy intervals
        List<TimeInterval> mergedBusy = mergeIntervals(busyIntervals, dayStart, dayEnd);

        // 7. Find gaps
        List<TimeInterval> freeSlots = new ArrayList<>();
        LocalTime current = dayStart;

        for (TimeInterval busy : mergedBusy) {
            if (current.isBefore(busy.getStart())) {
                long duration = java.time.Duration.between(current, busy.getStart()).toMinutes();
                if (duration >= durationMinutes) {
                    freeSlots.add(new TimeInterval(current, busy.getStart()));
                }
            }
            if (busy.getEnd().isAfter(current)) {
                current = busy.getEnd();
            }
        }

        if (current.isBefore(dayEnd)) {
            long duration = java.time.Duration.between(current, dayEnd).toMinutes();
            if (duration >= durationMinutes) {
                freeSlots.add(new TimeInterval(current, dayEnd));
            }
        }

        return freeSlots;
    }

    private List<TimeInterval> mergeIntervals(List<TimeInterval> intervals, LocalTime boundsStart, LocalTime boundsEnd) {
        if (intervals.isEmpty()) {
            return new ArrayList<>();
        }

        // Filter and clamp intervals to the academic bounds
        List<TimeInterval> clamped = new ArrayList<>();
        for (TimeInterval interval : intervals) {
            LocalTime start = interval.getStart();
            LocalTime end = interval.getEnd();

            if (start.isBefore(boundsStart)) start = boundsStart;
            if (end.isAfter(boundsEnd)) end = boundsEnd;

            if (start.isBefore(end)) {
                clamped.add(new TimeInterval(start, end));
            }
        }

        if (clamped.isEmpty()) {
            return new ArrayList<>();
        }

        // Sort by start time
        clamped.sort(Comparator.comparing(TimeInterval::getStart));

        List<TimeInterval> merged = new ArrayList<>();
        TimeInterval active = clamped.get(0);

        for (int i = 1; i < clamped.size(); i++) {
            TimeInterval next = clamped.get(i);
            if (!next.getStart().isAfter(active.getEnd())) {
                // Overlap or touch, merge
                if (next.getEnd().isAfter(active.getEnd())) {
                    active.setEnd(next.getEnd());
                }
            } else {
                merged.add(active);
                active = next;
            }
        }
        merged.add(active);

        return merged;
    }
}
