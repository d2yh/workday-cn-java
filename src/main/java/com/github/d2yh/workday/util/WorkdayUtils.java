package com.github.d2yh.workday.util;

import com.github.d2yh.workday.model.HolidayInfo;
import com.github.d2yh.workday.strategy.OffDayStrategy;
import com.github.d2yh.workday.strategy.WeekendOnlyStrategy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工作日计算静态工具类。
 * <p>
 * 提供与 {@link OffDayStrategy} 配合使用的工作日计算方法，
 * 无需依赖 HolidayFetcher 实例即可进行工作日推算。
 */
public final class WorkdayUtils {

    private static final OffDayStrategy DEFAULT_STRATEGY = new WeekendOnlyStrategy();

    private WorkdayUtils() {
        // 工具类不允许实例化
    }

    // ──────────────── Weekend Check ────────────────

    /**
     * 判断指定日期是否为周末（周六或周日）。
     */
    public static boolean isWeekend(LocalDate date) {
        return HolidayInfo.isWeekendDay(date);
    }

    /**
     * 判断指定日期是否为工作日（周一至周五）。
     * 仅基于日历判断，不考虑法定假日。
     */
    public static boolean isWeekday(LocalDate date) {
        return !isWeekend(date);
    }

    // ──────────────── WorkDay Check (Strategy-aware) ────────────────

    /**
     * 判断指定日期是否为工作日（使用默认 WEEKEND_ONLY 策略）。
     */
    public static boolean isWorkDay(LocalDate date) {
        return isWorkDay(date, DEFAULT_STRATEGY);
    }

    /**
     * 判断指定日期是否为工作日。
     *
     * @param date     日期
     * @param strategy 休息日策略
     * @return true 表示工作日
     */
    public static boolean isWorkDay(LocalDate date, OffDayStrategy strategy) {
        return !strategy.isOffDay(date);
    }

    // ──────────────── Add / Subtract WorkDays ────────────────

    /**
     * 计算从 startDate 起经过 n 个工作日后的日期（默认策略）。
     *
     * @param startDate 起始日期
     * @param n         工作日数（非负）
     * @return n 个工作日后的日期
     */
    public static LocalDate addWorkDays(LocalDate startDate, int n) {
        return addWorkDays(startDate, n, DEFAULT_STRATEGY);
    }

