# workday-cn-java

A Java library for fetching and caching Chinese holiday data. Pulls holiday configurations from remote sources with local caching, strategy-based calculation, and working-day arithmetic.

## Features

- 📅 **Multi-source data fetching** — Remote holiday data compatible with [holiday-cn](https://github.com/NateScarlet/holiday-cn) JSON format
- 💾 **Multi-level loading** — Data directory → classpath resources → remote URLs with cascading fallback
- 🩹 **Extension patches (ext.json)** — Optional `{year}-ext.json` files to override or add date entries for company-level customization
- 🔄 **Periodic auto-refresh** — Configurable interval (default: every 10 days)
- 🌏 **Multi-region isolation** — `calendar.region` config for different countries/regions (CN, TW, HK, SG, etc.)
- 🧮 **Strategy-based calculation** — Auto-compute off-days for years without data: weekends only (WEEKEND_ONLY) or weekends + Spring Festival + National Day (FESTIVAL)
- 📆 **Working-day arithmetic** — Instance methods (`isWorkDay`, `addWorkDays`) + standalone `WorkdayUtils` utility class
- 📦 **Configuration-driven** — `.properties` config with built-in defaults, classpath override, and external file override
- ⚡ **In-memory caching** — All data loaded at startup for zero-latency queries

## Data Format

Compatible with [holiday-cn](https://github.com/NateScarlet/holiday-cn) JSON format:

```json
{
  "days": [
    {
      "date": "2025-10-01",
      "name": "National Day",
      "isOffDay": true,
      "isWeekend": false,
      "wage": 3
    }
  ]
}
```

| Field | Type | Description |
|-------|------|-------------|
| `date` | String | Date in YYYY-MM-DD format |
| `name` | String | Holiday name |
| `isOffDay` | Boolean | Whether it is a statutory off day |
| `isWeekend` | Boolean | Whether it falls on a weekend |
| `wage` | Integer | Wage multiplier (1 for workday, 3 for statutory holiday) |

## Installation

```xml
<dependency>
    <groupId>com.github.d2yh</groupId>
    <artifactId>workday-cn-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick Start

```java
import com.github.d2yh.workday.HolidayFetcher;
import com.github.d2yh.workday.model.HolidayInfo;
import java.time.LocalDate;

// Initialize with default configuration
HolidayFetcher fetcher = new HolidayFetcher();

// Fetch and cache data
fetcher.refresh();

// Query a specific date
HolidayInfo holiday = fetcher.getHoliday(LocalDate.of(2025, 10, 1));
if (holiday != null) {
    System.out.println("Holiday: " + holiday.getName());
}

// Get all off-days for a year
List<HolidayInfo> holidays = fetcher.getHolidaysByYear(2025);

// Working day calculation
boolean isWorkDay = fetcher.isWorkDay(LocalDate.of(2025, 10, 15));
LocalDate deadline = fetcher.addWorkDays(LocalDate.of(2025, 10, 15), 10);
```

## Detailed Documentation

For configuration reference, data loading strategy, ext.json patching, off-day strategies, working-day utilities, and full API reference, see:

**→ [Development Guide](DEVELOPMENT.md)**

## Attribution

Inspired by [holiday-cn](https://github.com/NateScarlet/holiday-cn) and its data format.

## License

MIT License - see [LICENSE](LICENSE) for details.
