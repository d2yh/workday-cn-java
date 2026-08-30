package com.github.d2yh.samples;

import com.github.d2yh.workday.strategy.FestivalStrategy;
import com.github.d2yh.workday.util.WorkdayUtils;

import java.time.LocalDate;

/**
 * Sample 00：默认 API 全景（无需任何配置）。
 * <p>
 * 演示 {@link WorkdayUtils} 静态工具类——不依赖 HolidayFetcher、不加载数据文件，
 * 默认使用 WEEKEND_ONLY 策略，也可显式传入 FESTIVAL 等其他策略。
 * 覆盖基础推算与月度计算（当月工作日数、第 n 个工作日、首/末工作日、工作日枚举）。
 */
public class DefaultApiApp {

    public static void main(String[] args) {
        // ──────────── 基础推算 ────────────
        System.out.println("── 基础推算（默认 WEEKEND_ONLY 策略）──");
        LocalDate friday = LocalDate.of(2025, 10, 17); // 周五
        System.out.println(friday + " 是工作日: " + WorkdayUtils.isWorkDay(friday));
        System.out.println("下一个工作日: " + WorkdayUtils.nextWorkDay(friday));
        System.out.println("上一个工作日: " + WorkdayUtils.previousWorkDay(friday));
        System.out.println("加 5 个工作日: " + WorkdayUtils.addWorkDays(friday, 5));
        System.out.println("减 3 个工作日: " + WorkdayUtils.subtractWorkDays(friday, 3));
        System.out.println("10-06 ~ 10-31 工作日数: "
                + WorkdayUtils.workDaysBetween(LocalDate.of(2025, 10, 6), LocalDate.of(2025, 10, 31)));

        // ──────────── 月度计算 ────────────
        System.out.println();
        System.out.println("── 月度计算 ──");
        System.out.println("2025-10 工作日总数: " + WorkdayUtils.workDaysInMonth(2025, 10));
        System.out.println("2025-11 第一个工作日: " + WorkdayUtils.firstWorkDayOfMonth(2025, 11)
                + "（11-01 是周六，自动跳过）");
        System.out.println("2025-11 最后一个工作日: " + WorkdayUtils.lastWorkDayOfMonth(2025, 11)
                + "（11-30 是周日，自动回退）");
        System.out.println("2025-10 第 4 个工作日: " + WorkdayUtils.nthWorkDayOfMonth(2025, 10, 4)
                + "（\"每月第 n 个工作日\"结算日场景）");
        System.out.println("2025-10-17 ~ 10-21 的所有工作日: "
                + WorkdayUtils.getWorkDays(LocalDate.of(2025, 10, 17), LocalDate.of(2025, 10, 21)));

        // ──────────── 切换 FESTIVAL 策略 ────────────
        System.out.println();
        System.out.println("── FESTIVAL 策略（周末 + 春节 + 国庆）──");
        FestivalStrategy festival = new FestivalStrategy();
        System.out.println("2026-02 工作日总数: " + WorkdayUtils.workDaysInMonth(2026, 2, festival)
                + "（春节 02-17~19 被扣除）");
        System.out.println("2026-02 第 12 个工作日: "
                + WorkdayUtils.nthWorkDayOfMonth(2026, 2, 12, festival) + "（自动跳过春节）");
        System.out.println("2026-02-16 ~ 02-20 的所有工作日: "
                + WorkdayUtils.getWorkDays(LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 20), festival));
    }
}
