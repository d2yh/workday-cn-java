package com.github.d2yh.workday.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

/**
 * 节假日配置加载器。
 * <p>
 * 加载优先级（后者覆盖前者）：
 * <ol>
 *   <li>classpath 上的 {@code holiday-default.properties}（内置默认值）</li>
 *   <li>classpath 上的 {@code holiday-config.properties}（用户覆盖）</li>
 *   <li>外部文件路径（如通过构造函数指定）</li>
 * </ol>
 */
public class HolidayConfig {

    private static final Logger logger = LoggerFactory.getLogger(HolidayConfig.class);

    private static final String DEFAULT_CONFIG = "holiday-default.properties";
    private static final String USER_CONFIG = "holiday-config.properties";

    private String calendarRegion;
    private String dataDir;
    private boolean refreshEnabled;
    private String refreshCron;
    private List<String> sourceUrls;
    private int sourceYearsAfter;
    private String sourceUrlOrder;
    private List<String> extSuffixes;
    private String strategy;
    private String classpathResourcePrefix;

    private HolidayConfig() {
    }

    // ──────────────── Factory Methods ────────────────

    /**
     * 仅加载内置默认配置。
     */
    public static HolidayConfig loadDefaults() {
        Properties props = new Properties();
        loadFromClasspath(props, DEFAULT_CONFIG);
        return fromProperties(props);
    }

    /**
     * 加载默认配置 + classpath 上的用户覆盖文件。
     */
    public static HolidayConfig load() {
        Properties props = new Properties();
        loadFromClasspath(props, DEFAULT_CONFIG);
        loadFromClasspath(props, USER_CONFIG);
        return fromProperties(props);
    }

    /**
     * 加载默认配置 + classpath 用户覆盖 + 外部文件覆盖。
     *
     * @param externalPath 外部配置文件路径
     */
    public static HolidayConfig load(String externalPath) {
        Properties props = new Properties();
        loadFromClasspath(props, DEFAULT_CONFIG);
        loadFromClasspath(props, USER_CONFIG);
        loadFromFile(props, externalPath);
        return fromProperties(props);
    }

    // ──────────────── Property Loading ────────────────

    private static void loadFromClasspath(Properties props, String resource) {
        try (InputStream is = HolidayConfig.class.getClassLoader().getResourceAsStream(resource)) {
            if (is != null) {
                props.load(is);
                logger.debug("Loaded config from classpath: {}", resource);
            } else {
                logger.debug("Config resource not found on classpath: {}", resource);
            }
        } catch (IOException e) {
            logger.warn("Failed to load classpath config {}: {}", resource, e.getMessage());
        }
    }

    private static void loadFromFile(Properties props, String path) {
        File file = new File(path);
        if (!file.exists()) {
            logger.warn("External config file not found: {}", path);
            return;
        }
        try (InputStream is = new FileInputStream(file)) {
            props.load(is);
            logger.debug("Loaded config from file: {}", path);
        } catch (IOException e) {
            logger.warn("Failed to load external config {}: {}", path, e.getMessage());
        }
    }

