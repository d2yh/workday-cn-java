package com.github.d2yh.workday.strategy;

import com.github.d2yh.workday.model.HolidayInfo;

import java.time.LocalDate;
import java.util.List;

/**
 * 休息日计算策略接口。
 * <p>
 * 当某年份无节假日数据文件时，使用策略计算该年的休息日。
 */
public interface OffDayStrategy {

    /**
     * 判断指定日期是否为休息日（无数据文件时的回退计算）。
     *
     * @param date 日期
     * @return true 表示休息日
     */
    boolean isOffDay(LocalDate date);

    /**
     * 获取指定日期的休息日详情（无数据文件时的回退计算）。
     * <p>
     * 默认返回通用条目（名称"策略计算"，wage=1）；
     * 能提供更详细信息（节日名称、工资倍数）的策略应覆盖此方法。
     *
     * @param date 日期
     * @return 休息日详情；该日期不是休息日时返回 null
     */
    default HolidayInfo getOffDayInfo(LocalDate date) {
        if (!isOffDay(date)) {
            return null;
        }
        return new HolidayInfo(
                date.toString(), "策略计算", true,
                HolidayInfo.isWeekendDay(date), 1);
    }

    /**
     * 为该年份生成所有休息日的 HolidayInfo 列表。
     *
     * @param year 年份
     * @return 休息日列表
     */
    List<HolidayInfo> generateOffDays(int year);
}
