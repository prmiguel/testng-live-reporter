package io.github.prmiguel.testng.live.json.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Resolves the on-disk location of a single execution's output files.
 */
public final class ReportFiles {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    public static final String NDJSON_EXTENSION = ".ndjson";
    public static final String DOCUMENT_EXTENSION = ".json";

    private final Path basePath;

    private ReportFiles(Path basePath) {
        this.basePath = basePath;
    }

    /**
     * Resolves a collision-free base path for the given run.
     *
     * @param configuredDirectory configured output directory, relative paths resolved against the work dir
     * @param fileName            configured base name, or {@code null} for the default
     * @param runId               run identifier
     * @param overwrite           whether an existing file may be replaced
     * @return resolved files
     * @throws IOException if the output directory cannot be created
     */
    public static ReportFiles resolve(Path configuredDirectory, String fileName, String runId,
                                      boolean overwrite, Clock clock) throws IOException {
        Path directory = configuredDirectory.isAbsolute()
                ? configuredDirectory
                : Path.of(System.getProperty("user.dir", ".")).resolve(configuredDirectory);
        Files.createDirectories(directory);

        String baseName = fileName != null ? fileName : defaultFileName(runId, clock);
        Path candidate = directory.resolve(baseName);
        if (!overwrite) {
            candidate = firstFree(candidate);
        }
        return new ReportFiles(candidate);
    }

    public Path ndjsonPath() {
        return Path.of(basePath + NDJSON_EXTENSION);
    }

    public Path documentPath() {
        return Path.of(basePath + DOCUMENT_EXTENSION);
    }

    private static String defaultFileName(String runId, Clock clock) {
        return TIMESTAMP.format(Instant.now(clock)) + "-" + runId;
    }

    /**
     * Appends {@code -1}, {@code -2}, ... until no sibling file of either format exists.
     */
    private static Path firstFree(Path candidate) {
        Path parent = candidate.getParent();
        String name = candidate.getFileName().toString();
        Path current = candidate;
        int suffix = 1;
        while (exists(current)) {
            current = parent.resolve(name + "-" + suffix++);
        }
        return current;
    }

    private static boolean exists(Path basePath) {
        Path ndjsonPath = Path.of(basePath + NDJSON_EXTENSION);
        Path documentPath = Path.of(basePath + DOCUMENT_EXTENSION);
        return Files.exists(ndjsonPath) || Files.exists(documentPath);
    }
}
