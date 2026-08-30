# Development Guide

Development and configuration reference for `workday-cn-java`.

---

## 1. Quick Start

### 1.1 Add Dependency

Maven:

```xml
<dependency>
    <groupId>com.github.d2yh</groupId>
    <artifactId>workday-cn-java</artifactId>
    <version>0.2.0</version>
</dependency>
```

### 1.2 Initialize

```java
// Default config (auto-loads holiday-config.properties from classpath to override defaults)
HolidayFetcher fetcher = new HolidayFetcher();

// Custom config
HolidayConfig config = HolidayConfig.load("/path/to/my-config.properties");
HolidayFetcher fetcher = new HolidayFetcher(config);
```

### 1.3 Query Holidays

```java
// Query a specific date (returns cached data, or strategy-computed result)
HolidayInfo info = fetcher.getHoliday(LocalDate.of(2025, 10, 1));
// info.getName()    → "国庆节"
// info.isOffDay()   → true
// info.getWage()    → 3

// Get all off-days for a year
List<HolidayInfo> holidays = fetcher.getHolidaysByYear(2025);

// Check if a date is a working day
boolean workDay = fetcher.isWorkDay(LocalDate.of(2025, 10, 15)); // true
```

### 1.4 Working Day Calculation

```java
// Instance methods (based on loaded data + strategy)
LocalDate deadline = fetcher.addWorkDays(LocalDate.of(2025, 10, 15), 10);
```

Use the static utility class `WorkdayUtils` when no HolidayFetcher instance is needed:

```java
import com.github.d2yh.workday.util.WorkdayUtils;
import com.github.d2yh.workday.strategy.WeekendOnlyStrategy;

// Simple weekend / weekday check
boolean weekend = WorkdayUtils.isWeekend(LocalDate.of(2025, 10, 4)); // true
boolean weekday = WorkdayUtils.isWeekday(LocalDate.of(2025, 10, 6)); // true

// Strategy-aware calculation
OffDayStrategy strategy = new WeekendOnlyStrategy();
boolean workDay = WorkdayUtils.isWorkDay(LocalDate.of(2025, 10, 15), strategy);

// Add / subtract work days (both LocalDate and Date supported)
LocalDate future = WorkdayUtils.addWorkDays(LocalDate.of(2025, 10, 15), 5, strategy);
LocalDate past = WorkdayUtils.subtractWorkDays(LocalDate.of(2025, 10, 15), 5, strategy);

// Next / previous work day
LocalDate next = WorkdayUtils.nextWorkDay(LocalDate.of(2025, 10, 17), strategy);
LocalDate prev = WorkdayUtils.previousWorkDay(LocalDate.of(2025, 10, 6), strategy);

// Count work days between two dates
int count = WorkdayUtils.workDaysBetween(
    LocalDate.of(2025, 10, 1), LocalDate.of(2025, 10, 31), strategy);

// Get all off-days for a year
Set<String> offDays = WorkdayUtils.getOffDaySet(2025, strategy);
```

All date-parameter methods in `WorkdayUtils` provide both `LocalDate` and `java.util.Date` overloads; the return type matches the input type.

### 1.5 Periodic Update

```java
// Cron-based auto-refresh (uses configured data-file.update.cron)
fetcher.startPeriodicUpdate();

// Stop auto-refresh
fetcher.stopPeriodicUpdate();
```

---

## 2. How It Works

### 2.1 Configuration Properties

Built-in `holiday-default.properties`:

```properties
# Region identifier (short form, internally mapped to "calendar-{region}")
# Examples: cn (mainland China), cn-tw (Taiwan), cn-hk (Hong Kong), sg (Singapore)
data-store.region=cn

# Base directory for data storage (actual path: {dir}/calendar-{region}/)
data-store.dir=./holiday-data

# Strategy for years without data: WEEKEND_ONLY or FESTIVAL
data-calc.fallback.strategy=WEEKEND_ONLY

# ── Data File Update Strategy (data-file.update) ──

# Enable or disable cron-based periodic data download (true/false, default true).
# When false, startPeriodicUpdate() will not start the scheduler thread; manual refresh() still works.
data-file.update.enabled=true

# Download schedule as a Quartz cron expression
# Default: 1st, 11th, 21st of November and December at 02:00
data-file.update.cron=0 0 2 1,11,21 11,12 ?

# Remote data source URL templates (comma-separated, use ${yyyy} as year placeholder)
# For each year, URLs are tried in order; first success stops further attempts
data-file.update.source.urls=https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/${yyyy}.json

# Year boundary: fetch current year, all years >= this value, and cached years
data-file.update.source.years-after=2026

# URL attempt order: sequential (default) | random
data-file.update.source.url-order=sequential

# ── Data File Loader Strategy (data-file.loader) ──

# Patch file suffix names (comma-separated, default: ext)
# For each year, loads {year}-{suffix}.json in configured order
data-file.loader.ext.suffixes=ext

# Classpath resource prefix (auto-derived from region as calendar-{region}/ by default)
data-file.loader.classpath.prefix=
```

