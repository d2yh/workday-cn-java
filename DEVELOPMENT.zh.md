# 开发指南

本文档为 `workday-cn-java` 的开发与配置参考。

---

## 1. 快速开始

### 1.1 添加依赖

Maven：

```xml
<dependency>
    <groupId>com.github.d2yh</groupId>
    <artifactId>workday-cn-java</artifactId>
    <version>0.2.0</version>
</dependency>
```

### 1.2 初始化

```java
// 默认配置（自动读取 classpath 上的 holiday-config.properties 覆盖默认值）
HolidayFetcher fetcher = new HolidayFetcher();

// 自定义配置
HolidayConfig config = HolidayConfig.load("/path/to/my-config.properties");
HolidayFetcher fetcher = new HolidayFetcher(config);
```

### 1.3 查询节假日

```java
// 查询指定日期（有数据返回缓存，无数据使用策略计算）
HolidayInfo info = fetcher.getHoliday(LocalDate.of(2025, 10, 1));
// info.getName()    → "国庆节"
// info.isOffDay()   → true
// info.getWage()    → 3

// 查询某年所有休息日
List<HolidayInfo> holidays = fetcher.getHolidaysByYear(2025);

// 判断是否工作日
boolean workDay = fetcher.isWorkDay(LocalDate.of(2025, 10, 15)); // true
```

### 1.4 工作日计算

```java
// 实例方法（基于已加载的数据 + 策略）
LocalDate deadline = fetcher.addWorkDays(LocalDate.of(2025, 10, 15), 10);
```

无需 HolidayFetcher 实例时，使用静态工具类 `WorkdayUtils`：

```java
import com.github.d2yh.workday.util.WorkdayUtils;
import com.github.d2yh.workday.strategy.WeekendOnlyStrategy;

// 简单判断周末 / 工作日
boolean weekend = WorkdayUtils.isWeekend(LocalDate.of(2025, 10, 4)); // true
boolean weekday = WorkdayUtils.isWeekday(LocalDate.of(2025, 10, 6)); // true

// 配合策略计算
OffDayStrategy strategy = new WeekendOnlyStrategy();
boolean workDay = WorkdayUtils.isWorkDay(LocalDate.of(2025, 10, 15), strategy);

// 加减工作日（LocalDate / Date 均支持）
LocalDate future = WorkdayUtils.addWorkDays(LocalDate.of(2025, 10, 15), 5, strategy);
LocalDate past = WorkdayUtils.subtractWorkDays(LocalDate.of(2025, 10, 15), 5, strategy);

// 上 / 下一个工作日
LocalDate next = WorkdayUtils.nextWorkDay(LocalDate.of(2025, 10, 17), strategy);
LocalDate prev = WorkdayUtils.previousWorkDay(LocalDate.of(2025, 10, 6), strategy);

// 两个日期之间的工作日数量
int count = WorkdayUtils.workDaysBetween(
    LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 31), strategy);

// 获取某年所有休息日
Set<String> offDays = WorkdayUtils.getOffDaySet(2025, strategy);
```

`WorkdayUtils` 的所有日期参数方法同时提供 `LocalDate` 和 `java.util.Date` 两种重载，返回值类型与输入一致。

### 1.5 定期更新

```java
// 基于 cron 表达式自动调度（使用配置中的 data-file.update.cron）
fetcher.startPeriodicUpdate();

// 停止自动更新
fetcher.stopPeriodicUpdate();
```

---

## 2. 机制详解

### 2.1 配置项

内置默认配置 `holiday-default.properties`：

```properties
# 区域标识符（简写，内部自动映射为 calendar-{region}）
# 示例：cn（中国大陆）、cn-tw（台湾）、cn-hk（香港）、sg（新加坡）
data-store.region=cn

# 数据文件存储基础目录（实际存储路径为 {dir}/calendar-{region}/）
data-store.dir=./holiday-data

# 无数据年份策略：WEEKEND_ONLY 或 FESTIVAL
data-calc.fallback.strategy=WEEKEND_ONLY

# ── 数据文件下载策略（data-file.update）──

# 是否启用 cron 定时下载（true/false，默认 true）
# 设为 false 时 startPeriodicUpdate() 不会启动调度线程，手动 refresh() 仍可用
data-file.update.enabled=true

# 定期下载调度（Quartz cron 表达式）
# 默认：每年 11、12 月的 1、11、21 日凌晨 2 点下载
data-file.update.cron=0 0 2 1,11,21 11,12 ?

# 远程数据源 URL 模板（逗号分隔，使用 ${yyyy} 作为年份占位符）
# 每个年份依次尝试各 URL，首个成功即停止
data-file.update.source.urls=https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/${yyyy}.json

# 年份边界值，拉取当前年份、所有 ≥ 该值的年份、以及磁盘已缓存的年份
data-file.update.source.years-after=2026

# URL 尝试顺序：sequential（依次尝试，默认） | random（随机顺序）
data-file.update.source.url-order=sequential

# ── 数据文件加载策略（data-file.loader）──

# 补丁文件后缀名（逗号分隔，默认 ext）
# 每个年份会依次加载 {year}-{suffix}.json
data-file.loader.ext.suffixes=ext

# classpath 内置资源前缀（默认从 region 自动派生为 calendar-{region}/）
data-file.loader.classpath.prefix=
```

