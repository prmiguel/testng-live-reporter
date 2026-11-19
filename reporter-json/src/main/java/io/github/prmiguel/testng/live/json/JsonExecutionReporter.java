package io.github.prmiguel.testng.live.json;

import io.github.prmiguel.testng.live.core.model.ExecutionEvent;
import io.github.prmiguel.testng.live.core.spi.ExecutionReporter;
import io.github.prmiguel.testng.live.json.internal.EventFilter;
import io.github.prmiguel.testng.live.json.internal.JsonDocumentAssembler;
import io.github.prmiguel.testng.live.json.internal.ReportFiles;
import io.github.prmiguel.testng.live.json.internal.RunTotals;
import io.github.prmiguel.testng.live.json.spi.EventRecordEncoder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Writes a live JSON execution stream plus a consolidated report document.
 * <p>
 * Core gives every reporter its own single-thread dispatcher, so {@link #onEvent} runs
 * off TestNG worker threads: the file writes below never slow a test down.
 * <p>
 * Output starts lazily on the first event because the SPI instantiates reporters without
 * arguments, and only then is the run identifier known. Each execution writes its own
 * file pair: {@code <name>.ndjson} is appended live, {@code <name>.json} is assembled
 * from it when the stream ends.
 * <p>
 * When the live stream is disabled but the document is not, event records are kept in
 * memory instead so the document can still be written; that only makes sense for small
 * suites and is therefore an explicit opt-in.
 */
public final class JsonExecutionReporter implements ExecutionReporter {

    private static final Logger LOGGER = System.getLogger(JsonExecutionReporter.class.getName());

    private final JsonReporterConfiguration configuration;
    private final EventRecordEncoder encoder;
    private final EventFilter filter;
    private final Clock clock;

    private final RunTotals totals = new RunTotals();
    private final Set<String> acceptedAttemptStates = new HashSet<>();
    private final List<String> bufferedRecords = new ArrayList<>();

    private String runId;
    private Instant startedAt;
    private Path ndjsonPath;
    private Path documentPath;
    private Writer writer;
    private boolean failed;
    private boolean closed;

    /**
     * Constructor used by {@link java.util.ServiceLoader} and TestNG. Resolves configuration
     * from system properties and environment variables.
     */
    public JsonExecutionReporter() {
        this(JsonReporterConfiguration.from(System.getProperties(), System.getenv()), Clock.systemUTC());
    }

    /**
     * Constructor for programmatic use and tests.
     *
     * @param configuration resolved configuration
     */
    public JsonExecutionReporter(JsonReporterConfiguration configuration) {
        this(configuration, Clock.systemUTC());
    }

    JsonExecutionReporter(JsonReporterConfiguration configuration, Clock clock) {
        this.configuration = configuration;
        this.encoder = EventRecordEncoder.create(configuration);
        this.filter = new EventFilter(configuration);
        this.clock = clock;
    }

    @Override
    public synchronized void onEvent(ExecutionEvent event) {
        if (!configuration.enabled() || failed) {
            return;
        }
        ensureOpen(event);
        if (!isOpen()) {
            return;
        }
        if (!filter.accepts(event)) {
            totals.eventFiltered();
            return;
        }
        if (!acceptedAttemptStates.add(event.executionId() + '\u0000' + event.state())) {
            totals.duplicateDropped();
            return;
        }
        totals.accept(event);
        write(encoder.event(event));
    }

    @Override
    public synchronized void flush() {
        if (writer == null) {
            return;
        }
        try {
            writer.flush();
        } catch (IOException e) {
            fail("flush", e);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (runId == null) {
            return;
        }
        Instant finishedAt = Instant.now(clock);
        write(encoder.summary(runId, startedAt, finishedAt, totals.snapshot()));
        if (writer != null) {
            try {
                writer.flush();
                writer.close();
            } catch (IOException e) {
                fail("close", e);
            }
            writer = null;
        }
        if (configuration.writeDocument()) {
            assembleDocument();
        }
        bufferedRecords.clear();
        acceptedAttemptStates.clear();
    }

    private void ensureOpen(ExecutionEvent event) {
        if (runId != null || failed) {
            return;
        }
        this.runId = event.runId();
        this.startedAt = Instant.now(clock);
        try {
            ReportFiles files = ReportFiles.resolve(configuration.outputDirectory(), configuration.fileName(),
                    runId, configuration.overwrite(), clock);
            this.ndjsonPath = files.ndjsonPath();
            this.documentPath = files.documentPath();
            if (configuration.writeNdjson()) {
                this.writer = Files.newBufferedWriter(ndjsonPath, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            }
        } catch (IOException e) {
            fail("open", e);
            return;
        }
        write(encoder.runStarted(runId, startedAt));
    }

    private boolean isOpen() {
        return runId != null && (writer != null || !configuration.writeNdjson());
    }

    private void write(String record) {
        if (writer == null) {
            bufferedRecords.add(record);
            return;
        }
        try {
            writer.write(record);
            writer.write('\n');
            if (configuration.flushEachEvent()) {
                writer.flush();
            }
        } catch (IOException e) {
            fail("write", e);
        }
    }

    private void assembleDocument() {
        try {
            if (writer != null || configuration.writeNdjson()) {
                JsonDocumentAssembler.assemble(ndjsonPath, documentPath, configuration.sortEvents());
            } else {
                JsonDocumentAssembler.assemble(bufferedRecords, documentPath, configuration.sortEvents());
            }
        } catch (IOException | RuntimeException e) {
            totals.writeFailed();
            LOGGER.log(Level.ERROR, "Could not assemble {0}: {1}", documentPath, e.getMessage());
        }
    }

    private void fail(String operation, IOException e) {
        if (!failed) {
            totals.writeFailed();
            LOGGER.log(Level.ERROR, "Could not {0} JSON report under {1}: {2}",
                    operation, configuration.outputDirectory(), e.getMessage());
        }
        failed = true;
    }

    /**
     * Returns the configuration in use.
     */
    public JsonReporterConfiguration configuration() {
        return configuration;
    }

    /**
     * Returns the path of the live stream for the execution written so far, if any.
     */
    public synchronized Path ndjsonPath() {
        return configuration.writeNdjson() ? ndjsonPath : null;
    }

    /**
     * Returns the path of the consolidated document for the execution written so far, if any.
     */
    public synchronized Path documentPath() {
        return configuration.writeDocument() ? documentPath : null;
    }

    /**
     * Builds a configuration from system properties and environment variables.
     *
     * @return configuration
     */
    public static JsonReporterConfiguration resolveConfiguration() {
        return JsonReporterConfiguration.from(System.getProperties(), System.getenv());
    }

    /**
     * Builds a configuration from the given sources, mainly for tests.
     *
     * @param systemProperties system properties
     * @param environment      environment variables
     * @return configuration
     */
    public static JsonReporterConfiguration resolveConfiguration(Properties systemProperties,
                                                                 Map<String, String> environment) {
        return JsonReporterConfiguration.from(systemProperties, environment);
    }
}