### 2.2 Configuration Override

Place `holiday-config.properties` on the classpath to override defaults, or specify an external path:

```java
HolidayConfig config = HolidayConfig.load("/path/to/my-config.properties");
```

Loading priority: external file > classpath override > built-in defaults.

### 2.3 Data Loading Strategy

Multi-level loading priority on startup:

1. **Data directory** (`{data.dir}/{calendar.region}/`) — per-year JSON files, fully loaded into memory
2. **Classpath resources** (`{region}/{year}.json`) — data bundled in the jar
3. **Remote URL templates** — for each configured year, replaces `${yyyy}` and tries URLs in order until first success

Each level falls back to the next only when data is missing or empty. Successful fetches are persisted to the data directory with a `holiday-meta.json` metadata file.

### 2.4 Dynamic Years & Cron Scheduling

**Year determination** (`data-file.update.source.years-after`):

The program automatically determines which years to fetch:
- Current year
- All years ≥ `years-after` (up to current year + 2)
- Years with cached files in the data directory

For example, with `years-after=2026` and current year 2026, it fetches 2026, 2027, and 2028.

**Cron scheduling** (`data-file.update.cron`):

Uses a Quartz cron expression to control refresh timing. The default `0 0 2 1,11,21 11,12 ?` means execution on the 1st, 11th, and 21st of November and December at 02:00. Any valid Quartz cron expression is supported.

### 2.5 Off-Day Strategies

When no data file exists for a queried year, a configured strategy calculates off-days.

**Strategy A: WEEKEND_ONLY (default)** — Only Saturdays and Sundays are off-days (wage=1).

**Strategy B: FESTIVAL** — In addition to weekends:

- **Spring Festival**: Lunar New Year Day 1, 2, 3 (wage=3)
- **National Day**: October 1, 2, 3 (wage=3)
- **Deferral**: If a statutory holiday falls on a weekend, the next working day becomes a compensatory off-day (wage=1)

```java
HolidayConfig config = HolidayConfig.loadDefaults();
config.setStrategy("FESTIVAL");
HolidayFetcher fetcher = new HolidayFetcher(config);
```

### 2.6 Multi-Region Support

Use `data-store.region` to isolate data for different countries/regions:

| Value | Description | Classpath Dir | Disk Cache Dir |
|-----------|-------------|---------------|----------------|
| `cn` | Mainland China (default) | `calendar-cn/` | `holiday-data/calendar-cn/` |
| `cn-tw` | Taiwan | `calendar-cn-tw/` | `holiday-data/calendar-cn-tw/` |
| `cn-hk` | Hong Kong | `calendar-cn-hk/` | `holiday-data/calendar-cn-hk/` |
| `sg` | Singapore | `calendar-sg/` | `holiday-data/calendar-sg/` |

Each region's data is fully isolated. Configuration example:

```properties
data-store.region=cn-tw
data-file.update.source.urls=https://example.com/tw/${yyyy}.json
data-file.update.source.years-after=2026
```

Then provide bundled resources in the matching classpath directory (e.g., `resources/calendar-cn-tw/2025.json`).

### 2.7 Runtime Architecture

#### Application Instance Roles

| Object | Lifecycle | Description |
|--------|-----------|-------------|
| `HolidayFetcher` | Application-level, long-lived | Core instance holding config, strategy, in-memory cache (`ConcurrentHashMap`), and scheduler. Typically one instance per application or per region. |
| `HolidayConfig` | Follows HolidayFetcher | Mutable configuration holder. Retrieve via `getConfig()` for runtime modification. |
| `WorkdayUtils` | Stateless static utility | All methods are `static`, no instantiation needed, holds no state. |
| `OffDayStrategy` | Follows HolidayFetcher | Strategy implementation (`WeekendOnlyStrategy` / `FestivalStrategy`), held internally by HolidayFetcher. |

