package com.github.d2yh.workday;

import com.github.d2yh.workday.config.HolidayConfig;
import com.github.d2yh.workday.exception.HolidayFetchException;
import com.github.d2yh.workday.exception.HolidayParseException;
import com.github.d2yh.workday.model.HolidayDataFile;
import com.github.d2yh.workday.model.HolidayInfo;
import com.github.d2yh.workday.strategy.FestivalStrategy;
import com.github.d2yh.workday.strategy.OffDayStrategy;
import com.github.d2yh.workday.strategy.WeekendOnlyStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cronutils.model.Cron;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中国节假日数据拉取器。
 * <p>
 * 支持多级数据加载、策略计算、工作日推算和定时更新。
 */
public class HolidayFetcher {

    private static final Logger logger = LoggerFactory.getLogger(HolidayFetcher.class);
    private static final String META_FILE_NAME = "holiday-meta.json";

    private final HolidayConfig config;
    private final OffDayStrategy strategy;
    private final ObjectMapper objectMapper;
    private final Map<String, HolidayInfo> holidayCache = new ConcurrentHashMap<>();

    /** 已加载数据的年份集合 */
    private final Set<Integer> loadedYears = ConcurrentHashMap.newKeySet();

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;

    // ──────────────── Constructors ────────────────

    /**
     * 使用配置对象初始化。
     */
    public HolidayFetcher(HolidayConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.strategy = createStrategy(config.getStrategy());
        loadFromDataDir();
    }

    /**
     * 无参构造器，自动加载默认配置。
     */
    public HolidayFetcher() {
        this(HolidayConfig.load());
    }

    private static OffDayStrategy createStrategy(String strategyName) {
        if ("FESTIVAL".equalsIgnoreCase(strategyName)) {
            return new FestivalStrategy();
        }
        return new WeekendOnlyStrategy();
    }

    // ──────────────── Refresh ────────────────

    /**
     * 拉取节假日数据，采用多级加载策略：
     * <ol>
     *   <li>磁盘数据目录缓存有效时直接使用</li>
     *   <li>对每个配置年份：先尝试 classpath 内置资源，再依次尝试远程 URL 模板</li>
     *   <li>每个年份加载后，自动依次叠加配置的补丁文件（{@code {year}-{suffix}.json}）</li>
     * </ol>
     * 最终结果持久化到数据目录并加载到内存。
     */
    public void refresh() {
        if (isDataFresh()) {
            logger.info("Data is still fresh, skipping refresh");
            return;
        }

        List<Integer> years = determineYearsToFetch();
        List<String> urlTemplates = config.getSourceUrls();
        boolean isRandom = "random".equalsIgnoreCase(config.getSourceUrlOrder());
        List<HolidayInfo> allHolidays = new ArrayList<>();

        logger.info("Years to fetch: {}", years);

        for (int year : years) {
            List<HolidayInfo> holidays = Collections.emptyList();

            // 1) classpath 内置资源
            holidays = loadFromClasspath(year);
            if (!holidays.isEmpty()) {
                logger.info("Loaded {} holidays from classpath for year {}",
                        holidays.size(), year);
            }

            // 2) 依次尝试远程 URL 模板
            if (holidays.isEmpty() && !urlTemplates.isEmpty()) {
                List<String> urlsToTry = new ArrayList<>(urlTemplates);
                if (isRandom) {
                    Collections.shuffle(urlsToTry);
                }
                for (String template : urlsToTry) {
                    String url = template.replace("${yyyy}", String.valueOf(year));
                    try {
                        String json = fetchFromUrl(url);
                        holidays = parseHolidayData(json);
                        logger.info("Fetched {} holidays from remote URL {}", holidays.size(), url);
                        break; // 首个成功即停止
                    } catch (HolidayFetchException e) {
                        logger.warn("Failed to fetch from {}: {}", url, e.getMessage());
                    } catch (HolidayParseException e) {
                        logger.warn("Failed to parse data from {}: {}", url, e.getMessage());
                    }
                }
            }

            allHolidays.addAll(holidays);

            // 依次叠加补丁文件（classpath 来源）
            List<HolidayInfo> extHolidays = loadExtFromClasspath(year);
            if (!extHolidays.isEmpty()) {
                allHolidays.addAll(extHolidays);
            }
        }

        if (allHolidays.isEmpty() && holidayCache.isEmpty()) {
            throw new HolidayFetchException("No holiday data could be fetched from any source");
        }

        for (HolidayInfo info : allHolidays) {
            holidayCache.put(info.getDate(), info);
            if (info.getYear() > 0) {
                loadedYears.add(info.getYear());
            }
        }

        saveToDataDir();
        logger.info("Data refreshed, total entries: {}", holidayCache.size());
    }

    // ──────────────── Year Determination ────────────────