### 2.2 配置覆盖

在 classpath 上放置 `holiday-config.properties` 文件即可覆盖默认值，或指定外部路径：

```java
HolidayConfig config = HolidayConfig.load("/path/to/my-config.properties");
```

加载优先级：外部文件 > classpath 覆盖 > 内置默认值。

### 2.3 数据加载策略

程序启动后采用多级加载优先级：

1. **数据目录**（`{data.dir}/{calendar.region}/`）中的年份 JSON 文件 — 启动时全量加载到内存
2. **classpath 内置资源**（`{region}/{year}.json`）— 打包在 jar 中的数据
3. **远程 URL 模板** — 对每个配置年份，将 `${yyyy}` 替换为实际年份后依次尝试各 URL，首个成功即停止

每级仅在上一级无数据或数据为空时回退。拉取成功后写入数据目录并更新元数据文件 `holiday-meta.json`。

### 2.4 动态年份与 cron 调度

**年份确定**（`data-file.update.source.years-after`）：

程序根据以下规则自动确定需要拉取的年份：
- 当前年份
- 所有 ≥ `years-after` 的年份（直到当前年份 + 2）
- 磁盘数据目录中已有缓存文件的年份

例如 `years-after=2026`，当前年份为 2026 时，会拉取 2026、2027、2028 年的数据。

**cron 调度**（`data-file.update.cron`）：

使用 Quartz cron 表达式控制刷新时机。默认 `0 0 2 1,11,21 11,12 ?` 表示每年 11 月和 12 月的 1、11、21 日凌晨 2 点执行。可自定义为任意 Quartz cron 表达式。

### 2.5 策略计算

当查询某年份无数据文件时，使用配置的策略计算休息日。

**策略 A：WEEKEND_ONLY（默认）** — 仅将周六、周日视为休息日（wage=1）。

**策略 B：FESTIVAL** — 在策略 A 基础上增加：

- **春节**：正月初一、初二、初三为法定假日（wage=3）
- **国庆**：10 月 1、2、3 日为法定假日（wage=3）
- **顺延规则**：法定假日若落在周六或周日，顺延至下周一/二（补休日 wage=1）

```java
HolidayConfig config = HolidayConfig.loadDefaults();
config.setStrategy("FESTIVAL");
HolidayFetcher fetcher = new HolidayFetcher(config);
```

### 2.6 多区域支持

通过 `data-store.region` 配置项实现多区域数据隔离：

| 配置值 | 说明 | classpath 目录 | 磁盘缓存目录 |
|------------|------|----------------|---------------|
| `cn` | 中国大陆（默认） | `calendar-cn/` | `holiday-data/calendar-cn/` |
| `cn-tw` | 台湾 | `calendar-cn-tw/` | `holiday-data/calendar-cn-tw/` |
| `cn-hk` | 香港 | `calendar-cn-hk/` | `holiday-data/calendar-cn-hk/` |
| `sg` | 新加坡 | `calendar-sg/` | `holiday-data/calendar-sg/` |

每个区域的数据互不干扰。配置示例：

```properties
data-store.region=cn-tw
data-file.update.source.urls=https://example.com/tw/${yyyy}.json
data-file.update.source.years-after=2026
```

同时在 classpath 上提供对应的内置资源目录（如 `resources/calendar-cn-tw/2025.json`）即可。

### 2.7 运行时架构

#### 应用实例角色

| 对象 | 生命周期 | 说明 |
|------|---------|------|
| `HolidayFetcher` | 应用级长生命周期 | 核心实例，持有配置、策略、内存缓存（`ConcurrentHashMap`）和调度器。通常每个应用或每个区域创建一个实例。 |
| `HolidayConfig` | 随 HolidayFetcher | 配置持有者，可变（mutable）。可通过 `getConfig()` 获取后在线修改。 |
| `WorkdayUtils` | 无状态静态工具类 | 全部方法为 `static`，无需实例化，不持有任何状态。 |
| `OffDayStrategy` | 随 HolidayFetcher | 策略实现（`WeekendOnlyStrategy` / `FestivalStrategy`），由 HolidayFetcher 内部持有。 |