    private static HolidayConfig fromProperties(Properties props) {
        HolidayConfig config = new HolidayConfig();

        // region: short form (e.g. "cn") → internal form "calendar-cn"
        String region = props.getProperty("data-store.region", "cn");
        config.calendarRegion = region.startsWith("calendar-") ? region : "calendar-" + region;

        config.dataDir = props.getProperty("data-store.dir", "./holiday-data");
        config.refreshEnabled = Boolean.parseBoolean(
                props.getProperty("data-file.update.enabled", "true"));
        config.refreshCron = props.getProperty("data-file.update.cron",
                "0 0 2 1,11,21 11,12 ?");
        config.strategy = props.getProperty("data-calc.fallback.strategy", "WEEKEND_ONLY");

        // classpath resource prefix: auto-derive from region if not explicitly set
        String prefix = props.getProperty("data-file.loader.classpath.prefix");
        if (prefix == null || prefix.isEmpty()) {
            config.classpathResourcePrefix = config.calendarRegion + "/";
        } else {
            config.classpathResourcePrefix = prefix;
        }

        String urls = props.getProperty("data-file.update.source.urls", "");
        config.sourceUrls = splitCsv(urls);

        config.sourceYearsAfter = parseInt(
                props.getProperty("data-file.update.source.years-after"), 2026);

        config.sourceUrlOrder = props.getProperty("data-file.update.source.url-order", "sequential");

        String exts = props.getProperty("data-file.loader.ext.suffixes", "ext");
        config.extSuffixes = splitCsv(exts);

        logger.info("HolidayConfig loaded: region={}, dataDir={}, effectiveDataDir={}, " +
                        "classpathPrefix={}, strategy={}, refreshEnabled={}, refreshCron={}, " +
                        "urlTemplates={}, yearsAfter={}, urlOrder={}, extSuffixes={}",
                config.calendarRegion, config.dataDir, config.getEffectiveDataDir(),
                config.classpathResourcePrefix, config.strategy,
                config.refreshEnabled, config.refreshCron, config.sourceUrls.size(),
                config.sourceYearsAfter, config.sourceUrlOrder, config.extSuffixes);

        return config;
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static List<String> splitCsv(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isEmpty()) {
            return result;
        }
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    // ──────────────── Getters / Setters ────────────────

    public String getCalendarRegion() {
        return calendarRegion;
    }

    public void setCalendarRegion(String calendarRegion) {
        this.calendarRegion = calendarRegion;
    }

    public String getDataDir() {
        return dataDir;
    }

    /**
     * 返回实际使用的数据目录路径，自动拼接 region 子目录。
     * <p>
     * 例如 dataDir=./holiday-data, region=calendar-cn
     * → 返回 ./holiday-data/calendar-cn
     */
    public String getEffectiveDataDir() {
        if (calendarRegion == null || calendarRegion.isEmpty()) {
            return dataDir;
        }
        return dataDir + File.separator + calendarRegion;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public boolean isRefreshEnabled() {
        return refreshEnabled;
    }

    public void setRefreshEnabled(boolean refreshEnabled) {
        this.refreshEnabled = refreshEnabled;
    }

    public String getRefreshCron() {
        return refreshCron;
    }

    public void setRefreshCron(String refreshCron) {
        this.refreshCron = refreshCron;
    }

    public List<String> getSourceUrls() {
        return sourceUrls;
    }

    public void setSourceUrls(List<String> sourceUrls) {
        this.sourceUrls = new ArrayList<>(sourceUrls);
    }

    /**
     * @return 年份边界值。系统会拉取当前年份、所有 ≥ 该值的年份、以及磁盘已缓存的年份。
     */
    public int getSourceYearsAfter() {
        return sourceYearsAfter;
    }

    public void setSourceYearsAfter(int sourceYearsAfter) {
        this.sourceYearsAfter = sourceYearsAfter;
    }

    /**
     * @return URL 尝试顺序："sequential"（依次尝试）或 "random"（随机顺序）
     */
    public String getSourceUrlOrder() {
        return sourceUrlOrder;
    }

    public void setSourceUrlOrder(String sourceUrlOrder) {
        this.sourceUrlOrder = sourceUrlOrder;
    }

    /**
     * @return 补丁文件后缀名列表（不含 {@code -} 和 {@code .json}），
     *         例如 ["ext", "company"] 对应 {@code 2025-ext.json} 和 {@code 2025-company.json}
     */
    public List<String> getExtSuffixes() {
        return extSuffixes;
    }

    public void setExtSuffixes(List<String> extSuffixes) {
        this.extSuffixes = new ArrayList<>(extSuffixes);
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getClasspathResourcePrefix() {
        return classpathResourcePrefix;
    }

    public void setClasspathResourcePrefix(String classpathResourcePrefix) {
        this.classpathResourcePrefix = classpathResourcePrefix;
    }
}