    /**
     * 动态确定需要拉取数据的年份列表。
     * <p>
     * 包含：
     * <ul>
     *   <li>当前年份</li>
     *   <li>所有 ≥ {@code sourceYearsAfter} 的年份（直到当前年份 + 2）</li>
     *   <li>磁盘数据目录中已有缓存文件的年份</li>
     * </ul>
     *
     * @return 排序后的年份列表
     */
    List<Integer> determineYearsToFetch() {
        Set<Integer> years = new TreeSet<>();
        int currentYear = LocalDate.now().getYear();
        int after = config.getSourceYearsAfter();

        // 当前年份
        years.add(currentYear);

        // yearsAfter 到 currentYear+2 的范围
        int start = Math.min(after, currentYear);
        int end = Math.max(after, currentYear) + 2;
        for (int y = start; y <= end; y++) {
            years.add(y);
        }

        // 磁盘缓存中的年份
        File dir = new File(config.getEffectiveDataDir());
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((d, name) -> name.matches("\\d{4}\\.json"));
            if (files != null) {
                for (File f : files) {
                    try {
                        years.add(Integer.parseInt(f.getName().replace(".json", "")));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        return new ArrayList<>(years);
    }

    // ──────────────── Data Dir Management ────────────────

    private boolean isDataFresh() {
        File metaFile = new File(config.getEffectiveDataDir(), META_FILE_NAME);
        if (!metaFile.exists()) {
            return false;
        }
        // 数据新鲜度：元数据文件存在且不超过 24 小时
        long ageMs = System.currentTimeMillis() - metaFile.lastModified();
        return ageMs < 24L * 60 * 60 * 1000;
    }

    /**
     * 从数据目录加载所有年份文件到内存。包级可见，便于测试。
     * <p>
     * 加载顺序：先加载 {@code {year}.json}，再依次叠加配置的补丁文件 {@code {year}-{suffix}.json}。
     */
    void loadFromDataDir() {
        File dir = new File(config.getEffectiveDataDir());
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] jsonFiles = dir.listFiles((d, name) ->
                name.matches("\\d{4}\\.json"));
        if (jsonFiles == null) return;

        for (File file : jsonFiles) {
            List<HolidayInfo> days = loadJsonFile(file);
            if (!days.isEmpty()) {
                cacheEntries(days);
                logger.info("Loaded {} holidays from {}", days.size(), file.getName());
            }

            // 依次加载配置的补丁文件
            String baseName = file.getName().replace(".json", "");
            for (String suffix : config.getExtSuffixes()) {
                File extFile = new File(dir, baseName + "-" + suffix + ".json");
                if (extFile.exists()) {
                    List<HolidayInfo> extDays = loadJsonFile(extFile);
                    if (!extDays.isEmpty()) {
                        cacheEntries(extDays);
                        logger.info("Loaded {} ext entries from {}", extDays.size(), extFile.getName());
                    }
                }
            }
        }
    }

    /**
     * 从 JSON 文件读取 days 列表。返回空列表而非 null。
     */
    private List<HolidayInfo> loadJsonFile(File file) {
        try {
            HolidayDataFile data = objectMapper.readValue(file, HolidayDataFile.class);
            List<HolidayInfo> days = data.getDays();
            return (days != null) ? days : Collections.emptyList();
        } catch (IOException e) {
            logger.warn("Failed to load data file {}: {}", file.getName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 将条目写入缓存（相同 date 会覆盖）。
     */
    private void cacheEntries(List<HolidayInfo> entries) {
        for (HolidayInfo info : entries) {
            holidayCache.put(info.getDate(), info);
            if (info.getYear() > 0) {
                loadedYears.add(info.getYear());
            }
        }
    }

    private void saveToDataDir() {
        try {
            File dir = new File(config.getEffectiveDataDir());
            if (!dir.exists() && !dir.mkdirs()) {
                logger.warn("Failed to create data directory: {}", config.getEffectiveDataDir());
                return;
            }

            // 按年份分组写入
            Map<Integer, List<HolidayInfo>> byYear = new TreeMap<>();
            for (HolidayInfo info : holidayCache.values()) {
                int year = info.getYear();
                if (year > 0) {
                    byYear.computeIfAbsent(year, k -> new ArrayList<>()).add(info);
                }
            }

            for (Map.Entry<Integer, List<HolidayInfo>> entry : byYear.entrySet()) {
                File yearFile = new File(dir, entry.getKey() + ".json");
                Map<String, Object> wrapper = new LinkedHashMap<>();
                List<HolidayInfo> sorted = new ArrayList<>(entry.getValue());
                sorted.sort(Comparator.comparing(HolidayInfo::getDate));
                wrapper.put("days", sorted);
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(yearFile, wrapper);
            }

            // 写入元数据
            File metaFile = new File(dir, META_FILE_NAME);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("lastUpdateTime", System.currentTimeMillis());
            meta.put("years", new ArrayList<>(byYear.keySet()));
            meta.put("totalEntries", holidayCache.size());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(metaFile, meta);

            logger.debug("Saved data to {}, {} years, {} entries",
                    config.getEffectiveDataDir(), byYear.size(), holidayCache.size());
        } catch (IOException e) {
            logger.warn("Failed to save data to directory: {}", e.getMessage());
        }
    }

    // ──────────────── Classpath Resource Loading ────────────────

    int extractYearFromUrl(String url) {
        Matcher m = Pattern.compile("(\\d{4})\\.json").matcher(url);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return -1;
    }

    List<HolidayInfo> loadFromClasspath(int year) {
        return loadClasspathResource(config.getClasspathResourcePrefix() + year + ".json");
    }

    /**
     * 从 classpath 依次加载配置的补丁文件 {@code {year}-{suffix}.json}。
     * 包级可见，便于测试。
     *
     * @return 所有补丁文件的合并结果（按配置顺序）
     */
    List<HolidayInfo> loadExtFromClasspath(int year) {
        List<HolidayInfo> all = new ArrayList<>();
        for (String suffix : config.getExtSuffixes()) {
            String resource = config.getClasspathResourcePrefix() + year + "-" + suffix + ".json";
            List<HolidayInfo> entries = loadClasspathResource(resource);
            if (!entries.isEmpty()) {
                logger.info("Loaded {} ext entries from classpath: {}", entries.size(), resource);
                all.addAll(entries);
            }
        }
        return all;
    }

    /**
     * 从 classpath 加载 JSON 资源文件。
     */
    private List<HolidayInfo> loadClasspathResource(String resourcePath) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                logger.debug("Classpath resource not found: {}", resourcePath);
                return Collections.emptyList();
            }
            HolidayDataFile data = objectMapper.readValue(is, HolidayDataFile.class);
            List<HolidayInfo> days = data.getDays();
            if (days == null || days.isEmpty()) {
                logger.debug("Classpath resource {} has no data", resourcePath);
                return Collections.emptyList();
            }
            return days;
        } catch (IOException e) {
            logger.warn("Failed to load classpath resource {}: {}", resourcePath, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ──────────────── HTTP Fetch ────────────────

    /**
     * 从指定 URL 拉取原始 JSON 字符串。包级可见，便于测试覆盖。
     */
    String fetchFromUrl(String url) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(url);
            request.setConfig(RequestConfig.custom()
                    .setConnectTimeout(Timeout.ofSeconds(10))
                    .setConnectionRequestTimeout(Timeout.ofSeconds(10))
                    .setResponseTimeout(Timeout.ofSeconds(30))
                    .build());
            return httpClient.execute(request, response -> {
                int status = response.getCode();
                if (status < 200 || status >= 300) {
                    throw new HolidayFetchException(
                            "HTTP request failed with status code: " + status);
                }
                return EntityUtils.toString(response.getEntity());
            });
        } catch (HolidayFetchException e) {
            throw e;
        } catch (IOException e) {
            throw new HolidayFetchException("Failed to fetch holiday data from: " + url, e);
        }
    }

    // ──────────────── JSON Parse ────────────────

    private List<HolidayInfo> parseHolidayData(String json) {
        try {
            HolidayDataFile data = objectMapper.readValue(json, HolidayDataFile.class);
            List<HolidayInfo> days = data.getDays();
            if (days == null) {
                throw new HolidayParseException(
                        "JSON does not contain expected 'days' field");
            }
            return days;
        } catch (IOException e) {
            throw new HolidayParseException("Failed to parse holiday JSON data", e);
        }
    }

    // ──────────────── Query ────────────────

    /**
     * 查询指定日期的节假日信息。
     * 有数据则返回缓存数据，无数据则使用策略计算。
     *
     * @param date 日期
     * @return 节假日信息，非休息日返回 null
     */
    public HolidayInfo getHoliday(LocalDate date) {
        HolidayInfo cached = holidayCache.get(date.toString());
        if (cached != null) {
            return cached;
        }
        // 无数据 → 策略计算（返回策略的详细条目）
        return strategy.getOffDayInfo(date);
    }

    /**
     * 获取指定年份的所有休息日列表。
     * 有数据则返回缓存数据，无数据则使用策略生成。
     *
     * @param year 年份
     * @return 休息日列表，按日期排序
     */
    public List<HolidayInfo> getHolidaysByYear(int year) {
        if (loadedYears.contains(year)) {
            String prefix = year + "-";
            List<HolidayInfo> result = new ArrayList<>();
            for (HolidayInfo info : holidayCache.values()) {
                if (info.getDate() != null && info.getDate().startsWith(prefix)) {
                    result.add(info);
                }
            }
            result.sort(Comparator.comparing(HolidayInfo::getDate));
            return result;
        }
        // 无数据 → 策略生成
        return strategy.generateOffDays(year);
    }

    /**
     * 判断指定年份是否有已加载的数据文件。
     */
    public boolean hasDataForYear(int year) {
        return loadedYears.contains(year);
    }

    // ──────────────── Working Day Calculation ────────────────

    /**
     * 判断指定日期是否为工作日。
     *
     * @param date 日期
     * @return true 表示工作日
     */
    public boolean isWorkDay(LocalDate date) {
        HolidayInfo info = getHoliday(date);
        return info == null; // null means not an off day → working day
    }

    /**
     * 计算从 startDate 起经过 n 个工作日后的日期。
     * <p>
     * n=0 时：若 startDate 为工作日返回 startDate 本身；
     * 若 startDate 为休息日，则返回下一个工作日。
     * <p>
     * 自动跳过所有休息日（数据文件 + 策略计算）。
     *
     * @param startDate 起始日期
     * @param n         工作日数（非负）
     * @return n 个工作日后的日期
     * @throws IllegalArgumentException 若 n 为负数
     */
    public LocalDate addWorkDays(LocalDate startDate, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be non-negative, got: " + n);
        }

        LocalDate current = startDate;
        // 如果起始日不是工作日，先推进到下一个工作日
        while (!isWorkDay(current)) {
            current = current.plusDays(1);
        }

        int count = 0;
        while (count < n) {
            current = current.plusDays(1);
            if (isWorkDay(current)) {
                count++;
            }
        }

        return current;
    }

    // ──────────────── Periodic Update ────────────────

    /**
     * 启动基于 cron 表达式的定期自动更新。
     * <p>
     * 仅当配置中 {@code holiday.refresh.enabled=true} 时生效。
     * 使用配置中的 {@code holiday.data.update.cron} 表达式计算下次执行时间，
     * 每次执行后自动重新调度下一次。
     */
    public void startPeriodicUpdate() {
        stopPeriodicUpdate();

        if (!config.isRefreshEnabled()) {
            logger.info("Periodic update is disabled (holiday.refresh.enabled=false), skipping");
            return;
        }

        String cronExpr = config.getRefreshCron();
        CronParser parser = new CronParser(
                CronDefinitionBuilder.instanceDefinitionFor(
                        com.cronutils.model.CronType.QUARTZ));
        Cron cron = parser.parse(cronExpr);
        ExecutionTime executionTime = ExecutionTime.forCron(cron);

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "workday-cn-java-updater");
            t.setDaemon(true);
            return t;
        });

