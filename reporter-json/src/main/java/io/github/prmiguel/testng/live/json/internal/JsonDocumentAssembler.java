package io.github.prmiguel.testng.live.json.internal;

import io.github.prmiguel.testng.live.core.model.CanonicalParameter;
import io.github.prmiguel.testng.live.core.model.ExecutionEvent;
import io.github.prmiguel.testng.live.core.model.FailureInfo;
import io.github.prmiguel.testng.live.core.model.TestIdentity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the consolidated report document from the live NDJSON stream.
 * <p>
 * The NDJSON is the single source of truth: {@code events[]} holds the stream's own lines,
 * so the two files can never disagree. A stream that was never closed (a crashed JVM, or
 * just {@code flush()}) still yields a valid document without a summary section.
 * <p>
 * {@code tests[]} and {@code configurationFailures[]} come from {@link AttemptIndex}, which
 * is fed on the same dispatcher thread as the stream, so their counts always reconcile with
 * the summary totals.
 */
public final class JsonDocumentAssembler {

    private static final String RUN_STARTED_PREFIX = "{\"type\":\"" + JsonEventRecordEncoder.RECORD_RUN_STARTED + "\",";
    private static final String RUN_SUMMARY_PREFIX = "{\"type\":\"" + JsonEventRecordEncoder.RECORD_RUN_SUMMARY + "\",";
    private static final Pattern SEQUENCE = Pattern.compile("\"sequence\":(\\d+)");

    private static final int DOCUMENT_SCHEMA_VERSION = 2;

    private final AttemptIndex index;
    private final int maxStackTraceChars;

    /**
     * @param index               per-attempt state collected while writing the stream
     * @param maxStackTraceChars  truncation limit applied to embedded stack traces
     */
    public JsonDocumentAssembler(AttemptIndex index, int maxStackTraceChars) {
        this.index = index;
        this.maxStackTraceChars = maxStackTraceChars;
    }

    /**
     * Writes the consolidated document next to the live stream.
     *
     * @param ndjsonPath    path of the live stream
     * @param documentPath  path of the document to write
     * @param sortEvents    whether to order {@code events[]} by publication sequence
     * @param includeEvents whether to embed the raw stream as {@code events[]}
     * @return the written document
     * @throws IOException if the stream cannot be read or the document cannot be written
     */
    public String assemble(Path ndjsonPath, Path documentPath, boolean sortEvents, boolean includeEvents)
            throws IOException {
        List<String> lines = Files.exists(ndjsonPath)
                ? Files.readAllLines(ndjsonPath, StandardCharsets.UTF_8)
                : List.of();
        return assemble(lines, documentPath, sortEvents, includeEvents);
    }

