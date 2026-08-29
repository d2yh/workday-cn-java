package com.github.d2yh.workday;

import com.github.d2yh.workday.strategy.FestivalStrategy;
import com.github.d2yh.workday.strategy.OffDayStrategy;
import com.github.d2yh.workday.strategy.WeekendOnlyStrategy;
import com.github.d2yh.workday.util.WorkdayUtils;
import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * WorkdayUtils 单元测试
 */
public class WorkdayUtilsTest {

    // ──────────────── Weekend / Weekday Check ────────────────

    @Test
    public void testIsWeekend() {
        assertTrue(WorkdayUtils.isWeekend(LocalDate.of(2025, 10, 4)));  // Saturday
        assertTrue(WorkdayUtils.isWeekend(LocalDate.of(2025, 10, 5)));  // Sunday
        assertFalse(WorkdayUtils.isWeekend(LocalDate.of(2025, 10, 6))); // Monday
        assertFalse(WorkdayUtils.isWeekend(LocalDate.of(2025, 10, 1))); // Wednesday
    }

    @Test
    public void testIsWeekday() {
        assertTrue(WorkdayUtils.isWeekday(LocalDate.of(2025, 10, 6)));  // Monday
        assertTrue(WorkdayUtils.isWeekday(LocalDate.of(2025, 10, 1)));  // Wednesday
        assertFalse(WorkdayUtils.isWeekday(LocalDate.of(2025, 10, 4))); // Saturday
        assertFalse(WorkdayUtils.isWeekday(LocalDate.of(2025, 10, 5))); // Sunday
    }

    // ──────────────── isWorkDay ────────────────

    @Test
    public void testIsWorkDayDefault() {
        // Default strategy is WEEKEND_ONLY
        assertTrue(WorkdayUtils.isWorkDay(LocalDate.of(2025, 10, 15))); // Wednesday
        assertFalse(WorkdayUtils.isWorkDay(LocalDate.of(2025, 10, 4))); // Saturday
        assertFalse(WorkdayUtils.isWorkDay(LocalDate.of(2025, 10, 5))); // Sunday
    }

    @Test
    public void testIsWorkDayWithFestivalStrategy() {
        OffDayStrategy festival = new FestivalStrategy();
        // 2025-10-01 is National Day → not a work day under FESTIVAL
        assertFalse(WorkdayUtils.isWorkDay(LocalDate.of(2025, 10, 1), festival));
        // 2025-10-04 is Saturday → not a work day
        assertFalse(WorkdayUtils.isWorkDay(LocalDate.of(2025, 10, 4), festival));
        // 2025-10-06 is Monday → work day
        assertTrue(WorkdayUtils.isWorkDay(LocalDate.of(2025, 10, 6), festival));
    }

    // ──────────────── addWorkDays ────────────────

    @Test
    public void testAddWorkDaysZero() {
        LocalDate wed = LocalDate.of(2025, 10, 15); // Wednesday
        assertEquals(wed, WorkdayUtils.addWorkDays(wed, 0));
    }

    @Test
    public void testAddWorkDaysZeroFromWeekend() {
        // n=0 from Saturday → advances to Monday
        LocalDate sat = LocalDate.of(2025, 10, 4);
        assertEquals(LocalDate.of(2025, 10, 6), WorkdayUtils.addWorkDays(sat, 0));
    }

    @Test
    public void testAddWorkDaysOne() {
        // Friday + 1 work day = Monday
        LocalDate fri = LocalDate.of(2025, 10, 17);
        assertEquals(LocalDate.of(2025, 10, 20), WorkdayUtils.addWorkDays(fri, 1));
    }

    @Test
    public void testAddWorkDaysFive() {
        // Wednesday Oct 15 + 5 work days = Wednesday Oct 22
        LocalDate start = LocalDate.of(2025, 10, 15);
        assertEquals(LocalDate.of(2025, 10, 22), WorkdayUtils.addWorkDays(start, 5));
    }

