package io.github.prmiguel.testng.live.json.internal;

import io.github.prmiguel.testng.live.core.internal.ExecutionEventFactory;
import io.github.prmiguel.testng.live.core.model.CanonicalParameter;
import io.github.prmiguel.testng.live.core.model.ExecutionEvent;
import io.github.prmiguel.testng.live.core.model.FailureInfo;
import io.github.prmiguel.testng.live.core.model.TestIdentity;
import io.github.prmiguel.testng.live.json.spi.EventRecordEncoder;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Properties;

/**
 * Encodes reporter records as compact single-line JSON.
 * <p>
 * Field order is fixed and deterministic; values that are absent in the event model are
 * omitted rather than written as {@code null}.
 */
public final class JsonEventRecordEncoder implements EventRecordEncoder {

    public static final String RECORD_RUN_STARTED = "run.started";
    public static final String RECORD_TEST_EVENT = "test.event";
    public static final String RECORD_RUN_SUMMARY = "run.summary";

    public static final int RECORD_SCHEMA_VERSION = 2;

    public static final String REPORTER_NAME = "testng-live-reporter-json";

    private final int maxStackTraceChars;
    private final boolean includeAttributes;

    public JsonEventRecordEncoder(int maxStackTraceChars, boolean includeAttributes) {
        this.maxStackTraceChars = maxStackTraceChars;
        this.includeAttributes = includeAttributes;
    }

    @Override
    public String runStarted(String runId, Instant startedAt) {
        JsonTextWriter writer = new JsonTextWriter();
        writer.beginObject()
                .name("type").value(RECORD_RUN_STARTED)
                .name("schemaVersion").value(RECORD_SCHEMA_VERSION)
                .name("runId").value(runId)
                .name("startedAt").value(timestamp(startedAt))
                .name("reporter").beginObject()
                .name("name").value(REPORTER_NAME)
                .name("version").value(reporterVersion())
                .endObject()
                .endObject();
        return writer.toString();
    }

    @Override
    public String event(ExecutionEvent event) {
        JsonTextWriter writer = new JsonTextWriter();
        writer.beginObject()
                .name("type").value(RECORD_TEST_EVENT)
                .name("recordSchemaVersion").value(RECORD_SCHEMA_VERSION)
                .name("eventSchemaVersion").value(event.schemaVersion())
                .name("sequence").value(event.sequence())
                .name("runId").value(event.runId())
                .name("executionId").value(event.executionId())
                .name("state").value(event.state().name())
                .name("status").value(event.status().name())
                .name("timestamp").value(timestamp(event.timestamp()));
        if (event.duration() != null) {
            writer.name("durationMs").value(event.duration().toMillis());
        }
        writer.name("test");
        writeTestIdentity(writer, event.testIdentity());
        writer.name("suiteName").value(event.suiteName())
                .name("testName").value(event.testName())
                .name("threadId").value(event.threadId())
                .name("threadName").value(event.threadName())
                .name("invocationIndex").value(event.invocationIndex())
                .name("retryAttempt").value(event.retryAttempt());
        FailureInfo failure = event.failure();
        if (failure != null) {
            writer.name("failure");
            writeFailure(writer, failure);
        }
        if (includeAttributes || event.attributes().containsKey(ExecutionEventFactory.PHASE_ATTRIBUTE)) {
            // The phase marker is never optional: without it a configuration failure is
            // indistinguishable from an ordinary test failure.
            writer.name("attributes");
            writeAttributes(writer, event.attributes());
        }
        writer.endObject();
        return writer.toString();
    }

    @Override
    public String summary(String runId, Instant startedAt, Instant finishedAt, RunTotals.Snapshot totals) {
        JsonTextWriter writer = new JsonTextWriter();
        writer.beginObject()
                .name("type").value(RECORD_RUN_SUMMARY)
                .name("schemaVersion").value(RECORD_SCHEMA_VERSION)
                .name("runId").value(runId)
                .name("startedAt").value(timestamp(startedAt))
                .name("finishedAt").value(timestamp(finishedAt))
                .name("durationMs").value(finishedAt.toEpochMilli() - startedAt.toEpochMilli())
                .name("totals").beginObject()
                .name("events").value(totals.events())
                .name("tests").value(totals.tests())
                .name("passed").value(totals.passed())
                .name("failed").value(totals.failed())
                .name("skipped").value(totals.skipped())
                .name("ignored").value(totals.ignored())
                .name("timeout").value(totals.timeout())
                .name("interrupted").value(totals.interrupted())
                .name("error").value(totals.error())
                .name("undetermined").value(totals.undetermined())
                .name("configurationFailures").value(totals.configurationFailures())
                .name("droppedDuplicates").value(totals.droppedDuplicates())
                .name("filtered").value(totals.filtered())
                .name("writeErrors").value(totals.writeErrors())
                .endObject()
                .endObject();
        return writer.toString();
    }

