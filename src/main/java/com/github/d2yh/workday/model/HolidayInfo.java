package com.github.d2yh.workday.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * 节假日信息数据模型，兼容 holiday-cn 的 JSON 格式。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HolidayInfo {

    @JsonProperty("date")
    private String date;

    @JsonProperty("name")
    private String name;

    @JsonProperty("isOffDay")
    private boolean offDay;

    @JsonProperty("isWeekend")
    private boolean weekend;

    @JsonProperty("wage")
    private int wage;

    /** 默认构造函数（Jackson 反序列化需要） */
    public HolidayInfo() {
    }

    public HolidayInfo(String date, String name, boolean offDay, boolean weekend, int wage) {
        this.date = date;
        this.name = name;
        this.offDay = offDay;
        this.weekend = weekend;
        this.wage = wage;
    }

    /**
     * @return 日期，格式为 YYYY-MM-DD
     */
    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    /**
     * @return 节假日名称
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return 是否法定休息日
     */
    public boolean isOffDay() {
        return offDay;
    }

    public void setOffDay(boolean offDay) {
        this.offDay = offDay;
    }

    /**
     * @return 是否周末
     */
    public boolean isWeekend() {
        return weekend;
    }

    public void setWeekend(boolean weekend) {
        this.weekend = weekend;
    }

    /**
     * @return 工资倍数（工作日为 1，法定假日通常为 3）
     */
    public int getWage() {
        return wage;
    }

    public void setWage(int wage) {
        this.wage = wage;
    }

    /**
     * @return 该日期所属年份
     */
    public int getYear() {
        if (date != null && date.length() >= 4) {
            return Integer.parseInt(date.substring(0, 4));
        }
        return -1;
    }

    /**
     * 判断指定日期是否为周末。
     */
    public static boolean isWeekendDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    @Override
    public String toString() {
        return "HolidayInfo{" +
                "date='" + date + '\'' +
                ", name='" + name + '\'' +
                ", isOffDay=" + offDay +
                ", isWeekend=" + weekend +
                ", wage=" + wage +
                '}';
    }
}
