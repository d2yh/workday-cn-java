package com.github.d2yh.samples;

import com.github.d2yh.workday.HolidayFetcher;
import com.github.d2yh.workday.config.HolidayConfig;
import com.github.d2yh.workday.model.HolidayInfo;

import java.time.LocalDate;
import java.util.Collections;

/**
 * Sample 04：纯编程式配置 + ext 补丁。
 * <p>
 * 不使用任何 properties 覆盖，全部通过代码配置；
 * 并演示企业级定制：本项目 classpath 中的 calendar-cn/2026-ext.json
 * 会在加载 2026 年数据后自动叠加，可新增或覆盖日期条目。
 */
public class ProgrammaticConfigApp {

    public static void main(String[] args) {
        HolidayConfig config = HolidayConfig.loadDefaults();
        config.setDataDir("./sample-data");
        config.setStrategy("FESTIVAL");

        HolidayFetcher fetcher = new HolidayFetcher(config);
        // 清空默认数据源，追加自定义数据源（演示完全由代码控制）
        config.setSourceUrls(Collections.<String>emptyList());
        fetcher.addSourceUrl(
                "https://raw.githubusercontent.com/d2yh/workday-cn-java/main/src/main/resources/calendar-cn/${yyyy}.json");
        fetcher.refresh();

        System.out.println("当前策略: " + fetcher.getStrategy().getClass().getSimpleName());
        System.out.println("数据源数量: " + fetcher.getSourceUrls().size());

        // 2026-12-31 并非法定节假日，但被本项目自带的 2026-ext.json 标记为公司假日
        LocalDate companyDay = LocalDate.of(2026, 12, 31);
        HolidayInfo patched = fetcher.getHoliday(companyDay);
        System.out.println(companyDay + "（ext 补丁生效）: " + patched);
        System.out.println(companyDay + " 是工作日: " + fetcher.isWorkDay(companyDay));
    }
}