典型用法中，`HolidayFetcher` 应作为应用级单例（如 Spring Bean、静态字段）长期持有：

```java
// 应用启动时创建一次，整个生命周期复用
private static final HolidayFetcher fetcher = new HolidayFetcher();
```

#### 线程模型

| 时机 | 线程 | 说明 |
|------|------|------|
| 构造 HolidayFetcher 时 | 无后台线程 | 构造器同步加载磁盘数据到内存，在调用线程中执行 |
| 调用 `startPeriodicUpdate()` | `workday-cn-java-updater` | 单线程 `ScheduledExecutorService`，daemon 线程，基于 Quartz cron 表达式计算下次执行时间并调度 |
| 调用 `stopPeriodicUpdate()` | — | 取消调度任务并关闭线程池 |

调度线程为 **daemon 线程**，不会阻止 JVM 退出。

#### 运行时管理 API

通过以下方法在应用运行期间调整配置或触发操作：

| 方法 | 说明 | 生效方式 |
|------|------|----------|
| `getConfig()` | 获取可变的 HolidayConfig 对象，可直接修改 `setRefreshCron()`、`setRefreshEnabled()`、`setSourceUrlOrder()` 等 | 下次 `refresh()` 时生效 |
| `addSourceUrl(String)` | 运行时追加数据源 URL | 下次 `refresh()` 时生效 |
| `refresh()` | 手动触发数据刷新（重新加载磁盘 / classpath / 远程数据） | 立即执行 |
| `startPeriodicUpdate()` | 启动 cron 定时刷新（若 `refreshEnabled=false` 则跳过） | 使用当前 cron 配置 |
| `stopPeriodicUpdate()` | 停止定时刷新 | 立即停止 |
| `setCacheDir(String)` | 修改磁盘缓存目录 | 下次 `refresh()` 时生效 |

其他查询方法：

| 方法 | 说明 |
|------|------|
| `getRefreshCron()` | 获取当前 cron 表达式 |
| `isRefreshEnabled()` | 获取定时刷新是否启用 |
| `getSourceUrls()` | 获取数据源 URL 列表（不可变视图） |
| `getCacheDir()` | 获取当前缓存目录 |
| `getCacheSize()` | 获取内存缓存条目数 |
| `hasDataForYear(int)` | 检查某年份是否有数据 |

**典型管理流程**：

```java
// 运行时修改 cron 并重启调度
fetcher.getConfig().setRefreshCron("0 0 3 1,11,21 * ?");
fetcher.startPeriodicUpdate(); // 重新启动以应用新 cron

// 添加新数据源并立即刷新
fetcher.addSourceUrl("https://backup.example.com/${yyyy}.json");
fetcher.refresh();

// 修改补丁后缀并刷新
fetcher.getConfig().setExtSuffixes(Arrays.asList("ext", "company", "project"));
fetcher.refresh();
```

---

## 3. 扩展

### 3.1 补丁文件

每个 `{year}.json` 文件支持多个可选的补丁文件，通过 `data-file.loader.ext.suffixes` 配置（逗号分隔）。

默认值 `ext` 对应 `{year}-ext.json`，可配置多个：

```properties
# 依次加载 2025-ext.json 和 2025-company.json，后者覆盖前者
data-file.loader.ext.suffixes=ext,company
```

**用途：**

- 公司级自定义假日（如圣诞放假、公司年会日等）
- 修正源数据中的错误条目
- 添加临时性休息日 / 工作日
- 多层级定制：基础补丁 + 部门/项目级补丁

**格式**与主文件完全一致：

```json
{
  "days": [
    {
      "date": "2025-10-01",
      "name": "公司日",
      "isOffDay": false,
      "isWeekend": false,
      "wage": 1
    },
    {
      "date": "2025-12-25",
      "name": "圣诞节",
      "isOffDay": true,
      "isWeekend": false,
      "wage": 1
    }
  ]
}
```

**补丁规则：**

- **覆盖**：补丁中与主文件相同 `date` 的条目会**替换**原始条目
- **新增**：补丁中新的 `date` 条目会**添加**到数据中
- **多个补丁按配置顺序依次叠加**，后者的覆盖前者的
- 补丁文件**仅用于叠加**，不会被程序自动写入或修改
- 在启动加载和定期刷新时均会检测并叠加

**存放位置**（以 `ext.suffixes=ext,company` 为例）：

| 来源 | 路径示例 |
|------|------|
| 磁盘数据目录 | `{dir}/calendar-{region}/{year}-ext.json`、`{year}-company.json` |
| classpath 内置资源 | `{classpath.prefix}{year}-ext.json`、`{year}-company.json` |

### 3.2 预置数据

