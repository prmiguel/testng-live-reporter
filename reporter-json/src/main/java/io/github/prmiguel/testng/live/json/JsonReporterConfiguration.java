package io.github.prmiguel.testng.live.json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Resolved, immutable configuration for {@link JsonExecutionReporter}.
 * <p>
 * Values are resolved without throwing: an unparsable value logs a warning and falls
 * back to its default so a bad property never breaks a test run.
 * <p>
 * Precedence, highest first: explicit properties (system properties), environment
 * variables, an optional properties file named by {@link #CONFIG_FILE_PROPERTY}, defaults.
 */
public record JsonReporterConfiguration(
        boolean enabled,
        Path outputDirectory,
        String fileName,
        boolean overwrite,
        boolean writeNdjson,
        boolean writeDocument,
        boolean flushEachEvent,
        List<String> includeClasses,
        List<String> excludeClasses,
        List<String> includeMethods,
        List<String> excludeMethods,
        List<String> includeStatuses,
        int maxStackTraceChars,
        boolean sortEvents,
        boolean includeAttributes,
        Duration shutdownTimeout
) {

    public static final String CONFIG_FILE_PROPERTY = "testng.live.json.config";

    public static final String ENABLED_PROPERTY = "testng.live.json.enabled";
    public static final String OUTPUT_DIR_PROPERTY = "testng.live.json.output-dir";
    public static final String FILE_NAME_PROPERTY = "testng.live.json.file-name";
    public static final String OVERWRITE_PROPERTY = "testng.live.json.overwrite";
    public static final String NDJSON_PROPERTY = "testng.live.json.ndjson";
    public static final String DOCUMENT_PROPERTY = "testng.live.json.document";
    public static final String FLUSH_EACH_EVENT_PROPERTY = "testng.live.json.flush-each-event";
    public static final String INCLUDE_CLASSES_PROPERTY = "testng.live.json.include-classes";
    public static final String EXCLUDE_CLASSES_PROPERTY = "testng.live.json.exclude-classes";
    public static final String INCLUDE_METHODS_PROPERTY = "testng.live.json.include-methods";
    public static final String EXCLUDE_METHODS_PROPERTY = "testng.live.json.exclude-methods";
    public static final String INCLUDE_STATUSES_PROPERTY = "testng.live.json.include-statuses";
    public static final String MAX_STACK_TRACE_CHARS_PROPERTY = "testng.live.json.max-stack-trace-chars";
    public static final String SORT_EVENTS_PROPERTY = "testng.live.json.sort-events";
    public static final String INCLUDE_ATTRIBUTES_PROPERTY = "testng.live.json.include-attributes";
    public static final String SHUTDOWN_TIMEOUT_PROPERTY = "testng.live.json.shutdown-timeout-seconds";

    public static final String DEFAULT_OUTPUT_DIRECTORY = "target/testng-live-reporter";

    public JsonReporterConfiguration {
        includeClasses = List.copyOf(includeClasses);
        excludeClasses = List.copyOf(excludeClasses);
        includeMethods = List.copyOf(includeMethods);
        excludeMethods = List.copyOf(excludeMethods);
        includeStatuses = List.copyOf(includeStatuses);
    }

    /**
     * Resolves configuration from system properties and environment variables.
     *
     * @param systemProperties system properties
     * @param environment      environment variables
     * @return resolved configuration
     */
    public static JsonReporterConfiguration from(Properties systemProperties, Map<String, String> environment) {
        Map<String, String> values = new LinkedHashMap<>();
        String configFile = systemProperties.getProperty(CONFIG_FILE_PROPERTY);
        if (configFile != null && !configFile.isBlank()) {
            values.putAll(loadPropertiesFile(Path.of(configFile.trim())));
        }
        environment.forEach((key, value) -> {
            String property = propertyForEnvironmentVariable(key);
            if (property != null) {
                values.put(property, value);
            }
        });
        for (String name : systemProperties.stringPropertyNames()) {
            String value = systemProperties.getProperty(name);
            if (value != null) {
                values.put(name, value);
            }
        }
        return from(values);
    }

    /**
     * Resolves configuration from an already-merged property map.
     *
     * @param values resolved property values
     * @return resolved configuration
     */
    public static JsonReporterConfiguration from(Map<String, String> values) {
        Map<String, String> properties = values == null ? Map.of() : values;
        Path outputDirectory = Path.of(trimmed(properties.get(OUTPUT_DIR_PROPERTY), DEFAULT_OUTPUT_DIRECTORY));
        String fileName = emptyToNull(trimmed(properties.get(FILE_NAME_PROPERTY), null));
        return new JsonReporterConfiguration(
                booleanValue(properties, ENABLED_PROPERTY, true),
                outputDirectory,
                fileName,
                booleanValue(properties, OVERWRITE_PROPERTY, false),
                booleanValue(properties, NDJSON_PROPERTY, true),
                booleanValue(properties, DOCUMENT_PROPERTY, true),
                booleanValue(properties, FLUSH_EACH_EVENT_PROPERTY, true),
                splitList(properties.get(INCLUDE_CLASSES_PROPERTY)),
                splitList(properties.get(EXCLUDE_CLASSES_PROPERTY)),
                splitList(properties.get(INCLUDE_METHODS_PROPERTY)),
                splitList(properties.get(EXCLUDE_METHODS_PROPERTY)),
                statusList(properties.get(INCLUDE_STATUSES_PROPERTY)),
                intValue(properties, MAX_STACK_TRACE_CHARS_PROPERTY, 0),
                booleanValue(properties, SORT_EVENTS_PROPERTY, false),
                booleanValue(properties, INCLUDE_ATTRIBUTES_PROPERTY, true),
                Duration.ofSeconds(Math.max(1, intValue(properties, SHUTDOWN_TIMEOUT_PROPERTY, 30)))
        );
    }

    /**
     * Returns the environment variable name paired with the given property, if any.
     */
    static String environmentVariableFor(String property) {
        return switch (property) {
            case ENABLED_PROPERTY -> "TESTNG_LIVE_JSON_ENABLED";
            case OUTPUT_DIR_PROPERTY -> "TESTNG_LIVE_JSON_OUTPUT_DIR";
            case FILE_NAME_PROPERTY -> "TESTNG_LIVE_JSON_FILE_NAME";
            case OVERWRITE_PROPERTY -> "TESTNG_LIVE_JSON_OVERWRITE";
            case NDJSON_PROPERTY -> "TESTNG_LIVE_JSON_NDJSON";
            case DOCUMENT_PROPERTY -> "TESTNG_LIVE_JSON_DOCUMENT";
            case FLUSH_EACH_EVENT_PROPERTY -> "TESTNG_LIVE_JSON_FLUSH_EACH_EVENT";
            case INCLUDE_CLASSES_PROPERTY -> "TESTNG_LIVE_JSON_INCLUDE_CLASSES";
            case EXCLUDE_CLASSES_PROPERTY -> "TESTNG_LIVE_JSON_EXCLUDE_CLASSES";
            case INCLUDE_METHODS_PROPERTY -> "TESTNG_LIVE_JSON_INCLUDE_METHODS";
            case EXCLUDE_METHODS_PROPERTY -> "TESTNG_LIVE_JSON_EXCLUDE_METHODS";
            case INCLUDE_STATUSES_PROPERTY -> "TESTNG_LIVE_JSON_INCLUDE_STATUSES";
            case MAX_STACK_TRACE_CHARS_PROPERTY -> "TESTNG_LIVE_JSON_MAX_STACK_TRACE_CHARS";
            case SORT_EVENTS_PROPERTY -> "TESTNG_LIVE_JSON_SORT_EVENTS";
            case INCLUDE_ATTRIBUTES_PROPERTY -> "TESTNG_LIVE_JSON_INCLUDE_ATTRIBUTES";
            case SHUTDOWN_TIMEOUT_PROPERTY -> "TESTNG_LIVE_JSON_SHUTDOWN_TIMEOUT_SECONDS";
            default -> null;
        };
    }

    private static String propertyForEnvironmentVariable(String environmentVariable) {
        for (String property : List.of(ENABLED_PROPERTY, OUTPUT_DIR_PROPERTY, FILE_NAME_PROPERTY,
                OVERWRITE_PROPERTY, NDJSON_PROPERTY, DOCUMENT_PROPERTY, FLUSH_EACH_EVENT_PROPERTY,
                INCLUDE_CLASSES_PROPERTY, EXCLUDE_CLASSES_PROPERTY, INCLUDE_METHODS_PROPERTY,
                EXCLUDE_METHODS_PROPERTY, INCLUDE_STATUSES_PROPERTY, MAX_STACK_TRACE_CHARS_PROPERTY,
                SORT_EVENTS_PROPERTY, INCLUDE_ATTRIBUTES_PROPERTY, SHUTDOWN_TIMEOUT_PROPERTY)) {
            if (environmentVariableFor(property).equals(environmentVariable)) {
                return property;
            }
        }
        return null;
    }

    private static Map<String, String> loadPropertiesFile(Path path) {
        Map<String, String> loaded = new LinkedHashMap<>();
        if (!Files.isReadable(path)) {
            return loaded;
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException e) {
            return loaded;
        }
        properties.stringPropertyNames().forEach(name -> loaded.put(name, properties.getProperty(name)));
        return loaded;
    }

    private static String trimmed(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean booleanValue(Map<String, String> properties, String property, boolean defaultValue) {
        String value = properties.get(property);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String normalized = value.trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        return defaultValue;
    }

    private static int intValue(Map<String, String> properties, String property, int defaultValue) {
        String value = properties.get(property);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static List<String> statusList(String value) {
        List<String> statuses = new ArrayList<>();
        for (String token : splitList(value)) {
            statuses.add(token.toUpperCase());
        }
        return Collections.unmodifiableList(statuses);
    }

    private static List<String> splitList(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .toList();
    }
}
