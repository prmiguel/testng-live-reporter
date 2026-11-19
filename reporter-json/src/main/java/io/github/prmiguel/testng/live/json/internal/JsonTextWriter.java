package io.github.prmiguel.testng.live.json.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Minimal JSON text builder with no external dependencies.
 * <p>
 * Nodes are assembled explicitly rather than by reflection, so field order is stable
 * and absent values can be omitted instead of written as {@code null}. A member name is
 * always followed directly by its value, including container values.
 */
public final class JsonTextWriter {

    private final StringBuilder out = new StringBuilder(256);
    private final List<Boolean> firstEntry = new ArrayList<>();
    private boolean expectingValue;

    /**
     * Starts an object. Every {@link #beginObject()} must be closed by {@link #endObject()}.
     */
    public JsonTextWriter beginObject() {
        separator();
        out.append('{');
        firstEntry.add(Boolean.TRUE);
        return this;
    }

    public JsonTextWriter endObject() {
        out.append('}');
        popContainer();
        return this;
    }

    /**
     * Starts an array. Every {@link #beginArray()} must be closed by {@link #endArray()}.
     */
    public JsonTextWriter beginArray() {
        separator();
        out.append('[');
        firstEntry.add(Boolean.TRUE);
        return this;
    }

    public JsonTextWriter endArray() {
        out.append(']');
        popContainer();
        return this;
    }

    /**
     * Writes a member name. The value must follow immediately.
     */
    public JsonTextWriter name(String name) {
        Objects.requireNonNull(name, "name must not be null");
        separator();
        writeEscaped(name);
        out.append(':');
        expectingValue = true;
        return this;
    }

    public JsonTextWriter value(String value) {
        if (value == null) {
            return nullValue();
        }
        separator();
        writeEscaped(value);
        return this;
    }

    public JsonTextWriter value(long value) {
        separator();
        out.append(value);
        return this;
    }

    public JsonTextWriter value(boolean value) {
        separator();
        out.append(value);
        return this;
    }

    public JsonTextWriter nullValue() {
        separator();
        out.append("null");
        return this;
    }

    /**
     * Returns the JSON text accumulated so far.
     */
    @Override
    public String toString() {
        return out.toString();
    }

    private void separator() {
        if (expectingValue) {
            expectingValue = false;
            return;
        }
        int index = firstEntry.size() - 1;
        if (index < 0) {
            return;
        }
        if (Boolean.TRUE.equals(firstEntry.get(index))) {
            firstEntry.set(index, Boolean.FALSE);
        } else {
            out.append(',');
        }
    }

    private void popContainer() {
        if (!firstEntry.isEmpty()) {
            firstEntry.remove(firstEntry.size() - 1);
        }
    }

    private void writeEscaped(String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
