package io.github.prmiguel.testng.live.json.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the consolidated report document from the live NDJSON stream.
 * <p>
 * The NDJSON is the single source of truth: event lines are spliced verbatim, so the
 * two files can never disagree. A stream that was never closed (a crashed JVM, or just
 * {@code flush()}) still yields a valid document without a summary section.
 */
public final class JsonDocumentAssembler {

    private static final String RUN_STARTED_PREFIX = "{\"type\":\"" + JsonEventRecordEncoder.RECORD_RUN_STARTED + "\",";
    private static final String RUN_SUMMARY_PREFIX = "{\"type\":\"" + JsonEventRecordEncoder.RECORD_RUN_SUMMARY + "\",";
    private static final Pattern SEQUENCE = Pattern.compile("\"sequence\":(\\d+)");

    private static final int DOCUMENT_SCHEMA_VERSION = 1;

    private JsonDocumentAssembler() {
    }

    /**
     * Writes the consolidated document next to the live stream.
     *
     * @param ndjsonPath path of the live stream
     * @param documentPath path of the document to write
     * @param sortEvents whether to order events by their publication sequence
     * @return the written document
     * @throws IOException if the stream cannot be read or the document cannot be written
     */
    public static String assemble(Path ndjsonPath, Path documentPath, boolean sortEvents) throws IOException {
        List<String> lines = Files.exists(ndjsonPath)
                ? Files.readAllLines(ndjsonPath, StandardCharsets.UTF_8)
                : List.of();
        return assemble(lines, documentPath, sortEvents);
    }

    /**
     * Writes the consolidated document from in-memory records.
     *
     * @param lines NDJSON records, in write order
     * @param documentPath path of the document to write
     * @param sortEvents whether to order events by their publication sequence
     * @return the written document
     * @throws IOException if the document cannot be written
     */
    public static String assemble(List<String> lines, Path documentPath, boolean sortEvents) throws IOException {
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

        StringBuilder out = new StringBuilder();
        out.append("{\"schemaVersion\":").append(DOCUMENT_SCHEMA_VERSION);
        if (run != null) {
            out.append(",\"run\":").append(run);
        }
        out.append(",\"events\":[").append(String.join(",", events)).append(']');
        if (summary != null) {
            out.append(",\"summary\":").append(summary);
        }
        out.append('}');

        String document = out.toString();
        Path parent = documentPath.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Files.writeString(documentPath, document, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        return document;
    }

    private static String stripType(String line, String prefix) {
        return "{" + line.substring(prefix.length());
    }

    private static long sequenceOf(String event) {
        Matcher matcher = SEQUENCE.matcher(event);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : Long.MAX_VALUE;
    }
}