    private void writeTestIdentity(JsonTextWriter writer, TestIdentity identity) {
        writer.beginObject()
                .name("testId").value(identity.testId())
                .name("hash").value(identity.hash())
                .name("className").value(identity.className())
                .name("methodName").value(identity.methodName())
                .name("readableKey").value(identity.readableKey())
                .name("parameters").beginArray();
        for (CanonicalParameter parameter : identity.parameters()) {
            writer.beginObject()
                    .name("declaredType").value(parameter.declaredType())
                    .name("runtimeType").value(parameter.runtimeType())
                    .name("value").value(parameter.value())
                    .endObject();
        }
        writer.endArray();
        writeAnnotations(writer, identity.annotations());
        writer.endObject();
    }

    /**
     * Writes the {@code @Test} metadata, omitting the whole object when nothing is set. Shared by
     * the event encoder and the document assembler so both files describe a test identically.
     *
     * @param writer   target writer
     * @param metadata captured annotations, may be null
     */
    static void writeAnnotations(JsonTextWriter writer, TestIdentity.Annotations metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        writer.name("annotations").beginObject();
        if (!metadata.groups().isEmpty()) {
            writer.name("groups").beginArray();
            metadata.groups().forEach(writer::value);
            writer.endArray();
        }
        if (!metadata.dependsOnGroups().isEmpty()) {
            writer.name("dependsOnGroups").beginArray();
            metadata.dependsOnGroups().forEach(writer::value);
            writer.endArray();
        }
        if (metadata.enabled() != null) {
            writer.name("enabled").value(metadata.enabled());
        }
        if (metadata.description() != null) {
            writer.name("description").value(metadata.description());
        }
        if (metadata.priority() != null) {
            writer.name("priority").value(metadata.priority().longValue());
        }
        if (metadata.timeOut() != null) {
            writer.name("timeOut").value(metadata.timeOut());
        }
        if (metadata.invocationCount() != null) {
            writer.name("invocationCount").value(metadata.invocationCount().longValue());
        }
        if (metadata.retryAnalyzer() != null) {
            writer.name("retryAnalyzer").value(metadata.retryAnalyzer());
        }
        if (metadata.dataProvider() != null) {
            writer.name("dataProvider").value(metadata.dataProvider());
        }
        writer.endObject();
    }

    private void writeFailure(JsonTextWriter writer, FailureInfo failure) {
        String stackTrace = failure.stackTrace();
        boolean truncated = maxStackTraceChars > 0 && stackTrace.length() > maxStackTraceChars;
        if (truncated) {
            stackTrace = stackTrace.substring(0, maxStackTraceChars);
        }
        writer.beginObject()
                .name("type").value(failure.type())
                .name("message").value(failure.message())
                .name("stackTrace").value(stackTrace);
        if (truncated) {
            writer.name("truncated").value(true);
        }
        writer.endObject();
    }

    private void writeAttributes(JsonTextWriter writer, Map<String, String> attributes) {
        writer.beginObject();
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            writer.name(entry.getKey()).value(entry.getValue());
        }
        writer.endObject();
    }

    private static String timestamp(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MILLIS).toString();
    }

    private static String reporterVersion() {
        try (InputStream in = JsonEventRecordEncoder.class.getResourceAsStream(
                "/META-INF/testng-live-reporter-json.properties")) {
            if (in != null) {
                Properties properties = new Properties();
                properties.load(in);
                String version = properties.getProperty("reporter.version");
                if (version != null && !version.isBlank() && !version.contains("${")) {
                    return version;
                }
            }
        } catch (IOException e) {
            // fall through to the unknown marker
        }
        return "unknown";
    }
}
