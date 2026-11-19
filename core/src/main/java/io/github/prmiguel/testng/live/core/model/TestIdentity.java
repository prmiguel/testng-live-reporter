package io.github.prmiguel.testng.live.core.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Stable identity of a test method and its canonical parameters.
 *
 * @param className  fully qualified class name
 * @param methodName method name
 * @param parameters canonical parameters
 * @param readableKey human-readable identity: package.Class.method[params]
 * @param hash       16-character lowercase SHA-256 prefix of the canonical payload
 * @param testId     stable identifier: readableKey#hash
 */
public record TestIdentity(String className, String methodName, List<CanonicalParameter> parameters,
                           String readableKey, String hash, String testId) {

    public TestIdentity {
        Objects.requireNonNull(className, "className must not be null");
        Objects.requireNonNull(methodName, "methodName must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");
        Objects.requireNonNull(readableKey, "readableKey must not be null");
        Objects.requireNonNull(hash, "hash must not be null");
        Objects.requireNonNull(testId, "testId must not be null");
        parameters = Collections.unmodifiableList(List.copyOf(parameters));
    }
}
