package com.github.d2yh.workday.strategy;

import com.github.d2yh.workday.model.HolidayInfo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 策略A：仅周末为休息日。
 * <p>
 * 当某年份无节假日数据文件时，将周六、周日视为休息日。
 */
public class WeekendOnlyStrategy implements OffDayStrategy {

    @Override
    public boolean isOffDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    @Override
    public HolidayInfo getOffDayInfo(LocalDate date) {
        if (!isOffDay(date)) {
            return null;
        }
        return new HolidayInfo(date.toString(), "周末", true, true, 1);
    }

    @Override
    public List<HolidayInfo> generateOffDays(int year) {
        List<HolidayInfo> result = new ArrayList<>();
        LocalDate date = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);

        while (!date.isAfter(end)) {
            if (isOffDay(date)) {
                result.add(new HolidayInfo(
                        date.toString(),
                        "周末",
                        true,   // isOffDay
                        true,   // isWeekend
                        1       // wage (普通周末)
                ));
            }
            date = date.plusDays(1);
        }

        return result;
    }
}