Typically, `HolidayFetcher` should be created once and held as an application-level singleton (e.g., Spring Bean, static field):

```java
// Created once at startup, reused for the entire application lifecycle
private static final HolidayFetcher fetcher = new HolidayFetcher();
```

#### Thread Model

| When | Thread | Description |
|------|--------|-------------|
| Constructing HolidayFetcher | No background thread | Constructor synchronously loads disk data into memory on the calling thread |
| Calling `startPeriodicUpdate()` | `workday-cn-java-updater` | Single-threaded `ScheduledExecutorService`, daemon thread, calculates next execution via Quartz cron and schedules tasks |
| Calling `stopPeriodicUpdate()` | — | Cancels the scheduled task and shuts down the thread pool |

The scheduler thread is a **daemon thread** and will not prevent JVM shutdown.

#### Runtime Management API

Use these methods to adjust configuration or trigger operations while the application is running:

| Method | Description | Effective |
|--------|-------------|----------|
| `getConfig()` | Returns the mutable HolidayConfig; modify `setRefreshCron()`, `setRefreshEnabled()`, `setSourceUrlOrder()`, etc. | Takes effect on next `refresh()` |
| `addSourceUrl(String)` | Append a data source URL at runtime | Takes effect on next `refresh()` |
| `refresh()` | Manually trigger data refresh (reload from disk / classpath / remote) | Executes immediately |
| `startPeriodicUpdate()` | Start cron-based scheduled refresh (skipped if `refreshEnabled=false`) | Uses current cron config |
| `stopPeriodicUpdate()` | Stop scheduled refresh | Stops immediately |
| `setCacheDir(String)` | Change the disk cache directory | Takes effect on next `refresh()` |

Query methods:

| Method | Description |
|--------|-------------|
| `getRefreshCron()` | Get current cron expression |
| `isRefreshEnabled()` | Get whether periodic refresh is enabled |
| `getSourceUrls()` | Get data source URL list (unmodifiable view) |
| `getCacheDir()` | Get current cache directory |
| `getCacheSize()` | Get in-memory cache entry count |
| `hasDataForYear(int)` | Check if data exists for a year |

**Typical management workflow**:

```java
// Modify cron at runtime and restart scheduler
fetcher.getConfig().setRefreshCron("0 0 3 1,11,21 * ?");
fetcher.startPeriodicUpdate(); // Restart to apply new cron

// Add a new data source and refresh immediately
fetcher.addSourceUrl("https://backup.example.com/${yyyy}.json");
fetcher.refresh();

// Modify patch suffixes and refresh
fetcher.getConfig().setExtSuffixes(Arrays.asList("ext", "company", "project"));
fetcher.refresh();
```

---

## 3. Extension

### 3.1 Patch Files

Each `{year}.json` file supports multiple optional patch files, configured via `data-file.loader.ext.suffixes` (comma-separated).

Default value `ext` maps to `{year}-ext.json`. Multiple suffixes can be configured:

```properties
# Loads 2025-ext.json first, then 2025-company.json (latter overrides former)
data-file.loader.ext.suffixes=ext,company
```

**Use cases:**

- Company-specific holidays (e.g., Christmas break, annual meeting day)
- Correcting erroneous entries in source data
- Adding temporary off-days or work-days
- Multi-layer customization: base patch + department/project-level patch

**Format** — identical to the main file:

```json
{
  "days": [
    {
      "date": "2025-10-01",
      "name": "Company Day",
      "isOffDay": false,
      "isWeekend": false,
      "wage": 1
    },
    {
      "date": "2025-12-25",
      "name": "Christmas",
      "isOffDay": true,
      "isWeekend": false,
      "wage": 1
    }
  ]
}
```

**Patch rules:**

- **Override**: Entries with the same `date` as the main file **replace** the original entry
- **Add**: New `date` entries are **appended** to the data
- **Multiple patches are applied in configured order** — later ones override earlier ones
- Patch files are **overlay-only** — never auto-written or modified by the program
- Detected and applied during both startup loading and periodic refresh

**Locations** (with `ext.suffixes=ext,company`):

| Source | Path Examples |
|--------|------|
| Disk data directory | `{dir}/calendar-{region}/{year}-ext.json`, `{year}-company.json` |
| Classpath bundled resource | `{classpath.prefix}{year}-ext.json`, `{year}-company.json` |