    /**
     * 计算从 startDate 起经过 n 个工作日后的日期。
     * <p>
     * n=0 时：若 startDate 为工作日返回本身，否则推进到下一个工作日。
     *
     * @param startDate 起始日期
     * @param n         工作日数（非负）
     * @param strategy  休息日策略
     * @return n 个工作日后的日期
     * @throws IllegalArgumentException 若 n 为负数
     */
    public static LocalDate addWorkDays(LocalDate startDate, int n, OffDayStrategy strategy) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be non-negative, got: " + n);
        }

        LocalDate current = startDate;
        while (strategy.isOffDay(current)) {
            current = current.plusDays(1);
        }

        int count = 0;
        while (count < n) {
            current = current.plusDays(1);
            if (!strategy.isOffDay(current)) {
                count++;
            }
        }

        return current;
    }

    /**
     * 计算从 startDate 往前推 n 个工作日的日期（默认策略）。
     *
     * @param startDate 起始日期
     * @param n         工作日数（非负）
     * @return n 个工作日前的日期
     */
    public static LocalDate subtractWorkDays(LocalDate startDate, int n) {
        return subtractWorkDays(startDate, n, DEFAULT_STRATEGY);
    }

    /**
     * 计算从 startDate 往前推 n 个工作日的日期。
     * <p>
     * n=0 时：若 startDate 为工作日返回本身，否则回退到上一个工作日。
     *
     * @param startDate 起始日期
     * @param n         工作日数（非负）
     * @param strategy  休息日策略
     * @return n 个工作日前的日期
     * @throws IllegalArgumentException 若 n 为负数
     */
    public static LocalDate subtractWorkDays(LocalDate startDate, int n, OffDayStrategy strategy) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be non-negative, got: " + n);
        }

        LocalDate current = startDate;
        while (strategy.isOffDay(current)) {
            current = current.minusDays(1);
        }

        int count = 0;
        while (count < n) {
            current = current.minusDays(1);
            if (!strategy.isOffDay(current)) {
                count++;
            }
        }

        return current;
    }

    // ──────────────── Next / Previous WorkDay ────────────────

    /**
     * 获取指定日期之后的下一个工作日（默认策略）。
     * 若指定日期本身是工作日，则返回下一个工作日。
     */
    public static LocalDate nextWorkDay(LocalDate date) {
        return nextWorkDay(date, DEFAULT_STRATEGY);
    }

    /**
     * 获取指定日期之后的下一个工作日。
     *
     * @param date     日期
     * @param strategy 休息日策略
     * @return 下一个工作日
     */
    public static LocalDate nextWorkDay(LocalDate date, OffDayStrategy strategy) {
        LocalDate next = date.plusDays(1);
        while (strategy.isOffDay(next)) {
            next = next.plusDays(1);
        }
        return next;
    }

    /**
     * 获取指定日期之前的上一个工作日（默认策略）。
     * 若指定日期本身是工作日，则返回上一个工作日。
     */
    public static LocalDate previousWorkDay(LocalDate date) {
        return previousWorkDay(date, DEFAULT_STRATEGY);
    }

    /**
     * 获取指定日期之前的上一个工作日。
     *
     * @param date     日期
     * @param strategy 休息日策略
     * @return 上一个工作日
     */
    public static LocalDate previousWorkDay(LocalDate date, OffDayStrategy strategy) {
        LocalDate prev = date.minusDays(1);
        while (strategy.isOffDay(prev)) {
            prev = prev.minusDays(1);
        }
        return prev;
    }

    // ──────────────── Count / Range ────────────────

    /**
     * 计算两个日期之间（含两端）的工作日数量（默认策略）。
     *
     * @param from 起始日期
     * @param to   结束日期
     * @return 工作日数量
     * @throws IllegalArgumentException 若 from 在 to 之后
     */
    public static int workDaysBetween(LocalDate from, LocalDate to) {
        return workDaysBetween(from, to, DEFAULT_STRATEGY);
    }

    /**
     * 计算两个日期之间（含两端）的工作日数量。
     *
     * @param from     起始日期
     * @param to       结束日期
     * @param strategy 休息日策略
     * @return 工作日数量
     * @throws IllegalArgumentException 若 from 在 to 之后
     */
    public static int workDaysBetween(LocalDate from, LocalDate to, OffDayStrategy strategy) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to: " + from + " > " + to);
        }

        int count = 0;
        LocalDate current = from;
        while (!current.isAfter(to)) {
            if (!strategy.isOffDay(current)) {
                count++;
            }
            current = current.plusDays(1);
        }
        return count;
    }

    /**
     * 获取指定年份所有休息日的日期集合（默认策略）。
     *
     * @param year 年份
     * @return 休息日日期集合（String 格式 YYYY-MM-DD）
     */
    public static Set<String> getOffDaySet(int year) {
        return getOffDaySet(year, DEFAULT_STRATEGY);
    }

    /**
     * 获取指定年份所有休息日的日期集合。
     *
     * @param year     年份
     * @param strategy 休息日策略
     * @return 休息日日期集合（String 格式 YYYY-MM-DD）
     */
    public static Set<String> getOffDaySet(int year, OffDayStrategy strategy) {
        return strategy.generateOffDays(year).stream()
                .map(HolidayInfo::getDate)
                .collect(Collectors.toSet());
    }

    // ──────────────── Date ↔ LocalDate Conversion ────────────────

    private static LocalDate toLocalDate(Date date) {
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static Date toDate(LocalDate localDate) {
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    // ──────────────── java.util.Date Overloads ────────────────

    /**
     * 判断指定日期是否为周末（java.util.Date 版本）。
     */
    public static boolean isWeekend(Date date) {
        return isWeekend(toLocalDate(date));
    }

    /**
     * 判断指定日期是否为工作日（java.util.Date 版本）。
     */
    public static boolean isWeekday(Date date) {
        return isWeekday(toLocalDate(date));
    }

    /**
     * 判断指定日期是否为工作日（java.util.Date 版本，默认策略）。
     */
    public static boolean isWorkDay(Date date) {
        return isWorkDay(toLocalDate(date));
    }

    /**
     * 判断指定日期是否为工作日（java.util.Date 版本）。
     */
    public static boolean isWorkDay(Date date, OffDayStrategy strategy) {
        return isWorkDay(toLocalDate(date), strategy);
    }

    /**
     * 计算从 startDate 起经过 n 个工作日后的日期（java.util.Date 版本，默认策略）。
     */
    public static Date addWorkDays(Date startDate, int n) {
        return toDate(addWorkDays(toLocalDate(startDate), n));
    }

    /**
     * 计算从 startDate 起经过 n 个工作日后的日期（java.util.Date 版本）。
     */
    public static Date addWorkDays(Date startDate, int n, OffDayStrategy strategy) {
        return toDate(addWorkDays(toLocalDate(startDate), n, strategy));
    }

    /**
     * 计算从 startDate 往前推 n 个工作日的日期（java.util.Date 版本，默认策略）。
     */
    public static Date subtractWorkDays(Date startDate, int n) {
        return toDate(subtractWorkDays(toLocalDate(startDate), n));
    }

    /**
     * 计算从 startDate 往前推 n 个工作日的日期（java.util.Date 版本）。
     */
    public static Date subtractWorkDays(Date startDate, int n, OffDayStrategy strategy) {
        return toDate(subtractWorkDays(toLocalDate(startDate), n, strategy));
    }

    /**
     * 获取指定日期之后的下一个工作日（java.util.Date 版本，默认策略）。
     */
    public static Date nextWorkDay(Date date) {
        return toDate(nextWorkDay(toLocalDate(date)));
    }

    /**
     * 获取指定日期之后的下一个工作日（java.util.Date 版本）。
     */
    public static Date nextWorkDay(Date date, OffDayStrategy strategy) {
        return toDate(nextWorkDay(toLocalDate(date), strategy));
    }

    /**
     * 获取指定日期之前的上一个工作日（java.util.Date 版本，默认策略）。
     */
    public static Date previousWorkDay(Date date) {
        return toDate(previousWorkDay(toLocalDate(date)));
    }

    /**
     * 获取指定日期之前的上一个工作日（java.util.Date 版本）。
     */
    public static Date previousWorkDay(Date date, OffDayStrategy strategy) {
        return toDate(previousWorkDay(toLocalDate(date), strategy));
    }

    /**
     * 计算两个日期之间的工作日数量（java.util.Date 版本，默认策略）。
     */
    public static int workDaysBetween(Date from, Date to) {
        return workDaysBetween(toLocalDate(from), toLocalDate(to));
    }

    /**
     * 计算两个日期之间的工作日数量（java.util.Date 版本）。
     */
    public static int workDaysBetween(Date from, Date to, OffDayStrategy strategy) {
        return workDaysBetween(toLocalDate(from), toLocalDate(to), strategy);
    }
}
