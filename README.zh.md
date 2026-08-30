# workday-cn-java

中国工作日计算的 Java 库：基于节假日数据（兼容 [holiday-cn](https://github.com/NateScarlet/holiday-cn) 格式）提供工作日判断与工作日推算，支持多级数据加载、本地缓存，以及无数据年份的策略化回退计算。

## 功能特性

- 📅 **多源数据拉取** — 从配置的远程源获取节假日数据，兼容 [holiday-cn](https://github.com/NateScarlet/holiday-cn) JSON 格式
- 💾 **多级加载** — 数据目录 → classpath 内置资源 → 远程 URL，逐级回退
- 🩹 **扩展补丁（ext.json）** — 可选的 `{year}-ext.json` 文件，覆盖或新增日期条目，支持公司级定制
- 🔄 **定期自动更新** — 基于 cron 表达式调度（Quartz 格式；默认每年 11/12 月 1、11、21 日 02:00）
- 🌏 **多区域隔离** — 通过 `data-store.region` 配置支持不同国家/地区（CN、TW、HK、SG 等）
- 🧮 **策略化假日计算** — 无数据年份自动计算：仅周末（WEEKEND_ONLY）或 周末+春节+国庆（FESTIVAL）
- 📆 **工作日推算** — `isWorkDay`、`addWorkDays` 等实例方法 + `WorkdayUtils` 静态工具类
- 📦 **配置驱动** — `.properties` 配置体系，支持默认配置、classpath 覆盖和外部文件覆盖
- ⚡ **内存常驻** — 启动后全量加载到内存，查询零延迟

## 数据格式

兼容 [holiday-cn](https://github.com/NateScarlet/holiday-cn) JSON 格式：

```json
{
  "days": [
    {
      "date": "2025-10-01",
      "name": "国庆节",
      "isOffDay": true,
      "isWeekend": false,
      "wage": 3
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `date` | String | 日期（YYYY-MM-DD） |
| `name` | String | 节假日名称 |
| `isOffDay` | Boolean | 是否法定休息日 |
| `isWeekend` | Boolean | 是否周末 |
| `wage` | Integer | 工资倍数（工作日 1，法定假日 3） |

## 安装

```xml
<dependency>
    <groupId>com.github.d2yh</groupId>
    <artifactId>workday-cn-java</artifactId>
    <version>0.2.0</version>
</dependency>
```

## 快速开始

```java
import com.github.d2yh.workday.HolidayFetcher;
import com.github.d2yh.workday.model.HolidayInfo;
import java.time.LocalDate;

// 使用默认配置初始化
HolidayFetcher fetcher = new HolidayFetcher();

// 拉取并缓存数据
fetcher.refresh();

// 查询某个日期
HolidayInfo holiday = fetcher.getHoliday(LocalDate.of(2025, 10, 1));
if (holiday != null) {
    System.out.println("节假日：" + holiday.getName());
}

// 获取某一年的所有休息日
List<HolidayInfo> holidays = fetcher.getHolidaysByYear(2025);

// 工作日计算
boolean isWorkDay = fetcher.isWorkDay(LocalDate.of(2025, 10, 15));
LocalDate deadline = fetcher.addWorkDays(LocalDate.of(2025, 10, 15), 10);
```

## 详细文档

配置参数、数据加载策略、ext.json 补丁、策略计算、工作日工具类、API 参考等详细内容，请参阅：

**→ [开发指南](DEVELOPMENT.zh.md)**

## 致敬

本项目受到 [holiday-cn](https://github.com/NateScarlet/holiday-cn) 项目的启发，基于其数据格式规范开发。

## 许可证

MIT License - 详见 [LICENSE](LICENSE) 文件。
