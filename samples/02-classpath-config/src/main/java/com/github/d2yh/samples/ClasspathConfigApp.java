package com.github.d2yh.samples;

import com.github.d2yh.workday.HolidayFetcher;
import com.github.d2yh.workday.model.HolidayInfo;

import java.time.LocalDate;

/**
 * Sample 02：classpath 配置文件覆盖。
 * <p>
 * 只需在本项目 classpath（src/main/resources）放一个 holiday-config.properties，
 * 库会自动用它覆盖内置默认值，代码本身与零配置完全一样。
 * <p>
 * 本例覆盖了两项：
 * <ul>
 *   <li>data-calc.fallback.strategy=FESTIVAL（无数据年份按 周末+春节+国庆 计算）</li>
 *   <li>data-store.dir=./sample-data（缓存目录）</li>
 * </ul>
 */
public class ClasspathConfigApp {

    public static void main(String[] args) {
        HolidayFetcher fetcher = new HolidayFetcher();
        fetcher.refresh();

        System.out.println("当前策略: " + fetcher.getStrategy().getClass().getSimpleName());
        System.out.println("缓存目录: " + fetcher.getCacheDir());

        // 2031 年没有数据文件，由 FESTIVAL 策略推算（2031-01-23 为春节初一，周四）
        LocalDate springFestival = LocalDate.of(2031, 1, 23);
        HolidayInfo info = fetcher.getHoliday(springFestival);
        System.out.println(springFestival + "（2031 春节）: " + info);

        // 对比：远离春节的普通周三
        LocalDate plainWednesday = LocalDate.of(2031, 1, 29);
        System.out.println(plainWednesday + " 是工作日: " + fetcher.isWorkDay(plainWednesday));
    }
}
