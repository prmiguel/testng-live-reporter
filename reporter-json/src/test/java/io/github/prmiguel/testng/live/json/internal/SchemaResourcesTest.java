package io.github.prmiguel.testng.live.json.internal;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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

    private static String read(String resource) {
        try (InputStream in = SchemaResourcesTest.class.getResourceAsStream(resource)) {
            return in == null ? null : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }
}
