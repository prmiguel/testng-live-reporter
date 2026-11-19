package io.github.prmiguel.testng.live.core.model;

import java.util.Objects;

/**
 * A deterministic, human-readable representation of a single parameter value.
 *
 * @param declaredType the declared parameter type (fully qualified)
 * @param runtimeType  the actual runtime type (fully qualified)
 * @param value        the canonical string value
 */
public record CanonicalParameter(String declaredType, String runtimeType, String value) {

    public CanonicalParameter {
        Objects.requireNonNull(declaredType, "declaredType must not be null");
        Objects.requireNonNull(runtimeType, "runtimeType must not be null");
        Objects.requireNonNull(value, "value must not be null");
    }
}