    @Test
    public void testAddWorkDaysCrossMonth() {
        // Jan 30 (Fri) + 1 work day = Feb 2 (Mon) — 2025-01-31 is Spring Festival!
        // Under WEEKEND_ONLY: Jan 30(Thu) + 1 = Jan 31(Fri)
        LocalDate jan30 = LocalDate.of(2025, 1, 30); // Thursday
        assertEquals(LocalDate.of(2025, 1, 31), WorkdayUtils.addWorkDays(jan30, 1));
    }

    @Test
    public void testAddWorkDaysWithFestivalStrategy() {
        OffDayStrategy festival = new FestivalStrategy();
        // 2025-09-30 (Tue) + 1 work day: Oct 1,2,3 are National Day → Oct 6 (Mon)
        // (Oct 4,5 are weekends)
        LocalDate sep30 = LocalDate.of(2025, 9, 30);
        assertEquals(LocalDate.of(2025, 10, 6), WorkdayUtils.addWorkDays(sep30, 1, festival));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddWorkDaysNegativeThrows() {
        WorkdayUtils.addWorkDays(LocalDate.of(2025, 10, 15), -1);
    }

    // ──────────────── subtractWorkDays ────────────────

    @Test
    public void testSubtractWorkDaysZero() {
        LocalDate wed = LocalDate.of(2025, 10, 15);
        assertEquals(wed, WorkdayUtils.subtractWorkDays(wed, 0));
    }

    @Test
    public void testSubtractWorkDaysZeroFromWeekend() {
        // n=0 from Saturday → goes back to Friday
        LocalDate sat = LocalDate.of(2025, 10, 4);
        assertEquals(LocalDate.of(2025, 10, 3), WorkdayUtils.subtractWorkDays(sat, 0));
    }

    @Test
    public void testSubtractWorkDaysOne() {
        // Monday - 1 work day = Friday
        LocalDate mon = LocalDate.of(2025, 10, 6);
        assertEquals(LocalDate.of(2025, 10, 3), WorkdayUtils.subtractWorkDays(mon, 1));
    }

    @Test
    public void testSubtractWorkDaysFive() {
        // Wednesday Oct 22 - 5 work days = Wednesday Oct 15
        LocalDate start = LocalDate.of(2025, 10, 22);
        assertEquals(LocalDate.of(2025, 10, 15), WorkdayUtils.subtractWorkDays(start, 5));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSubtractWorkDaysNegativeThrows() {
        WorkdayUtils.subtractWorkDays(LocalDate.of(2025, 10, 15), -1);
    }

    // ──────────────── nextWorkDay / previousWorkDay ────────────────

    @Test
    public void testNextWorkDayFromWeekday() {
        // Wednesday → Thursday
        LocalDate wed = LocalDate.of(2025, 10, 15);
        assertEquals(LocalDate.of(2025, 10, 16), WorkdayUtils.nextWorkDay(wed));
    }

    @Test
    public void testNextWorkDayFromFriday() {
        // Friday → Monday
        LocalDate fri = LocalDate.of(2025, 10, 17);
        assertEquals(LocalDate.of(2025, 10, 20), WorkdayUtils.nextWorkDay(fri));
    }

    @Test
    public void testNextWorkDayFromSaturday() {
        // Saturday → Monday
        LocalDate sat = LocalDate.of(2025, 10, 4);
        assertEquals(LocalDate.of(2025, 10, 6), WorkdayUtils.nextWorkDay(sat));
    }

    @Test
    public void testPreviousWorkDayFromWeekday() {
        // Wednesday → Tuesday
        LocalDate wed = LocalDate.of(2025, 10, 15);
        assertEquals(LocalDate.of(2025, 10, 14), WorkdayUtils.previousWorkDay(wed));
    }

    @Test
    public void testPreviousWorkDayFromMonday() {
        // Monday → Friday
        LocalDate mon = LocalDate.of(2025, 10, 6);
        assertEquals(LocalDate.of(2025, 10, 3), WorkdayUtils.previousWorkDay(mon));
    }

    @Test
    public void testPreviousWorkDayFromSunday() {
        // Sunday → Friday
        LocalDate sun = LocalDate.of(2025, 10, 5);
        assertEquals(LocalDate.of(2025, 10, 3), WorkdayUtils.previousWorkDay(sun));
    }

    // ──────────────── workDaysBetween ────────────────

    @Test
    public void testWorkDaysBetweenSameDay() {
        LocalDate wed = LocalDate.of(2025, 10, 15);
        assertEquals(1, WorkdayUtils.workDaysBetween(wed, wed));
    }

    @Test
    public void testWorkDaysBetweenSameDayWeekend() {
        LocalDate sat = LocalDate.of(2025, 10, 4);
        assertEquals(0, WorkdayUtils.workDaysBetween(sat, sat));
    }

    @Test
    public void testWorkDaysBetweenOneWeek() {
        // Mon Oct 6 to Fri Oct 10 = 5 work days
        LocalDate mon = LocalDate.of(2025, 10, 6);
        LocalDate fri = LocalDate.of(2025, 10, 10);
        assertEquals(5, WorkdayUtils.workDaysBetween(mon, fri));
    }

    @Test
    public void testWorkDaysBetweenIncludesWeekend() {
        // Fri Oct 3 to Mon Oct 6 = 2 work days (Fri + Mon, skip Sat/Sun)
        LocalDate fri = LocalDate.of(2025, 10, 3);
        LocalDate mon = LocalDate.of(2025, 10, 6);
        assertEquals(2, WorkdayUtils.workDaysBetween(fri, mon));
    }

    @Test
    public void testWorkDaysBetweenFullMonth() {
        // October 2025: 31 days, ~23 work days (varies by weekends)
        LocalDate oct1 = LocalDate.of(2025, 10, 1);
        LocalDate oct31 = LocalDate.of(2025, 10, 31);
        int workDays = WorkdayUtils.workDaysBetween(oct1, oct31);
        assertTrue(workDays > 18); // at least 18 work days in any month
        assertTrue(workDays < 28); // at most 27 work days
    }

    @Test
    public void testWorkDaysBetweenWithFestivalStrategy() {
        OffDayStrategy festival = new FestivalStrategy();
        // Oct 2025 with festival: Oct 1,2,3 are off (National Day) + weekends
        LocalDate oct1 = LocalDate.of(2025, 10, 1);
        LocalDate oct10 = LocalDate.of(2025, 10, 10);
        int workDaysDefault = WorkdayUtils.workDaysBetween(oct1, oct10);
        int workDaysFestival = WorkdayUtils.workDaysBetween(oct1, oct10, festival);
        assertTrue(workDaysFestival < workDaysDefault);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWorkDaysBetweenFromAfterToThrows() {
        WorkdayUtils.workDaysBetween(LocalDate.of(2025, 10, 10), LocalDate.of(2025, 10, 1));
    }

    // ──────────────── getOffDaySet ────────────────

    @Test
    public void testGetOffDaySetDefault() {
        Set<String> offDays = WorkdayUtils.getOffDaySet(2025);
        assertFalse(offDays.isEmpty());
        // All weekends should be in the set
        assertTrue(offDays.contains("2025-10-04")); // Saturday
        assertTrue(offDays.contains("2025-10-05")); // Sunday
        // Weekdays should NOT be in the set
        assertFalse(offDays.contains("2025-10-06")); // Monday
    }

    @Test
    public void testGetOffDaySetFestival() {
        OffDayStrategy festival = new FestivalStrategy();
        Set<String> offDays = WorkdayUtils.getOffDaySet(2025, festival);
        // Should include National Day
        assertTrue(offDays.contains("2025-10-01"));
        assertTrue(offDays.contains("2025-10-02"));
        assertTrue(offDays.contains("2025-10-03"));
        // Should have more off days than WEEKEND_ONLY
        Set<String> weekendOnly = WorkdayUtils.getOffDaySet(2025);
        assertTrue(offDays.size() > weekendOnly.size());
    }

    // ──────────────── Consistency Tests ────────────────

    @Test
    public void testAddAndSubtractConsistency() {
        LocalDate start = LocalDate.of(2025, 10, 15); // Wednesday
        LocalDate forward = WorkdayUtils.addWorkDays(start, 10);
        LocalDate back = WorkdayUtils.subtractWorkDays(forward, 10);
        assertEquals(start, back);
    }

    @Test
    public void testWorkDaysBetweenMatchesAddWorkDays() {
        LocalDate start = LocalDate.of(2025, 10, 6); // Monday
        int n = 5;
        LocalDate end = WorkdayUtils.addWorkDays(start, n);
        // workDaysBetween is inclusive of both endpoints
        int count = WorkdayUtils.workDaysBetween(start, end);
        assertEquals(n + 1, count); // +1 because both endpoints are work days and included
    }

    // ──────────────── java.util.Date Overload Tests ────────────────

    private static Date toDate(LocalDate ld) {
        return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static LocalDate toLocalDate(Date d) {
        return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    @Test
    public void testDateIsWeekend() {
        assertTrue(WorkdayUtils.isWeekend(toDate(LocalDate.of(2025, 10, 4))));
        assertFalse(WorkdayUtils.isWeekend(toDate(LocalDate.of(2025, 10, 6))));
    }

    @Test
    public void testDateIsWeekday() {
        assertTrue(WorkdayUtils.isWeekday(toDate(LocalDate.of(2025, 10, 6))));
        assertFalse(WorkdayUtils.isWeekday(toDate(LocalDate.of(2025, 10, 4))));
    }

    @Test
    public void testDateIsWorkDay() {
        assertTrue(WorkdayUtils.isWorkDay(toDate(LocalDate.of(2025, 10, 15))));
        assertFalse(WorkdayUtils.isWorkDay(toDate(LocalDate.of(2025, 10, 4))));
    }

    @Test
    public void testDateAddWorkDays() {
        Date fri = toDate(LocalDate.of(2025, 10, 17));
        Date result = WorkdayUtils.addWorkDays(fri, 1);
        assertEquals(LocalDate.of(2025, 10, 20), toLocalDate(result));
    }

    @Test
    public void testDateSubtractWorkDays() {
        Date mon = toDate(LocalDate.of(2025, 10, 6));
        Date result = WorkdayUtils.subtractWorkDays(mon, 1);
        assertEquals(LocalDate.of(2025, 10, 3), toLocalDate(result));
    }

    @Test
    public void testDateNextWorkDay() {
        Date fri = toDate(LocalDate.of(2025, 10, 17));
        Date result = WorkdayUtils.nextWorkDay(fri);
        assertEquals(LocalDate.of(2025, 10, 20), toLocalDate(result));
    }

    @Test
    public void testDatePreviousWorkDay() {
        Date mon = toDate(LocalDate.of(2025, 10, 6));
        Date result = WorkdayUtils.previousWorkDay(mon);
        assertEquals(LocalDate.of(2025, 10, 3), toLocalDate(result));
    }

    @Test
    public void testDateWorkDaysBetween() {
        Date mon = toDate(LocalDate.of(2025, 10, 6));
        Date fri = toDate(LocalDate.of(2025, 10, 10));
        assertEquals(5, WorkdayUtils.workDaysBetween(mon, fri));
    }

    @Test
    public void testDateConsistentWithLocalDate() {
        // Verify Date and LocalDate versions produce the same result
        LocalDate ldStart = LocalDate.of(2025, 10, 15);
        Date dateStart = toDate(ldStart);

        assertEquals(
                WorkdayUtils.addWorkDays(ldStart, 5),
                toLocalDate(WorkdayUtils.addWorkDays(dateStart, 5)));

        assertEquals(
                WorkdayUtils.subtractWorkDays(ldStart, 3),
                toLocalDate(WorkdayUtils.subtractWorkDays(dateStart, 3)));

        assertEquals(
                WorkdayUtils.nextWorkDay(ldStart),
                toLocalDate(WorkdayUtils.nextWorkDay(dateStart)));

        assertEquals(
                WorkdayUtils.previousWorkDay(ldStart),
                toLocalDate(WorkdayUtils.previousWorkDay(dateStart)));

        LocalDate ldEnd = LocalDate.of(2025, 10, 31);
        Date dateEnd = toDate(ldEnd);
        assertEquals(
                WorkdayUtils.workDaysBetween(ldStart, ldEnd),
                WorkdayUtils.workDaysBetween(dateStart, dateEnd));
    }
}
