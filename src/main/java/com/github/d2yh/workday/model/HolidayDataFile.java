package com.github.d2yh.workday.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * holiday-cn 数据文件的包装对象。
 * <p>
 * 真实数据文件顶层包含 {@code $schema}、{@code $id}、{@code year}、{@code papers}
 * 等非数组字段，不能整体按 {@code Map<String, List<HolidayInfo>>} 反序列化，
 * 此包装类仅提取 {@code days} 字段，忽略其余顶层字段。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HolidayDataFile {

    @JsonProperty("days")
    private List<HolidayInfo> days;

    /**
     * @return days 列表；文件中缺少该字段时返回 null
     */
    public List<HolidayInfo> getDays() {
        return days;
    }

    public void setDays(List<HolidayInfo> days) {
        this.days = days;
    }
}
