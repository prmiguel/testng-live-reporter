package io.github.prmiguel.testng.live.core.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Stable identity of a test method and its canonical parameters.
 * <p>
 * {@code annotations} carries the {@code @Test} metadata TestNG exposes on
 * {@code ITestNGMethod}. It is deliberately excluded from {@code hash}: the generator hashes a
 * {@code className#methodName#parameters} string, so identity stays comparable with reports
 * produced before this metadata existed.
 *
 * @param className   fully qualified class name
 * @param methodName  method name
 * @param parameters  canonical parameters
 * @param readableKey human-readable identity: package.Class.method[params]
 * @param hash        16-character lowercase SHA-256 prefix of the canonical payload
 * @param testId      stable identifier: readableKey#hash
 * @param annotations {@code @Test} metadata, never null
 */
public record TestIdentity(String className, String methodName, List<CanonicalParameter> parameters,
                           String readableKey, String hash, String testId, Annotations annotations) {

    /**
     * Convenience constructor for identities without annotation metadata.
     *
     * @param className   fully qualified class name
     * @param methodName  method name
     * @param parameters  canonical parameters
     * @param readableKey human-readable identity
     * @param hash        canonical hash
     * @param testId      stable identifier
     */
    public TestIdentity(String className, String methodName, List<CanonicalParameter> parameters,
                        String readableKey, String hash, String testId) {
        this(className, methodName, parameters, readableKey, hash, testId, Annotations.EMPTY);
    }

    public TestIdentity {
        Objects.requireNonNull(className, "className must not be null");
        Objects.requireNonNull(methodName, "methodName must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");
        Objects.requireNonNull(readableKey, "readableKey must not be null");
        Objects.requireNonNull(hash, "hash must not be null");
        Objects.requireNonNull(testId, "testId must not be null");
        Objects.requireNonNull(annotations, "annotations must not be null");
        parameters = Collections.unmodifiableList(List.copyOf(parameters));
    }

    /**
     * The {@code @Test} attributes TestNG exposes directly on {@link org.testng.ITestNGMethod}.
     * <p>
     * Absent values are null so consumers can omit them rather than guess at defaults. Values
     * TestNG already merges from a class-level {@code @Test} are reported as merged, and per-run
     * facts (invocation counts, thread names) are deliberately not repeated here.
     *
     * @param groups          groups this method belongs to, empty when none
     * @param dependsOnGroups groups that must pass first, empty when none
     * @param enabled         false when the method is disabled, null when enabled
     * @param description     human description, null when unset
     * @param priority        scheduling priority, null when 0
     * @param timeOut         per-invocation timeout in millis, null when 0
     * @param invocationCount number of invocations, null when 1
     * @param retryAnalyzer   retry analyzer class name, null when none
     * @param dataProvider    data provider name, null when none
     */
    public record Annotations(List<String> groups, List<String> dependsOnGroups, Boolean enabled,
                              String description, Integer priority, Long timeOut,
                              Integer invocationCount, String retryAnalyzer, String dataProvider) {

        /** No annotation metadata at all. */
        public static final Annotations EMPTY = new Annotations(
                List.of(), List.of(), null, null, null, null, null, null, null);

        public Annotations {
            groups = groups == null ? List.of() : Collections.unmodifiableList(List.copyOf(groups));
            dependsOnGroups = dependsOnGroups == null
                    ? List.of()
                    : Collections.unmodifiableList(List.copyOf(dependsOnGroups));
        }

        /**
         * Returns true when no attribute carries a value.
         *
         * @return true for empty metadata
         */
        public boolean isEmpty() {
            return groups.isEmpty() && dependsOnGroups.isEmpty() && enabled == null && description == null
                    && priority == null && timeOut == null && invocationCount == null
                    && retryAnalyzer == null && dataProvider == null;
        }
    }
}
