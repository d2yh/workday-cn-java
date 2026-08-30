# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

> 项目尚未公开发布，版本号处于 0.x 预发布迭代阶段（当前开发版本 **0.2.0**）。
> 版本曾由 `1.0.0` 重置为 `0.1.0` 以符合语义化版本的预发布约定。

### Added
- `HolidayConfig` — `.properties` configuration system with defaults and user override
- `OffDayStrategy` interface with two implementations:
  - `WeekendOnlyStrategy` (Strategy A): weekends as off-days
  - `FestivalStrategy` (Strategy B): weekends + Spring Festival + National Day with weekend deferral
- `LunarCalendar` — lookup-table-based lunar calendar utility (2000-2050)
- `HolidayFetcher.addWorkDays(LocalDate, int)` — calculate date after N working days
- `HolidayFetcher.isWorkDay(LocalDate)` — check if a date is a working day
- `HolidayFetcher.hasDataForYear(int)` — check if data exists for a year
- `HolidayInfo.isWeekendDay(LocalDate)` — static weekend check utility
- Multi-level data loading: data directory → classpath → remote URL
- Per-year JSON files in data directory with `holiday-meta.json` metadata
- Full English and Chinese README documentation
- 129 unit tests covering all features
- `WorkdayUtils` month-based methods: `workDaysInMonth`, `firstWorkDayOfMonth`,
  `lastWorkDayOfMonth`, `nthWorkDayOfMonth`, `getWorkDays` (work-day enumeration in a range)
- `samples/` — five runnable sample projects: `00-default` (WorkdayUtils API showcase)
  plus four configuration patterns (zero-config, classpath override, external config file,
  programmatic config with `{year}-ext.json` patch overlay)
- `HolidayDataFile` — data-file wrapper tolerating extra top-level fields
  (`$schema`, `$id`, `year`, `papers`) in real holiday-cn files
- `OffDayStrategy.getOffDayInfo(LocalDate)` — strategies now return detailed off-day
  entries (festival name, wage multiplier) instead of a generic wrapper

### Changed
- Project renamed `holiday-cn-java` → `workday-cn-java`; package migrated
  `com.github.d2yh.holiday` → `com.github.d2yh.workday`
- Version reset `1.0.0` → `0.1.0` (pre-release, nothing was ever published)
- Version bumped `0.1.0` → `0.2.0` for the WorkdayUtils month-based method additions
- `HolidayFetcher` refactored to use `HolidayConfig` for all settings
- Periodic refresh is cron-based (Quartz expression; default: 1st, 11th, 21st of Nov/Dec at 02:00), replacing the earlier fixed-interval design
- `getHoliday()` and `getHolidaysByYear()` now fall back to strategy when no data exists
- Data directory replaces single cache file approach
- `logback-classic` moved to `test` scope and `logback.xml` moved to `src/test/resources`,
  so the library no longer ships a logging configuration or implementation to consumers
- HTTP fetches now enforce connect/connection-request/response timeouts

### Fixed
- Real holiday-cn JSON (with non-array top-level fields) failed to parse on all three
  loading paths (classpath, disk, remote) and the error was silently swallowed
- Default remote data source URL pointed to a GitHub `/tree/` page (returns HTML);
  now uses the raw file address of the renamed repository
- `FestivalStrategy` per-year cache switched to `ConcurrentHashMap` (thread safety)

### Compatibility
- Minimum Java version: 1.8
