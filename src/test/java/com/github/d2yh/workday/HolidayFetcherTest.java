package com.github.d2yh.workday;

import com.github.d2yh.workday.config.HolidayConfig;
import com.github.d2yh.workday.exception.HolidayFetchException;
import com.github.d2yh.workday.model.HolidayInfo;
import com.github.d2yh.workday.strategy.FestivalStrategy;
import com.github.d2yh.workday.strategy.OffDayStrategy;
import com.github.d2yh.workday.strategy.WeekendOnlyStrategy;
import com.github.d2yh.workday.util.LunarCalendar;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * HolidayFetcher 单元测试
 */
public class HolidayFetcherTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final String SAMPLE_JSON = "{"
            + "\"days\": ["
            + "  {\"date\": \"2025-10-01\", \"name\": \"国庆节\", \"isOffDay\": true, \"isWeekend\": false, \"wage\": 3},"
            + "  {\"date\": \"2025-10-02\", \"name\": \"国庆节\", \"isOffDay\": true, \"isWeekend\": false, \"wage\": 3},"
            + "  {\"date\": \"2025-01-01\", \"name\": \"元旦\", \"isOffDay\": true, \"isWeekend\": false, \"wage\": 3},"
            + "  {\"date\": \"2025-09-28\", \"name\": \"国庆节\", \"isOffDay\": false, \"isWeekend\": true, \"wage\": 1}"
            + "]"
            + "}";

    private static final String SAMPLE_2026_JSON = "{"
            + "\"days\": ["
            + "  {\"date\": \"2026-01-01\", \"name\": \"元旦\", \"isOffDay\": true, \"isWeekend\": false, \"wage\": 3},"
            + "  {\"date\": \"2026-02-17\", \"name\": \"春节\", \"isOffDay\": true, \"isWeekend\": false, \"wage\": 3}"
            + "]"
            + "}";

    private String dataDir;
    private String effectiveDataDir;

    @Before
    public void setUp() {
        dataDir = tempFolder.getRoot().getAbsolutePath();
        effectiveDataDir = dataDir + File.separator + "calendar-cn";
    }

    // ──────────────── Mock helpers ────────────────

    private HolidayConfig createTestConfig() {
        HolidayConfig config = HolidayConfig.loadDefaults();
        config.setDataDir(dataDir);
        config.setSourceUrls(Collections.singletonList("http://mock.test/${yyyy}.json"));
        config.setSourceYearsAfter(2025);
        config.setClasspathResourcePrefix("nonexistent/"); // avoid real classpath hits
        return config;
    }

    private HolidayFetcher createMockFetcher(String responseJson) {
        HolidayConfig config = createTestConfig();
        return new HolidayFetcher(config) {
            @Override
            String fetchFromUrl(String url) {
                return responseJson;
            }
        };
    }

    private HolidayFetcher createMultiYearFetcher(
            int yearsAfter, List<String> urlTemplates, String response2025, String response2026) {
        HolidayConfig config = createTestConfig();
        config.setSourceYearsAfter(yearsAfter);
        config.setSourceUrls(urlTemplates);
        return new HolidayFetcher(config) {
            @Override
            String fetchFromUrl(String url) {
                if (url.contains("2026")) return response2026;
                return response2025;
            }
        };
    }

    // ──────────────── HolidayInfo Tests ────────────────

    @Test
    public void testHolidayInfoFields() {
        HolidayInfo info = new HolidayInfo("2025-10-01", "国庆节", true, false, 3);
        assertEquals("2025-10-01", info.getDate());
        assertEquals("国庆节", info.getName());
        assertTrue(info.isOffDay());
        assertFalse(info.isWeekend());
        assertEquals(3, info.getWage());
        assertEquals(2025, info.getYear());
    }

    @Test
    public void testHolidayInfoSetters() {
        HolidayInfo info = new HolidayInfo();
        info.setDate("2025-05-01");
        info.setName("劳动节");
        info.setOffDay(true);
        info.setWeekend(false);
        info.setWage(3);
        assertEquals("2025-05-01", info.getDate());
        assertEquals("劳动节", info.getName());
        assertTrue(info.isOffDay());
        assertEquals(3, info.getWage());
    }

    @Test
    public void testHolidayInfoIsWeekendDay() {
        assertTrue(HolidayInfo.isWeekendDay(LocalDate.of(2025, 10, 4)));  // Saturday
        assertTrue(HolidayInfo.isWeekendDay(LocalDate.of(2025, 10, 5)));  // Sunday
        assertFalse(HolidayInfo.isWeekendDay(LocalDate.of(2025, 10, 1))); // Wednesday
    }

    @Test
    public void testHolidayInfoToString() {
        HolidayInfo info = new HolidayInfo("2025-10-01", "国庆节", true, false, 3);
        String str = info.toString();
        assertTrue(str.contains("2025-10-01"));
        assertTrue(str.contains("国庆节"));
    }

    // ──────────────── Refresh Tests ────────────────

    @Test
    public void testRefreshFetchesAndCachesData() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.refresh();
        assertTrue(fetcher.getCacheSize() > 0);
    }

    @Test
    public void testGetHolidayAfterRefresh() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.refresh();
        HolidayInfo info = fetcher.getHoliday(LocalDate.of(2025, 10, 1));
        assertNotNull(info);
        assertEquals("国庆节", info.getName());
        assertTrue(info.isOffDay());
        assertEquals(3, info.getWage());
    }

    @Test
    public void testGetHolidayReturnsNullForNonHolidayWorkday() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.refresh();
        // 2025-10-15 is a Wednesday, not a weekend → null with WEEKEND_ONLY strategy
        // but strategy says non-weekend → null (not off day)
        // getHoliday returns null for non-off-days
        assertNull(fetcher.getHoliday(LocalDate.of(2025, 10, 15)));
    }

    @Test
    public void testRefreshSkipWhenDataFresh() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.refresh(); // first refresh writes meta file
        int sizeAfterFirst = fetcher.getCacheSize();

        fetcher.refresh(); // second refresh should be skipped (data fresh)
        assertEquals(sizeAfterFirst, fetcher.getCacheSize());
    }

    @Test
    public void testRefreshNotSkippedWhenDataExpired() throws IOException {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.refresh();

        // Manually expire the meta file (25 hours ago, beyond 24h freshness)
        File metaFile = new File(effectiveDataDir, "holiday-meta.json");
        assertTrue(metaFile.exists());
        metaFile.setLastModified(System.currentTimeMillis() - 25L * 3600 * 1000);

        final int[] fetchCount = {0};
        HolidayConfig config = createTestConfig();
        HolidayFetcher expiredFetcher = new HolidayFetcher(config) {
            @Override
            String fetchFromUrl(String url) {
                fetchCount[0]++;
                return SAMPLE_JSON;
            }
        };
        expiredFetcher.refresh();
        assertTrue(fetchCount[0] > 0);
    }

    // ──────────────── Multi-Source Tests ────────────────

    @Test
    public void testMultipleYearsFromUrlTemplate() {
        HolidayFetcher fetcher = createMultiYearFetcher(
                2025,
                Collections.singletonList("http://mock.test/${yyyy}.json"),
                SAMPLE_JSON, SAMPLE_2026_JSON);
        fetcher.refresh();
        assertNotNull(fetcher.getHoliday(LocalDate.of(2025, 10, 1)));
        assertNotNull(fetcher.getHoliday(LocalDate.of(2026, 1, 1)));
    }

    @Test
    public void testUrlTemplateFallbackOnFailure() {
        // First URL template fails, second succeeds
        HolidayConfig config = createTestConfig();
        config.setSourceYearsAfter(2025);
        config.setSourceUrls(Arrays.asList(
                "http://mock.test/fail/${yyyy}.json",
                "http://mock.test/ok/${yyyy}.json"));
        HolidayFetcher fetcher = new HolidayFetcher(config) {
            @Override
            String fetchFromUrl(String url) {
                if (url.contains("fail")) throw new HolidayFetchException("Simulated failure");
                return SAMPLE_JSON;
            }
        };
        fetcher.refresh(); // should NOT throw - second URL template succeeds
        assertTrue(fetcher.getCacheSize() > 0);
    }

    @Test(expected = HolidayFetchException.class)
    public void testAllUrlTemplatesFailThrows() {
        HolidayConfig config = createTestConfig();
        config.setSourceYearsAfter(2025);
        config.setSourceUrls(Arrays.asList(
                "http://mock.test/bad1/${yyyy}.json",
                "http://mock.test/bad2/${yyyy}.json"));
        HolidayFetcher fetcher = new HolidayFetcher(config) {
            @Override
            String fetchFromUrl(String url) {
                throw new HolidayFetchException("Simulated failure");
            }
        };
        fetcher.refresh();
    }

    @Test
    public void testSourceUrlOrderRandom() {
        HolidayConfig config = createTestConfig();
        config.setSourceYearsAfter(2025);
        config.setSourceUrls(Arrays.asList(
                "http://mock.test/a/${yyyy}.json",
                "http://mock.test/b/${yyyy}.json"));
        config.setSourceUrlOrder("random");
        HolidayFetcher fetcher = new HolidayFetcher(config) {
            @Override
            String fetchFromUrl(String url) {
                return SAMPLE_JSON;
            }
        };
        fetcher.refresh(); // should work with random order
        assertTrue(fetcher.getCacheSize() > 0);
    }

    @Test
    public void testSourceYearsAfterConfiguration() {
        HolidayConfig config = createTestConfig();
        config.setSourceYearsAfter(2024);
        assertEquals(2024, config.getSourceYearsAfter());
    }

    @Test
    public void testDetermineYearsToFetch() {
        HolidayConfig config = createTestConfig();
        config.setSourceYearsAfter(2025);
        HolidayFetcher fetcher = new HolidayFetcher(config);
        List<Integer> years = fetcher.determineYearsToFetch();
        int currentYear = LocalDate.now().getYear();
        // Should include current year and yearsAfter
        assertTrue(years.contains(currentYear));
        assertTrue(years.contains(2025));
        // Should be sorted (TreeSet)
        for (int i = 1; i < years.size(); i++) {
            assertTrue(years.get(i) > years.get(i - 1));
        }
    }

    // ──────────────── Parse Tests ────────────────

    @Test(expected = HolidayFetchException.class)
    public void testRefreshWithInvalidJsonThrows() {
        HolidayConfig config = createTestConfig();
        HolidayFetcher fetcher = new HolidayFetcher(config) {
            @Override
            String fetchFromUrl(String url) {
                return "not-valid-json";
            }
        };
        fetcher.refresh();
    }

    @Test(expected = HolidayFetchException.class)
    public void testRefreshWithMissingDaysFieldThrows() {
        HolidayConfig config = createTestConfig();
        HolidayFetcher fetcher = new HolidayFetcher(config) {
            @Override
            String fetchFromUrl(String url) {
                return "{\"holidays\": []}";
            }
        };
        fetcher.refresh();
    }

    // ──────────────── Query Tests ────────────────

    @Test
    public void testGetHolidaysByYear() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.refresh();
        List<HolidayInfo> holidays = fetcher.getHolidaysByYear(2025);
        assertFalse(holidays.isEmpty());
        for (HolidayInfo h : holidays) {
            assertTrue(h.getDate().startsWith("2025-"));
        }
    }

    @Test
    public void testGetHolidaysByYearNoDataUsesStrategy() {
        // 1999 has no data → strategy (WEEKEND_ONLY) generates weekends
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.refresh();
        List<HolidayInfo> holidays = fetcher.getHolidaysByYear(1999);
        assertFalse(holidays.isEmpty()); // weekends for 1999
        for (HolidayInfo h : holidays) {
            assertTrue(h.getDate().startsWith("1999-"));
            assertTrue(h.isWeekend());
        }
    }

    @Test
    public void testGetHolidaysByYearSorted() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.refresh();
        List<HolidayInfo> holidays = fetcher.getHolidaysByYear(2025);
        for (int i = 1; i < holidays.size(); i++) {
            assertTrue(holidays.get(i).getDate().compareTo(holidays.get(i - 1).getDate()) >= 0);
        }
    }

    @Test
    public void testQuerySpecificHolidayDetails() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.refresh();
        HolidayInfo weekendDay = fetcher.getHoliday(LocalDate.of(2025, 9, 28));
        assertNotNull(weekendDay);
        assertTrue(weekendDay.isWeekend());
        assertFalse(weekendDay.isOffDay());
        assertEquals(1, weekendDay.getWage());
    }

    // ──────────────── Data Dir Tests ────────────────

    @Test
    public void testDataFilesCreatedOnDisk() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.refresh();
        File yearFile = new File(effectiveDataDir, "2025.json");
        File metaFile = new File(effectiveDataDir, "holiday-meta.json");
        assertTrue(yearFile.exists());
        assertTrue(metaFile.exists());
        assertTrue(yearFile.length() > 0);
    }

    @Test
    public void testDataLoadedFromDiskOnNewInstance() {
        HolidayFetcher fetcher1 = createMockFetcher(SAMPLE_JSON);
        fetcher1.refresh();

        // New fetcher with same dataDir should load from disk
        HolidayConfig config2 = createTestConfig();
        HolidayFetcher fetcher2 = new HolidayFetcher(config2);
        assertTrue(fetcher2.getCacheSize() > 0);
        assertNotNull(fetcher2.getHoliday(LocalDate.of(2025, 10, 1)));
    }

    @Test
    public void testHasDataForYear() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.refresh();
        assertTrue(fetcher.hasDataForYear(2025));
        assertFalse(fetcher.hasDataForYear(1999));
    }

    // ──────────────── Extension (ext.json) Tests ────────────────

    @Test
    public void testExtFileOverlayFromDataDir() throws IOException {
        // 1) Write main data and ext file to disk
        HolidayFetcher fetcher1 = createMockFetcher(SAMPLE_JSON);
        fetcher1.refresh();

        // Create ext file that overrides 2025-10-01 (originally wage=3)
        String extJson = "{\"days\": ["
                + "  {\"date\": \"2025-10-01\", \"name\": \"公司日\", \"isOffDay\": false, \"isWeekend\": false, \"wage\": 1},"
                + "  {\"date\": \"2025-12-25\", \"name\": \"公司圣诞\", \"isOffDay\": true, \"isWeekend\": false, \"wage\": 1}"
                + "]}";
        File extFile = new File(effectiveDataDir, "2025-ext.json");
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        om.writeValue(extFile, om.readValue(extJson, Object.class));

        // 2) New fetcher should load main + ext from data dir
        HolidayConfig config2 = createTestConfig();
        HolidayFetcher fetcher2 = new HolidayFetcher(config2);

        // Override: 2025-10-01 should now be a work day (isOffDay=false)
        HolidayInfo oct1 = fetcher2.getHoliday(LocalDate.of(2025, 10, 1));
        assertNotNull(oct1);
        assertFalse(oct1.isOffDay());
        assertEquals("公司日", oct1.getName());

        // New entry: 2025-12-25 should be added
        HolidayInfo dec25 = fetcher2.getHoliday(LocalDate.of(2025, 12, 25));
        assertNotNull(dec25);
        assertTrue(dec25.isOffDay());
        assertEquals("公司圣诞", dec25.getName());
    }

    @Test
    public void testExtFileFromClasspath() {
        // Use the real classpath prefix "calendar-cn/" to test ext loading
        HolidayConfig config = HolidayConfig.loadDefaults();
        config.setDataDir(dataDir);
        config.setSourceUrls(Collections.singletonList("http://mock.test/${yyyy}.json"));
        config.setSourceYearsAfter(2025);
        // classpath prefix defaults to "calendar-cn/" which has real data

        // There is no 2025-ext.json in classpath, so loadExtFromClasspath should return empty
        HolidayFetcher fetcher = new HolidayFetcher(config) {
            @Override
            String fetchFromUrl(String url) {
                return SAMPLE_JSON;
            }
        };
        List<HolidayInfo> extEntries = fetcher.loadExtFromClasspath(2025);
        // No ext file exists in classpath, so should be empty
        assertTrue(extEntries.isEmpty());
    }

    @Test
    public void testMultipleExtSuffixes() throws IOException {
        // 1) Write main data
        HolidayFetcher fetcher1 = createMockFetcher(SAMPLE_JSON);
        fetcher1.refresh();

        // 2) Create ext file: overrides 2025-10-01
        String extJson = "{\"days\": ["
                + "  {\"date\": \"2025-10-01\", \"name\": \"ext修正\", \"isOffDay\": false, \"isWeekend\": false, \"wage\": 1}"
                + "]}";
        File extFile = new File(effectiveDataDir, "2025-ext.json");
        com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
        om.writeValue(extFile, om.readValue(extJson, Object.class));

        // 3) Create company file: overrides 2025-10-01 again and adds new date
        String companyJson = "{\"days\": ["
                + "  {\"date\": \"2025-10-01\", \"name\": \"公司日\", \"isOffDay\": true, \"isWeekend\": false, \"wage\": 2},"
                + "  {\"date\": \"2025-11-11\", \"name\": \"公司日\", \"isOffDay\": true, \"isWeekend\": false, \"wage\": 1}"
                + "]}";
        File companyFile = new File(effectiveDataDir, "2025-company.json");
        om.writeValue(companyFile, om.readValue(companyJson, Object.class));

        // 4) Configure two suffixes: ext,company
        HolidayConfig config2 = createTestConfig();
        config2.setExtSuffixes(Arrays.asList("ext", "company"));
        HolidayFetcher fetcher2 = new HolidayFetcher(config2);

        // company overlays ext: 2025-10-01 should be "公司日" with wage=2
        HolidayInfo oct1 = fetcher2.getHoliday(LocalDate.of(2025, 10, 1));
        assertNotNull(oct1);
        assertTrue(oct1.isOffDay());
        assertEquals("公司日", oct1.getName());
        assertEquals(2, oct1.getWage());

        // New entry from company: 2025-11-11
        HolidayInfo nov11 = fetcher2.getHoliday(LocalDate.of(2025, 11, 11));
        assertNotNull(nov11);
        assertTrue(nov11.isOffDay());
    }

    @Test
    public void testExtFileDoesNotExistIsHarmless() {
        // Without ext file, everything should work normally
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.refresh();
        assertNotNull(fetcher.getHoliday(LocalDate.of(2025, 10, 1)));
        assertTrue(fetcher.getCacheSize() >= 4);
    }

    // ──────────────── Configuration Tests ────────────────

    @Test
    public void testGetRefreshCron() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        assertNotNull(fetcher.getRefreshCron());
        assertFalse(fetcher.getRefreshCron().isEmpty());
    }

    @Test
    public void testSetCacheDir() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.setCacheDir("/custom/path");
        assertEquals("/custom/path" + File.separator + "calendar-cn", fetcher.getCacheDir());
    }

    @Test
    public void testAddSourceUrl() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        assertEquals(1, fetcher.getSourceUrls().size());
        fetcher.addSourceUrl("http://mock.test/2026.json");
        assertEquals(2, fetcher.getSourceUrls().size());
    }

    @Test
    public void testGetSourceUrlsUnmodifiable() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        try {
            fetcher.getSourceUrls().add("http://hack.test");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testDefaultRefreshCronIsLoaded() {
        HolidayFetcher fetcher = new HolidayFetcher(createTestConfig());
        assertNotNull(fetcher.getRefreshCron());
    }

    @Test
    public void testExtractYearFromUrl() {
        HolidayFetcher fetcher = new HolidayFetcher(createTestConfig());
        assertEquals(2025, fetcher.extractYearFromUrl(
                "https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/2025.json"));
        assertEquals(2026, fetcher.extractYearFromUrl("http://example.com/2026.json"));
        assertEquals(-1, fetcher.extractYearFromUrl("http://example.com/data.json"));
    }

    @Test
    public void testDefaultCalendarRegion() {
        HolidayConfig config = HolidayConfig.loadDefaults();
        assertEquals("calendar-cn", config.getCalendarRegion());
    }

    @Test
    public void testClasspathPrefixAutoDerivedFromRegion() {
        HolidayConfig config = HolidayConfig.loadDefaults();
        assertEquals("calendar-cn/", config.getClasspathResourcePrefix());
    }

    @Test
    public void testEffectiveDataDirIncludesRegion() {
        HolidayConfig config = HolidayConfig.loadDefaults();
        config.setDataDir("/tmp/test-data");
        String expected = "/tmp/test-data" + File.separator + "calendar-cn";
        assertEquals(expected, config.getEffectiveDataDir());
    }

    @Test
    public void testCustomRegionIsolation() {
        HolidayConfig config = createTestConfig();
        config.setCalendarRegion("calendar-cn-tw");
        config.setClasspathResourcePrefix("nonexistent/");

        String expectedDir = dataDir + File.separator + "calendar-cn-tw";
        assertEquals(expectedDir, config.getEffectiveDataDir());
    }

    @Test
    public void testDataIsolatedByRegion() {
        // Write data under calendar-cn region
        HolidayFetcher fetcher1 = createMockFetcher(SAMPLE_JSON);
        fetcher1.refresh();
        File cnYearFile = new File(effectiveDataDir, "2025.json");
        assertTrue(cnYearFile.exists());

        // Different region should not see the data
        HolidayConfig config2 = createTestConfig();
        config2.setCalendarRegion("calendar-sg");
        config2.setClasspathResourcePrefix("nonexistent/");
        String sgDir = dataDir + File.separator + "calendar-sg";
        File sgYearFile = new File(sgDir, "2025.json");
        assertFalse(sgYearFile.exists());
    }

    // ──────────────── Strategy Tests ────────────────

    @Test
    public void testWeekendOnlyStrategyIsDefault() {
        HolidayFetcher fetcher = new HolidayFetcher(createTestConfig());
        assertTrue(fetcher.getStrategy() instanceof WeekendOnlyStrategy);
    }

    @Test
    public void testFestivalStrategyFromConfig() {
        HolidayConfig config = createTestConfig();
        config.setStrategy("FESTIVAL");
        HolidayFetcher fetcher = new HolidayFetcher(config);
        assertTrue(fetcher.getStrategy() instanceof FestivalStrategy);
    }

    @Test
    public void testWeekendOnlyIsOffDay() {
        OffDayStrategy strategy = new WeekendOnlyStrategy();
        assertTrue(strategy.isOffDay(LocalDate.of(2025, 10, 4)));  // Saturday
        assertTrue(strategy.isOffDay(LocalDate.of(2025, 10, 5)));  // Sunday
        assertFalse(strategy.isOffDay(LocalDate.of(2025, 10, 1))); // Wednesday
    }

    @Test
    public void testWeekendOnlyGenerateOffDays() {
        OffDayStrategy strategy = new WeekendOnlyStrategy();
        List<HolidayInfo> offDays = strategy.generateOffDays(2025);
        assertFalse(offDays.isEmpty());
        for (HolidayInfo h : offDays) {
            assertTrue(h.isWeekend());
            assertEquals(1, h.getWage());
        }
    }

    @Test
    public void testFestivalStrategyIncludesNationalDay() {
        OffDayStrategy strategy = new FestivalStrategy();
        List<HolidayInfo> offDays = strategy.generateOffDays(2025);
        // Find 10.1, 10.2, 10.3 or their deferred days
        boolean foundNationalDay = false;
        for (HolidayInfo h : offDays) {
            if (h.getName().contains("国庆")) {
                foundNationalDay = true;
                break;
            }
        }
        assertTrue("Should include National Day", foundNationalDay);
    }

    @Test
    public void testFestivalStrategyIncludesSpringFestival() {
        OffDayStrategy strategy = new FestivalStrategy();
        List<HolidayInfo> offDays = strategy.generateOffDays(2025);
        boolean foundSpringFestival = false;
        for (HolidayInfo h : offDays) {
            if (h.getName().contains("春节")) {
                foundSpringFestival = true;
                break;
            }
        }
        assertTrue("Should include Spring Festival", foundSpringFestival);
    }

    @Test
    public void testFestivalStrategyMoreOffDaysThanWeekend() {
        OffDayStrategy festivalStrategy = new FestivalStrategy();
        OffDayStrategy weekendStrategy = new WeekendOnlyStrategy();
        List<HolidayInfo> festivalDays = festivalStrategy.generateOffDays(2025);
        List<HolidayInfo> weekendDays = weekendStrategy.generateOffDays(2025);
        assertTrue(festivalDays.size() > weekendDays.size());
    }

    @Test
    public void testStrategyFallbackInGetHoliday() {
        // For a year without data, getHoliday should use strategy
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.refresh();
        // Query a Saturday in 1999 (no data) → strategy says off day
        LocalDate saturday1999 = LocalDate.of(1999, 1, 2); // Saturday
        HolidayInfo info = fetcher.getHoliday(saturday1999);
        assertNotNull(info);
        assertTrue(info.isOffDay());
    }

    // ──────────────── Working Day Tests ────────────────

    @Test
    public void testIsWorkDay() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.refresh();
        // Wednesday = work day
        assertTrue(fetcher.isWorkDay(LocalDate.of(2025, 10, 15)));
        // Saturday = not work day (strategy)
        assertFalse(fetcher.isWorkDay(LocalDate.of(2025, 10, 4)));
        // 2025-10-01 is a holiday from data
        assertFalse(fetcher.isWorkDay(LocalDate.of(2025, 10, 1)));
    }

    @Test
    public void testAddWorkDaysZero() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.refresh();
        // n=0 from a work day returns the same day
        LocalDate wed = LocalDate.of(2025, 10, 15);
        assertEquals(wed, fetcher.addWorkDays(wed, 0));
    }

    @Test
    public void testAddWorkDaysSkipsWeekends() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.refresh();
        // Friday Oct 17 + 1 work day = Monday Oct 20 (skip Sat/Sun)
        LocalDate friday = LocalDate.of(2025, 10, 17);
        LocalDate result = fetcher.addWorkDays(friday, 1);
        assertEquals(LocalDate.of(2025, 10, 20), result); // Monday
    }

    @Test
    public void testAddWorkDaysSkipsHolidays() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.refresh();
        // Sep 30 (Tue) + 1 work day: Oct 1-2 are holidays → Oct 3 (Fri)
        LocalDate sep30 = LocalDate.of(2025, 9, 30);
        LocalDate result = fetcher.addWorkDays(sep30, 1);
        assertEquals(LocalDate.of(2025, 10, 3), result);
    }

    @Test
    public void testAddWorkDaysMultiple() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.refresh();
        // Oct 15 (Wed) + 5 work days = Oct 22 (Wed)
        // Oct 15(W), 16(Th), 17(F), 18(Sat skip), 19(Sun skip), 20(M), 21(Tu), 22(W) = 5 work days
        LocalDate start = LocalDate.of(2025, 10, 15);
        LocalDate result = fetcher.addWorkDays(start, 5);
        assertEquals(LocalDate.of(2025, 10, 22), result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddWorkDaysNegativeThrows() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.addWorkDays(LocalDate.of(2025, 10, 15), -1);
    }

    @Test
    public void testAddWorkDaysFromWeekendStart() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.refresh();
        // Starting from Saturday, should advance to Monday first
        LocalDate saturday = LocalDate.of(2025, 10, 4);
        LocalDate result = fetcher.addWorkDays(saturday, 1);
        // Saturday → skip to Monday Oct 6, then +1 work day = Tuesday Oct 7
        assertEquals(LocalDate.of(2025, 10, 7), result);
    }

    // ──────────────── Lunar Calendar Tests ────────────────

    @Test
    public void testLunarCalendar2025SpringFestival() {
        LocalDate date = LunarCalendar.getSpringFestivalDate(2025);
        assertEquals(LocalDate.of(2025, 1, 29), date);
    }

    @Test
    public void testLunarCalendar2026SpringFestival() {
        LocalDate date = LunarCalendar.getSpringFestivalDate(2026);
        assertEquals(LocalDate.of(2026, 2, 17), date);
    }

    @Test
    public void testLunarCalendarNewYearDates() {
        List<LocalDate> dates = LunarCalendar.getLunarNewYearDates(2025);
        assertEquals(3, dates.size());
        assertEquals(LocalDate.of(2025, 1, 29), dates.get(0)); // 初一
        assertEquals(LocalDate.of(2025, 1, 30), dates.get(1)); // 初二
        assertEquals(LocalDate.of(2025, 1, 31), dates.get(2)); // 初三
    }

    @Test
    public void testLunarCalendarIsSupported() {
        assertTrue(LunarCalendar.isSupported(2025));
        assertTrue(LunarCalendar.isSupported(2000));
        assertTrue(LunarCalendar.isSupported(2050));
        assertFalse(LunarCalendar.isSupported(1999));
        assertFalse(LunarCalendar.isSupported(2051));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLunarCalendarOutOfRangeThrows() {
        LunarCalendar.getSpringFestivalDate(1999);
    }

    // ──────────────── HolidayConfig Tests ────────────────

    @Test
    public void testConfigLoadDefaults() {
        HolidayConfig config = HolidayConfig.loadDefaults();
        assertNotNull(config);
        assertEquals("./holiday-data", config.getDataDir());
        assertNotNull(config.getRefreshCron());
        assertFalse(config.getRefreshCron().isEmpty());
        assertEquals("WEEKEND_ONLY", config.getStrategy());
        assertEquals("calendar-cn/", config.getClasspathResourcePrefix());
        assertFalse(config.getSourceUrls().isEmpty());
        assertTrue(config.isRefreshEnabled());
    }

    @Test
    public void testConfigSetters() {
        HolidayConfig config = HolidayConfig.loadDefaults();
        config.setDataDir("/custom");
        config.setRefreshCron("0 0 3 * * ?");
        config.setStrategy("FESTIVAL");
        assertEquals("/custom", config.getDataDir());
        assertEquals("0 0 3 * * ?", config.getRefreshCron());
        assertEquals("FESTIVAL", config.getStrategy());
    }

    // ──────────────── Periodic Update Tests ────────────────

    @Test
    public void testStartAndStopPeriodicUpdate() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.startPeriodicUpdate();
        fetcher.stopPeriodicUpdate(); // should not throw
    }

    @Test
    public void testStopPeriodicUpdateWhenNotStarted() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.stopPeriodicUpdate(); // should NOT throw
    }

    @Test
    public void testStartPeriodicUpdateReplacesExisting() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.startPeriodicUpdate();
        fetcher.startPeriodicUpdate(); // replaces
        fetcher.stopPeriodicUpdate();
    }

    @Test
    public void testIsRefreshEnabled() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        assertTrue(fetcher.isRefreshEnabled());
    }

    @Test
    public void testStartPeriodicUpdateDisabled() {
        HolidayFetcher fetcher = createMockFetcher(SAMPLE_JSON);
        fetcher.getConfig().setRefreshEnabled(false);
        fetcher.startPeriodicUpdate(); // should log and skip, no thread created
        fetcher.stopPeriodicUpdate();  // harmless
        assertFalse(fetcher.isRefreshEnabled());
    }

    // ──────────────── Real Data Format Tests ────────────────

    @Test
    public void testGetHolidayFestivalFallbackDetails() {
        // FESTIVAL 策略：无数据年份应返回节日详情而非通用包装
        HolidayConfig config = createTestConfig();
        config.setStrategy("FESTIVAL");
        HolidayFetcher fetcher = new HolidayFetcher(config);
        HolidayInfo info = fetcher.getHoliday(LocalDate.of(2031, 1, 23)); // 2031 春节初一（周四）
        assertNotNull(info);
        assertEquals("春节", info.getName());
        assertEquals(3, info.getWage());
    }

    @Test
    public void testGetHolidayWeekendFallbackDetails() {
        HolidayFetcher fetcher = new HolidayFetcher(createTestConfig()); // WEEKEND_ONLY
        HolidayInfo saturday = fetcher.getHoliday(LocalDate.of(2031, 1, 25)); // 周六
        assertNotNull(saturday);
        assertEquals("周末", saturday.getName());
        assertNull(fetcher.getHoliday(LocalDate.of(2031, 1, 23))); // 周四 → 工作日
    }

    @Test
    public void testParseRealHolidayCnFormat() {
        // 真实 holiday-cn 文件顶层含 $schema/$id/year/papers 等额外字段
        String realFormat = "{"
                + "\"$schema\": \"https://example.com/schema.json\","
                + "\"$id\": \"https://example.com/2025.json\","
                + "\"year\": 2025,"
                + "\"papers\": [\"https://example.com/paper.htm\"],"
                + "\"days\": ["
                + "  {\"date\": \"2025-10-01\", \"name\": \"国庆节\", \"isOffDay\": true, \"isWeekend\": false, \"wage\": 3}"
                + "]"
                + "}";
        HolidayFetcher fetcher = createMockFetcher(realFormat);
        fetcher.refresh();
        HolidayInfo info = fetcher.getHoliday(LocalDate.of(2025, 10, 1));
        assertNotNull("真实格式的额外顶层字段不应导致解析失败", info);
        assertEquals("国庆节", info.getName());
        assertEquals(3, info.getWage());
    }

    @Test
    public void testLoadBundledDataFromClasspath() {
        // 库内置的 classpath 数据文件即真实 holiday-cn 格式
        HolidayConfig config = HolidayConfig.loadDefaults();
        config.setDataDir(dataDir);
        config.setSourceUrls(Collections.<String>emptyList());
        HolidayFetcher fetcher = new HolidayFetcher(config);
        List<HolidayInfo> days = fetcher.loadFromClasspath(2025);
        assertFalse("内置 2025 数据应能正常解析", days.isEmpty());
    }
}
