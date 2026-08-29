package com.github.d2yh.samples;

import com.github.d2yh.workday.HolidayFetcher;
import com.github.d2yh.workday.model.HolidayInfo;

import java.time.LocalDate;
import java.util.List;

/**
 * Sample 01：零配置使用。
 * <p>
 * 直接 {@code new HolidayFetcher()}，全部使用库内置默认配置：
 * 内置日历数据 + 磁盘缓存目录 ./holiday-data + WEEKEND_ONLY 回退策略。
 */
public class ZeroConfigApp {

    public static void main(String[] args) {
        HolidayFetcher fetcher = new HolidayFetcher();
        // 首次运行从 classpath 内置数据加载并写入磁盘缓存，之后直接读缓存
        fetcher.refresh();

        LocalDate today = LocalDate.now();
        System.out.println("今天 " + today + " 是工作日: " + fetcher.isWorkDay(today));
        System.out.println("今天起第 3 个工作日: " + fetcher.addWorkDays(today, 3));

        LocalDate nationalDay = LocalDate.of(2026, 10, 1);
        HolidayInfo info = fetcher.getHoliday(nationalDay);
        System.out.println(nationalDay + " 的节假日信息: " + info);

        System.out.println("2026 年有数据文件: " + fetcher.hasDataForYear(2026));
        List<HolidayInfo> days2026 = fetcher.getHolidaysByYear(2026);
        long offCount = days2026.stream().filter(HolidayInfo::isOffDay).count();
        System.out.println("2026 年日历条目: " + days2026.size()
                + "（休息 " + offCount + " 天，调休补班 " + (days2026.size() - offCount) + " 天）");
    }
}
