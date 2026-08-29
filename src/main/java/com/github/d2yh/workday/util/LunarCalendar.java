package com.github.d2yh.workday.util;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 农历（阴历）工具类，采用经典查表法实现。
 * <p>
 * 覆盖公历 2000-2050 年对应的农历数据。
 * 每个年份编码为一个 int 值，包含：闰月月份、各月大小月信息。
 * <p>
 * 编码规则：
 * <ul>
 *   <li>低 4 位 (0-3)：闰月月份，0 表示无闰月</li>
 *   <li>中间 12 位 (4-15)：1-12 月各月天数，1=大月(30天)，0=小月(29天)</li>
 *   <li>第 16 位：闰月天数，1=大月(30天)，0=小月(29天)</li>
 * </ul>
 */
public class LunarCalendar {

    /**
     * 农历数据表 (2000-2050)
     * 每年一个 int，编码该年农历信息。
     * 数据来源：经典农历万年历编码表。
     */
    @SuppressWarnings("unused")
    private static final int[] LUNAR_INFO = {
            /* 2000 */ 0x04bd8, /* 2001 */ 0x04ae0, /* 2002 */ 0x0a570,
            /* 2003 */ 0x054d5, /* 2004 */ 0x0d260, /* 2005 */ 0x0d950,
            /* 2006 */ 0x16554, /* 2007 */ 0x056a0, /* 2008 */ 0x09ad0,
            /* 2009 */ 0x055d2, /* 2010 */ 0x04ae0, /* 2011 */ 0x0a5b6,
            /* 2012 */ 0x0a4d0, /* 2013 */ 0x0d250, /* 2014 */ 0x1d255,
            /* 2015 */ 0x0b540, /* 2016 */ 0x0d6a0, /* 2017 */ 0x0ada2,
            /* 2018 */ 0x095b0, /* 2019 */ 0x14977, /* 2020 */ 0x04970,
            /* 2021 */ 0x0a4b0, /* 2022 */ 0x0b4b5, /* 2023 */ 0x06a50,
            /* 2024 */ 0x06d40, /* 2025 */ 0x1ab54, /* 2026 */ 0x02b60,
            /* 2027 */ 0x09570, /* 2028 */ 0x052f2, /* 2029 */ 0x04970,
            /* 2030 */ 0x06566, /* 2031 */ 0x0d4a0, /* 2032 */ 0x0ea50,
            /* 2033 */ 0x06e95, /* 2034 */ 0x05ad0, /* 2035 */ 0x02b60,
            /* 2036 */ 0x186e3, /* 2037 */ 0x092e0, /* 2038 */ 0x1c8d7,
            /* 2039 */ 0x0c950, /* 2040 */ 0x0d4a0, /* 2041 */ 0x1d8a6,
            /* 2042 */ 0x0b550, /* 2043 */ 0x056a0, /* 2044 */ 0x1a5b4,
            /* 2045 */ 0x025d0, /* 2046 */ 0x092d0, /* 2047 */ 0x0d2b2,
            /* 2048 */ 0x0a950, /* 2049 */ 0x0b557, /* 2050 */ 0x06ca0
    };

    /** 数据起始年份 */
    private static final int BASE_YEAR = 2000;

    /**
     * 已知每年春节（正月初一）的公历日期。
     * 以 {year, month, day} 形式存储，避免复杂计算。
     * 覆盖 2000-2050。
     */
    private static final int[][] SPRING_FESTIVAL_DATES = {
            /* 2000 */ {2, 5},  /* 2001 */ {1, 24}, /* 2002 */ {2, 12},
            /* 2003 */ {2, 1},  /* 2004 */ {1, 22}, /* 2005 */ {2, 9},
            /* 2006 */ {1, 29}, /* 2007 */ {2, 18}, /* 2008 */ {2, 7},
            /* 2009 */ {1, 26}, /* 2010 */ {2, 14}, /* 2011 */ {2, 3},
            /* 2012 */ {1, 23}, /* 2013 */ {2, 10}, /* 2014 */ {1, 31},
            /* 2015 */ {2, 19}, /* 2016 */ {2, 8},  /* 2017 */ {1, 28},
            /* 2018 */ {2, 16}, /* 2019 */ {2, 5},  /* 2020 */ {1, 25},
            /* 2021 */ {2, 12}, /* 2022 */ {2, 1},  /* 2023 */ {1, 22},
            /* 2024 */ {2, 10}, /* 2025 */ {1, 29}, /* 2026 */ {2, 17},
            /* 2027 */ {2, 6},  /* 2028 */ {1, 26}, /* 2029 */ {2, 13},
            /* 2030 */ {2, 3},  /* 2031 */ {1, 23}, /* 2032 */ {2, 11},
            /* 2033 */ {1, 31}, /* 2034 */ {2, 19}, /* 2035 */ {2, 8},
            /* 2036 */ {1, 28}, /* 2037 */ {2, 15}, /* 2038 */ {2, 4},
            /* 2039 */ {1, 24}, /* 2040 */ {2, 12}, /* 2041 */ {2, 1},
            /* 2042 */ {1, 22}, /* 2043 */ {2, 10}, /* 2044 */ {1, 30},
            /* 2045 */ {2, 17}, /* 2046 */ {2, 6},  /* 2047 */ {1, 26},
            /* 2048 */ {2, 14}, /* 2049 */ {2, 2},  /* 2050 */ {1, 23}
    };

    private LunarCalendar() {
    }

    /**
     * 获取指定年份春节（正月初一）的公历日期。
     *
     * @param year 公历年份 (2000-2050)
     * @return 春节的公历日期
     * @throws IllegalArgumentException 若年份不在支持范围内
     */
    public static LocalDate getSpringFestivalDate(int year) {
        int index = year - BASE_YEAR;
        if (index < 0 || index >= SPRING_FESTIVAL_DATES.length) {
            throw new IllegalArgumentException(
                    "Year " + year + " is out of supported range [2000-2050]");
        }
        int month = SPRING_FESTIVAL_DATES[index][0];
        int day = SPRING_FESTIVAL_DATES[index][1];
        return LocalDate.of(year, month, day);
    }

    /**
     * 获取指定年份正月初一、初二、初三的公历日期。
     *
     * @param year 公历年份 (2000-2050)
     * @return 包含初一、初二、初三公历日期的列表
     */
    public static List<LocalDate> getLunarNewYearDates(int year) {
        LocalDate firstDay = getSpringFestivalDate(year);
        List<LocalDate> dates = new ArrayList<>(3);
        dates.add(firstDay);          // 初一
        dates.add(firstDay.plusDays(1)); // 初二
        dates.add(firstDay.plusDays(2)); // 初三
        return dates;
    }

    /**
     * 判断指定年份是否在支持范围内。
     */
    public static boolean isSupported(int year) {
        int index = year - BASE_YEAR;
        return index >= 0 && index < SPRING_FESTIVAL_DATES.length;
    }
}
