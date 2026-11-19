package io.github.prmiguel.testng.live.json.internal;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaResourcesTest {

    @Test
    void shipsBothSchemaVersions() {
        assertNotNull(read("/schema/testng-live-report-v1.schema.json"), "v1 stays published for existing consumers");
        assertNotNull(read("/schema/testng-live-report-v2.schema.json"), "reporters emit v2");
    }

    @Test
    void schemaIdsDiffer() {
        assertNotEquals(read("/schema/testng-live-report-v1.schema.json"),
                read("/schema/testng-live-report-v2.schema.json"));
    }

    @Test
    void v2DocumentDeclaresThePerTestView() {
        String schema = read("/schema/testng-live-report-v2.schema.json");
        assertNotNull(schema);
        assertTrue(schema.contains("\"testSummary\""), "tests[] needs its own definition");
        assertTrue(schema.contains("\"configurationFailureSummary\""));
        assertTrue(schema.contains("\"document-events=none\"")
                        || schema.contains("Omitted when document-events=none"),
                "the optional events[] must be documented in the schema");
    }

    private static String read(String resource) {
        try (InputStream in = SchemaResourcesTest.class.getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
