package com.github.d2yh.workday.strategy;

import com.github.d2yh.workday.model.HolidayInfo;
import com.github.d2yh.workday.util.LunarCalendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 策略B：周末 + 春节（初一~初三） + 国庆（10.1~10.3），遇周末顺延。
 * <p>
 * 规则：
 * <ul>
 *   <li>周六、周日为休息日（wage=1）</li>
 *   <li>春节正月初一、初二、初三为法定假日（wage=3）</li>
 *   <li>10月1日、2日、3日为法定假日（wage=3）</li>
 *   <li>法定假日若落在周六或周日，顺延至下周一/二（补休日 wage=1）</li>
 * </ul>
 */
public class FestivalStrategy implements OffDayStrategy {

    /** 缓存：年份 → 该年所有休息日的日期集合 */
    private final Map<Integer, Set<String>> offDayCache = new ConcurrentHashMap<>();

    /** 缓存：年份 → 该年休息日详情（日期 → 条目） */
    private final Map<Integer, Map<String, HolidayInfo>> offDayInfoCache = new ConcurrentHashMap<>();

    @Override
    public boolean isOffDay(LocalDate date) {
        int year = date.getYear();
        Set<String> offDays = offDayCache.get(year);
        if (offDays == null) {
            offDays = buildOffDaySet(year);
            offDayCache.put(year, offDays);
        }
        return offDays.contains(date.toString());
    }

    @Override
    public HolidayInfo getOffDayInfo(LocalDate date) {
        int year = date.getYear();
        Map<String, HolidayInfo> infoMap = offDayInfoCache.get(year);
        if (infoMap == null) {
            generateOffDays(year);
            infoMap = offDayInfoCache.get(year);
        }
        return infoMap.get(date.toString());
    }

    @Override
    public List<HolidayInfo> generateOffDays(int year) {
        Map<String, HolidayInfo> infoMap = new LinkedHashMap<>();

        // 1) 所有周末
        addWeekends(year, infoMap);

        // 2) 春节（初一~初三）
        if (LunarCalendar.isSupported(year)) {
            addSpringFestival(year, infoMap);
        }

        // 3) 国庆（10.1~10.3）
        addNationalDay(year, infoMap);

        List<HolidayInfo> result = new ArrayList<>(infoMap.values());
        result.sort(Comparator.comparing(HolidayInfo::getDate));

        // 更新缓存
        Set<String> dateSet = new HashSet<>();
        for (HolidayInfo info : result) {
            dateSet.add(info.getDate());
        }
        offDayCache.put(year, dateSet);
        offDayInfoCache.put(year, infoMap);

        return result;
    }

    // ──────────────── Private Helpers ────────────────

    private Set<String> buildOffDaySet(int year) {
        Set<String> set = new HashSet<>();
        for (HolidayInfo info : generateOffDays(year)) {
            set.add(info.getDate());
        }
        return set;
    }

    private void addWeekends(int year, Map<String, HolidayInfo> infoMap) {
        LocalDate date = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        while (!date.isAfter(end)) {
            DayOfWeek dow = date.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                String key = date.toString();
                if (!infoMap.containsKey(key)) {
                    infoMap.put(key, new HolidayInfo(
                            key, "周末", true, true, 1
                    ));
                }
            }
            date = date.plusDays(1);
        }
    }

    /**
     * 添加春节假日（初一~初三），遇周末顺延。
     */
    private void addSpringFestival(int year, Map<String, HolidayInfo> infoMap) {
        List<LocalDate> lunarDates = LunarCalendar.getLunarNewYearDates(year);
        String[] names = {"春节", "春节", "春节"};

        for (int i = 0; i < lunarDates.size(); i++) {
            LocalDate date = lunarDates.get(i);
            DayOfWeek dow = date.getDayOfWeek();

            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                // 顺延：找下一个不在 infoMap 中的工作日
                LocalDate deferred = deferToNextWorkDay(date, infoMap);
                String key = deferred.toString();
                infoMap.put(key, new HolidayInfo(
                        key, "春节补休", true, false, 1
                ));
            } else {
                String key = date.toString();
                // 如果该日期已被标记为假日（如恰好与国庆重合），保留 wage=3
                infoMap.put(key, new HolidayInfo(
                        key, names[i], true, false, 3
                ));
            }
        }
    }

    /**
     * 添加国庆假日（10.1~10.3），遇周末顺延。
     */
    private void addNationalDay(int year, Map<String, HolidayInfo> infoMap) {
        LocalDate[] nationalDays = {
                LocalDate.of(year, 10, 1),
                LocalDate.of(year, 10, 2),
                LocalDate.of(year, 10, 3)
        };

        for (LocalDate date : nationalDays) {
            DayOfWeek dow = date.getDayOfWeek();

            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                // 顺延：找下一个不在 infoMap 中的工作日
                LocalDate deferred = deferToNextWorkDay(date, infoMap);
                String key = deferred.toString();
                infoMap.put(key, new HolidayInfo(
                        key, "国庆补休", true, false, 1
                ));
            } else {
                String key = date.toString();
                infoMap.put(key, new HolidayInfo(
                        key, "国庆节", true, false, 3
                ));
            }
        }
    }

    /**
     * 从指定日期开始，找到下一个不在 infoMap 中的工作日（用于顺延）。
     */
    private LocalDate deferToNextWorkDay(LocalDate date, Map<String, HolidayInfo> infoMap) {
        LocalDate next = date.plusDays(1);
        while (infoMap.containsKey(next.toString())) {
            next = next.plusDays(1);
        }
        return next;
    }
}