将 holiday-cn 的 JSON 文件放入 `src/main/resources/calendar-cn/` 即可随 jar 分发：

```
src/main/resources/calendar-cn/
├── 2025.json
├── 2026.json
└── ...
```

预置数据作为 classpath 资源参与多级加载，在磁盘缓存失效时提供离线回退。

---

## 4. 疑难解决

### 数据未拉取

- 检查网络是否可以访问配置的远程 URL
- 确认 `data-file.update.source.urls` 中的 `${yyyy}` 占位符是否正确
- 查看日志中的 `Failed to fetch` 或 `Failed to parse` 警告信息

### 年份数据缺失

- 确认 `data-file.update.source.years-after` 值是否覆盖了目标年份
- 检查磁盘数据目录（`{dir}/calendar-{region}/`）是否存在对应的 `{year}.json` 文件
- 若数据目录中有 `holiday-meta.json`，检查其 `years` 字段是否包含目标年份

### 定时任务未触发

- 确认已调用 `fetcher.startPeriodicUpdate()`
- 验证 `data-file.update.cron` 是否为合法的 Quartz cron 表达式（注意 Quartz 不允许同时指定 day-of-month 和 day-of-week，需用 `?` 占位一个）
- 确认 `data-file.update.enabled` 为 `true`
- 日志中搜索 `Periodic update started` 确认调度已启动

### 补丁未生效

- 确认补丁文件名与配置匹配：`{year}-{suffix}.json`（如 `2025-ext.json`）
- 确认 `data-file.loader.ext.suffixes` 包含对应的后缀名
- 补丁文件需放置在数据目录或 classpath 资源前缀目录下

### 多区域数据串扰

- 确认不同区域使用不同的 `data-store.region` 值
- 各区域的数据目录和 classpath 目录完全独立，检查是否有路径重叠

---

## 5. API 参考

### HolidayFetcher

| 方法 | 说明 |
|------|------|
| `HolidayFetcher()` | 使用默认配置初始化 |
| `HolidayFetcher(HolidayConfig)` | 使用自定义配置初始化 |
| `void refresh()` | 多级加载刷新数据（含补丁叠加） |
| `HolidayInfo getHoliday(LocalDate)` | 查询指定日期，无数据时使用策略 |
| `List<HolidayInfo> getHolidaysByYear(int)` | 获取指定年份休息日 |
| `boolean isWorkDay(LocalDate)` | 判断是否工作日 |
| `LocalDate addWorkDays(LocalDate, int)` | N 个工作日后的日期 |
| `boolean hasDataForYear(int)` | 该年份是否有数据文件 |
| `boolean isRefreshEnabled()` | 定时刷新是否启用 |
| `void startPeriodicUpdate()` | 启动 cron 定时更新（受 refreshEnabled 控制） |
| `void stopPeriodicUpdate()` | 停止定时更新 |

### HolidayConfig

| 方法 | 说明 |
|------|------|
| `HolidayConfig.loadDefaults()` | 仅加载默认配置 |
| `HolidayConfig.load()` | 默认 + classpath 覆盖 |
| `HolidayConfig.load(String)` | 默认 + 外部文件覆盖 |
| `String getCalendarRegion()` | 获取区域标识符 |
| `String getEffectiveDataDir()` | 获取实际数据目录（含 region 子路径） |

### WorkdayUtils

所有日期参数方法均提供 `LocalDate` 和 `java.util.Date` 两种重载，返回值类型与输入一致。

| 方法 | 说明 |
|------|------|
| `isWeekend(LocalDate)` / `isWeekend(Date)` | 判断是否周末 |
| `isWeekday(LocalDate)` / `isWeekday(Date)` | 判断是否工作日（仅日历） |
| `isWorkDay(LocalDate, OffDayStrategy)` / `isWorkDay(Date, OffDayStrategy)` | 判断是否工作日（策略感知） |
| `addWorkDays(LocalDate, int, OffDayStrategy)` / `addWorkDays(Date, int, OffDayStrategy)` | 加 N 个工作日 |
| `subtractWorkDays(LocalDate, int, OffDayStrategy)` / `subtractWorkDays(Date, int, OffDayStrategy)` | 减 N 个工作日 |
| `nextWorkDay(LocalDate, OffDayStrategy)` / `nextWorkDay(Date, OffDayStrategy)` | 下一个工作日 |
| `previousWorkDay(LocalDate, OffDayStrategy)` / `previousWorkDay(Date, OffDayStrategy)` | 上一个工作日 |
| `workDaysBetween(LocalDate, LocalDate, OffDayStrategy)` / `workDaysBetween(Date, Date, OffDayStrategy)` | 区间工作日数量 |
| `getOffDaySet(int, OffDayStrategy)` | 某年所有休息日集合 |
