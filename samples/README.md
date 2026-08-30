# Samples

本目录包含 5 个独立的小型 Maven 工程：`00` 演示 WorkdayUtils 默认 API 全景，
`01`~`04` 分别演示 workday-cn-java 的 4 种常见配置模式。
每个 sample 都是一个完整可运行的 Java 程序。

| Sample | 主题 | 演示点 |
|--------|----------|--------|
| [00-default](00-default/) | 默认 API 全景 | 零配置使用 `WorkdayUtils`：基础推算 + 月度计算（当月工作日数、第 n 个工作日、首/末工作日、工作日枚举），可选 FESTIVAL 策略 |
| [01-zero-config](01-zero-config/) | 零配置 | `new HolidayFetcher()`，全部使用内置默认值 |
| [02-classpath-config](02-classpath-config/) | classpath 覆盖 | `src/main/resources/holiday-config.properties` 自动生效（切换 FESTIVAL 策略） |
| [03-external-config](03-external-config/) | 外部文件 | `HolidayConfig.load("config/workday.properties")`，不重新打包即可调整 |
| [04-programmatic](04-programmatic/) | 纯代码配置 | `setStrategy` / `setDataDir` / `addSourceUrl`，以及 `{year}-ext.json` 企业补丁叠加 |

配置加载优先级（后者覆盖前者）：

```
内置 holiday-default.properties → classpath holiday-config.properties → 外部文件 → 编程式 setter
```

## 前置条件

先在仓库根目录把库安装到本地 Maven 仓库（库发布到中央仓库后此步可省略）：

```bash
# 仓库根目录
mvn install -DskipTests
```

## 运行

进入任一 sample 目录执行：

```bash
cd samples/01-zero-config
mvn -q compile exec:java
```

## 预期输出（要点）

- **00**：`WorkdayUtils` 全静态调用——周五加 5 个工作日、2025-10 共 23 个工作日、2025-11 首/末工作日自动跳过周末、第 4 个工作日、FESTIVAL 策略下 2026-02 扣除春节后 17 个工作日
- **01**：今天是否工作日、第 3 个工作日、2026 国庆的节假日信息、2026 年日历条目统计（休息/调休补班）
- **02**：策略显示 `FestivalStrategy`；2031-01-23（春节）返回 `春节, wage=3`
- **03**：定时刷新为 `false`、数据源数量为 `0`（外部文件已清空远程源）
- **04**：2026-12-31 被 `2026-ext.json` 标记为"公司周年纪念日"，`isWorkDay` 返回 `false`

> Windows 命令行若中文显示乱码，先执行 `chcp 65001` 切换 UTF-8 代码页。

## 说明

- 各 sample 的数据缓存写入各自的 `./holiday-data` 或 `./sample-data`（已加入 `.gitignore`），互不污染。
- 首次运行 `refresh()` 时，内置数据未覆盖的年份会尝试远程源；网络不可达时仅打印告警，不影响内置年份的使用。
- 库只依赖 `slf4j-api`，未绑定日志实现时控制台仅有一条 SLF4J 提示，属正常现象；如需日志输出可自行引入 `logback-classic` 等。
