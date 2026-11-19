package io.github.prmiguel.testng.live.json.internal;

import io.github.prmiguel.testng.live.core.model.TestIdentity;
import io.github.prmiguel.testng.live.json.TestJsonParser;
import io.github.prmiguel.testng.live.json.TestEvents;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationsEncodingTest {

    private static TestIdentity identified(TestIdentity.Annotations metadata) {
        String hash = "0123456789abcdef";
        return new TestIdentity("com.acme.Sample", "runs", List.of(), "com.acme.Sample.runs[]",
                hash, "com.acme.Sample.runs[]#" + hash, metadata);
    }

    private static Map<String, Object> encodedTest(TestIdentity identity) {
        JsonEventRecordEncoder encoder = new JsonEventRecordEncoder(0, true);
        Map<String, Object> parsed = TestJsonParser.parseObject(
                encoder.event(TestEvents.started("run-1", "exec-1", identity)));
        return (Map<String, Object>) parsed.get("test");
    }

    @Test
    void omitsTheObjectEntirelyWhenThereIsNoMetadata() {
        Map<String, Object> test = encodedTest(identified(TestIdentity.Annotations.EMPTY));

        assertNull(test.get("annotations"),
                "a plain @Test must not emit an empty annotations object");
    }

    @Test
    void writesOnlyTheAttributesThatCarryAValue() {
        TestIdentity.Annotations metadata = new TestIdentity.Annotations(
                List.of("slow", "db"), List.of("base"), Boolean.FALSE,
                "covers login", 2, 500L, 3, "com.acme.Retry", "rows");

        Map<String, Object> annotations = (Map<String, Object>) encodedTest(identified(metadata)).get("annotations");

        assertEquals(List.of("slow", "db"), annotations.get("groups"));
        assertEquals(List.of("base"), annotations.get("dependsOnGroups"));
        assertEquals(Boolean.FALSE, annotations.get("enabled"));
        assertEquals("covers login", annotations.get("description"));
        assertEquals(2L, annotations.get("priority"));
        assertEquals(500L, annotations.get("timeOut"));
        assertEquals(3L, annotations.get("invocationCount"));
        assertEquals("com.acme.Retry", annotations.get("retryAnalyzer"));
        assertEquals("rows", annotations.get("dataProvider"));
    }

    @Test
    void keepsTheIdentityFieldsIntact() {
        Map<String, Object> test = encodedTest(identified(new TestIdentity.Annotations(
                List.of("slow"), List.of(), null, null, null, null, null, null, null)));

        assertNotNull(test.get("testId"));
        assertEquals("0123456789abcdef", test.get("hash"));
        assertEquals("com.acme.Sample", test.get("className"));
        assertTrue(test.get("parameters") instanceof List);
    }
}