### 3.2 Bundling Data

Place holiday-cn JSON files in `src/main/resources/calendar-cn/` to distribute with your jar:

```
src/main/resources/calendar-cn/
├── 2025.json
├── 2026.json
└── ...
```

Bundled data participates in multi-level loading as a classpath resource, providing offline fallback when disk cache is unavailable.

---

## 4. Troubleshooting

### Data Not Fetched

- Verify network access to the configured remote URLs
- Ensure `${yyyy}` placeholder is present in `data-file.update.source.urls`
- Check logs for `Failed to fetch` or `Failed to parse` warnings

### Year Data Missing

- Confirm `data-file.update.source.years-after` covers the target year
- Check the data directory (`{dir}/calendar-{region}/`) for the corresponding `{year}.json` file
- If `holiday-meta.json` exists, verify its `years` field includes the target year

### Scheduled Task Not Triggering

- Confirm `fetcher.startPeriodicUpdate()` has been called
- Verify `data-file.update.cron` is a valid Quartz cron expression (note: Quartz does not allow both day-of-month and day-of-week — use `?` for one of them)
- Confirm `data-file.update.enabled` is `true`
- Search logs for `Periodic update started` to confirm scheduling is active

### Patch Not Applied

- Confirm the patch file name matches the pattern: `{year}-{suffix}.json` (e.g., `2025-ext.json`)
- Ensure the suffix is listed in `data-file.loader.ext.suffixes`
- Patch files must be placed in the data directory or under the classpath resource prefix

### Cross-Region Data Interference

- Ensure different regions use distinct `data-store.region` values
- Each region has independent data and classpath directories — check for path overlaps

---

## 5. API Reference

### HolidayFetcher

| Method | Description |
|--------|-------------|
| `HolidayFetcher()` | Initialize with default config |
| `HolidayFetcher(HolidayConfig)` | Initialize with custom config |
| `void refresh()` | Multi-level data refresh (with patch overlay) |
| `HolidayInfo getHoliday(LocalDate)` | Query date, falls back to strategy |
| `List<HolidayInfo> getHolidaysByYear(int)` | Get off-days for a year |
| `boolean isWorkDay(LocalDate)` | Check if date is a working day |
| `LocalDate addWorkDays(LocalDate, int)` | Date after N working days |
| `boolean hasDataForYear(int)` | Check if data exists for year |
| `boolean isRefreshEnabled()` | Check if periodic refresh is enabled |
| `void startPeriodicUpdate()` | Start cron-based scheduled refresh (controlled by refreshEnabled) |
| `void stopPeriodicUpdate()` | Stop scheduled refresh |

### HolidayConfig

| Method | Description |
|--------|-------------|
| `HolidayConfig.loadDefaults()` | Load built-in defaults only |
| `HolidayConfig.load()` | Defaults + classpath override |
| `HolidayConfig.load(String)` | Defaults + external file override |
| `String getCalendarRegion()` | Get region identifier |
| `String getEffectiveDataDir()` | Get effective data dir (with region sub-path) |

### WorkdayUtils

All date-parameter methods provide both `LocalDate` and `java.util.Date` overloads; the return type matches the input type.

| Method | Description |
|--------|-------------|
| `isWeekend(LocalDate)` / `isWeekend(Date)` | Check if weekend |
| `isWeekday(LocalDate)` / `isWeekday(Date)` | Check if weekday (calendar only) |
| `isWorkDay(LocalDate, OffDayStrategy)` / `isWorkDay(Date, OffDayStrategy)` | Check if work day (strategy-aware) |
| `addWorkDays(LocalDate, int, OffDayStrategy)` / `addWorkDays(Date, int, OffDayStrategy)` | Add N work days |
| `subtractWorkDays(LocalDate, int, OffDayStrategy)` / `subtractWorkDays(Date, int, OffDayStrategy)` | Subtract N work days |
| `nextWorkDay(LocalDate, OffDayStrategy)` / `nextWorkDay(Date, OffDayStrategy)` | Next work day |
| `previousWorkDay(LocalDate, OffDayStrategy)` / `previousWorkDay(Date, OffDayStrategy)` | Previous work day |
| `workDaysBetween(LocalDate, LocalDate, OffDayStrategy)` / `workDaysBetween(Date, Date, OffDayStrategy)` | Count work days in range |
| `getOffDaySet(int, OffDayStrategy)` | All off-days for a year |