    /**
     * Writes the consolidated document from in-memory records.
     *
     * @param lines         NDJSON records, in write order
     * @param documentPath  path of the document to write
     * @param sortEvents    whether to order {@code events[]} by publication sequence
     * @param includeEvents whether to embed the raw stream as {@code events[]}
     * @return the written document
     * @throws IOException if the document cannot be written
     */
    public String assemble(List<String> lines, Path documentPath, boolean sortEvents, boolean includeEvents)
            throws IOException {
        String run = null;
        String summary = null;
        List<String> events = new ArrayList<>(lines.size());

        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith(RUN_STARTED_PREFIX)) {
                run = stripType(line, RUN_STARTED_PREFIX);
            } else if (line.startsWith(RUN_SUMMARY_PREFIX)) {
                summary = stripType(line, RUN_SUMMARY_PREFIX);
            } else {
                events.add(line);
            }
        }

        if (sortEvents) {
            events.sort((left, right) -> Long.compare(sequenceOf(left), sequenceOf(right)));
        }

        JsonTextWriter writer = new JsonTextWriter();
        writer.beginObject()
                .name("schemaVersion").value(DOCUMENT_SCHEMA_VERSION);
        if (run != null) {
            writer.name("run").rawValue(run);
        }
        if (includeEvents) {
            writer.name("events").beginArray();
            for (String event : events) {
                writer.rawValue(event);
            }
            writer.endArray();
        }
        writer.name("tests").beginArray();
        for (ExecutionEvent test : index.tests()) {
            writeTest(writer, test);
        }
        writer.endArray();
        writer.name("configurationFailures").beginArray();
        for (ExecutionEvent failure : index.configurationFailures()) {
            writeConfigurationFailure(writer, failure);
        }
        writer.endArray();
        if (summary != null) {
            writer.name("summary").rawValue(summary);
        }
        writer.endObject();

        String document = writer.toString();
        Path parent = documentPath.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Files.writeString(documentPath, document, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        return document;
    }

    private void writeTest(JsonTextWriter writer, ExecutionEvent test) {
        writer.beginObject();
        writeIdentity(writer, test.testIdentity());
        writer.name("status").value(test.status().name());
        if (test.duration() != null) {
            writer.name("durationMs").value(test.duration().toMillis());
        }
        Instant startedAt = index.startedAt(test.executionId());
        if (startedAt != null) {
            writer.name("startedAt").value(timestamp(startedAt));
        }
        writer.name("finishedAt").value(timestamp(test.timestamp()));
        writer.name("suiteName").value(test.suiteName())
                .name("testName").value(test.testName())
                .name("threadName").value(test.threadName())
                .name("invocationIndex").value(test.invocationIndex())
                .name("retryAttempt").value(test.retryAttempt());
        writeFailure(writer, test.failure());
        writer.endObject();
    }

    private void writeConfigurationFailure(JsonTextWriter writer, ExecutionEvent failure) {
        writer.beginObject();
        writeIdentity(writer, failure.testIdentity());
        writer.name("configurationKind")
                .value(failure.attributes().getOrDefault("configurationKind", "configuration"))
                .name("status").value(failure.status().name())
                .name("timestamp").value(timestamp(failure.timestamp()));
        if (failure.duration() != null) {
            writer.name("durationMs").value(failure.duration().toMillis());
        }
        writer.name("suiteName").value(failure.suiteName())
                .name("testName").value(failure.testName());
        writeFailure(writer, failure.failure());
        writer.endObject();
    }

    private void writeIdentity(JsonTextWriter writer, TestIdentity identity) {
        writer.name("testId").value(identity.testId())
                .name("readableKey").value(identity.readableKey())
                .name("className").value(identity.className())
                .name("methodName").value(identity.methodName());
        if (!identity.parameters().isEmpty()) {
            writer.name("parameters").beginArray();
            for (CanonicalParameter parameter : identity.parameters()) {
                writer.beginObject()
                        .name("declaredType").value(parameter.declaredType())
                        .name("runtimeType").value(parameter.runtimeType())
                        .name("value").value(parameter.value())
                        .endObject();
            }
            writer.endArray();
        }
        JsonEventRecordEncoder.writeAnnotations(writer, identity.annotations());
    }

    private void writeFailure(JsonTextWriter writer, FailureInfo failure) {
        if (failure == null) {
            return;
        }
        String stackTrace = failure.stackTrace();
        boolean truncated = maxStackTraceChars > 0 && stackTrace.length() > maxStackTraceChars;
        if (truncated) {
            stackTrace = stackTrace.substring(0, maxStackTraceChars);
        }
        writer.name("failure").beginObject()
                .name("type").value(failure.type())
                .name("message").value(failure.message())
                .name("stackTrace").value(stackTrace);
        if (truncated) {
            writer.name("truncated").value(true);
        }
        writer.endObject();
    }

    private static String stripType(String line, String prefix) {
        return "{" + line.substring(prefix.length());
    }

    private static long sequenceOf(String event) {
        Matcher matcher = SEQUENCE.matcher(event);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : Long.MAX_VALUE;
    }

    private static String timestamp(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MILLIS).toString();
    }
}
