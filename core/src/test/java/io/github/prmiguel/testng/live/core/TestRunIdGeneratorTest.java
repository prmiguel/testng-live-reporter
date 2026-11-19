package io.github.prmiguel.testng.live.core;

import io.github.prmiguel.testng.live.core.fixtures.AnnotatedFixture;
import io.github.prmiguel.testng.live.core.model.TestIdentity;
import org.junit.jupiter.api.Test;
import org.testng.ITestNGMethod;
import org.testng.TestNG;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestRunIdGeneratorTest {

    private static ITestNGMethod method(String methodName) {
        final ITestNGMethod[] found = new ITestNGMethod[1];
        TestNG testNG = new TestNG();
        testNG.setTestClasses(new Class[]{AnnotatedFixture.class});
        testNG.setUseDefaultListeners(false);
        // Read from the context, not from onTestStart: a disabled method never starts.
        testNG.addListener(new org.testng.ITestListener() {
            @Override
            public void onStart(org.testng.ITestContext context) {
                // enabled=false methods are excluded, not "all", so search both collections.
                for (ITestNGMethod candidate : context.getAllTestMethods()) {
                    if (candidate.getMethodName().equals(methodName) && found[0] == null) {
                        found[0] = candidate;
                    }
                }
                for (ITestNGMethod candidate : context.getExcludedMethods()) {
                    if (candidate.getMethodName().equals(methodName) && found[0] == null) {
                        found[0] = candidate;
                    }
                }
            }
        });
        testNG.run();
        assertNotNull(found[0], "method not found: " + methodName);
        return found[0];
    }

    @Test
    void annotationMetadataDoesNotChangeTheHash() {
        TestRunIdGenerator generator = new TestRunIdGenerator(List.of());
        ITestNGMethod annotated = method("richlyAnnotated");

        TestIdentity withMetadata = generator.createIdentity(annotated);
        TestIdentity withoutMetadata = new TestIdentity(
                withMetadata.className(), withMetadata.methodName(), withMetadata.parameters(),
                withMetadata.readableKey(), withMetadata.hash(), withMetadata.testId());

        assertEquals(withoutMetadata.hash(), withMetadata.hash(),
                "the hash is derived from className#methodName#params, never from the record");
        assertEquals(withoutMetadata.testId(), withMetadata.testId());
        assertTrue(withMetadata.annotations().groups().contains("slow"));
        assertTrue(withoutMetadata.annotations().isEmpty());
    }

    @Test
    void capturesTheDirectlyAvailableAttributes() {
        TestIdentity.Annotations metadata = new TestRunIdGenerator(List.of())
                .createIdentity(method("richlyAnnotated"))
                .annotations();

        assertEquals(List.of("slow"), metadata.groups());
        assertEquals(List.of("base"), metadata.dependsOnGroups());
        assertEquals("covers the annotated fixture", metadata.description());
        assertEquals(2, metadata.priority());
        assertEquals(500L, metadata.timeOut());
        assertEquals(3, metadata.invocationCount());
        assertEquals("io.github.prmiguel.testng.live.core.fixtures.AnnotatedFixture$OnceAnalyzer",
                metadata.retryAnalyzer());
        assertNull(metadata.enabled(), "enabled=true is the default and is omitted");
    }

    @Test
    void omitsDefaultsForABareTest() {
        TestIdentity.Annotations metadata = new TestRunIdGenerator(List.of())
                .createIdentity(method("bare"))
                .annotations();

        assertTrue(metadata.isEmpty(), "a plain @Test carries no metadata");
    }

    @Test
    void reportsDisabledMethods() {
        TestIdentity.Annotations metadata = new TestRunIdGenerator(List.of())
                .createIdentity(method("disabled"))
                .annotations();

        assertEquals(Boolean.FALSE, metadata.enabled());
    }

    @Test
    void toleratesMissingAnnotations() {
        assertTrue(TestRunIdGenerator.annotations(null).isEmpty(),
                "a configuration method has no @Test and must not fail");
    }
}
