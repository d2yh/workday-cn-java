package com.github.d2yh.samples;

import com.github.d2yh.workday.HolidayFetcher;
import com.github.d2yh.workday.config.HolidayConfig;

import java.time.LocalDate;

/**
 * Sample 03：外部配置文件。
 * <p>
 * 通过 {@code HolidayConfig.load(path)} 加载 jar 之外的配置文件，
 * 适合运维场景：不重新打包即可调整行为。
 * <p>
 * 加载优先级：内置默认 → classpath holiday-config.properties → 外部文件（最高）。
 */
public class ExternalConfigApp {

    public static void main(String[] args) {
        HolidayConfig config = HolidayConfig.load("config/workday.properties");
        HolidayFetcher fetcher = new HolidayFetcher(config);
        fetcher.refresh();

        System.out.println("生效的数据目录: " + config.getEffectiveDataDir());
        System.out.println("定时刷新是否启用: " + fetcher.isRefreshEnabled());
        System.out.println("数据源 URL 数量: " + fetcher.getSourceUrls().size());

        LocalDate today = LocalDate.now();
        System.out.println("今天 " + today + " 是工作日: " + fetcher.isWorkDay(today));
    }
}