        scheduleNext(executionTime);
        logger.info("Periodic update started, cron: {}", cronExpr);
    }

    /**
     * @return 配置的 cron 定时刷新是否启用
     */
    public boolean isRefreshEnabled() {
        return config.isRefreshEnabled();
    }

    private void scheduleNext(ExecutionTime executionTime) {
        ZonedDateTime now = ZonedDateTime.now();
        Optional<ZonedDateTime> next = executionTime.nextExecution(now);
        if (!next.isPresent()) {
            logger.warn("No next execution time for cron expression");
            return;
        }

        long delayMs = Duration.between(now, next.get()).toMillis();
        if (delayMs < 0) {
            delayMs = 0;
        }

        scheduledTask = scheduler.schedule(
                () -> {
                    try {
                        refresh();
                    } catch (Exception e) {
                        logger.error("Periodic update failed: {}", e.getMessage(), e);
                    }
                    scheduleNext(executionTime);
                },
                delayMs,
                TimeUnit.MILLISECONDS
        );

        logger.debug("Next periodic update scheduled at {}, delay: {}ms",
                next.get(), delayMs);
    }

    /**
     * 停止定期自动更新。
     */
    public void stopPeriodicUpdate() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
            logger.info("Periodic update stopped");
        }
    }

    // ──────────────── Configuration Access ────────────────

    public HolidayConfig getConfig() {
        return config;
    }

    public OffDayStrategy getStrategy() {
        return strategy;
    }

    public void addSourceUrl(String url) {
        config.getSourceUrls().add(url);
    }

    public List<String> getSourceUrls() {
        return Collections.unmodifiableList(config.getSourceUrls());
    }

    public int getCacheSize() {
        return holidayCache.size();
    }

    public int getCacheDuration() {
        return 0; // cron-based scheduling, no fixed interval
    }

    public void setCacheDuration(int minutes) {
        // Deprecated: use setRefreshCron on config instead
    }

    /**
     * @return 配置的 cron 表达式
     */
    public String getRefreshCron() {
        return config.getRefreshCron();
    }

    public String getCacheDir() {
        return config.getEffectiveDataDir();
    }

    public void setCacheDir(String path) {
        config.setDataDir(path);
    }
}
