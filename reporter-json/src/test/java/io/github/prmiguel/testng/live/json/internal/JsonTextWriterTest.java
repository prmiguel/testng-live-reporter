package io.github.prmiguel.testng.live.json.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonTextWriterTest {

    @Test
    void writesNestedContainersWithoutStraySeparators() {
        JsonTextWriter writer = new JsonTextWriter();
        writer.beginObject()
                .name("a").value(1L)
                .name("nested").beginObject()
                .name("b").value("x")
                .endObject()
                .name("array").beginArray()
                .value(true)
                .beginObject()
                .name("c").nullValue()
                .endObject()
                .endArray()
                .endObject();

        assertEquals("{\"a\":1,\"nested\":{\"b\":\"x\"},\"array\":[true,{\"c\":null}]}", writer.toString());
    }

    @Test
    void writesEmptyContainers() {
        JsonTextWriter writer = new JsonTextWriter();
        writer.beginObject().name("o").beginObject().endObject().name("a").beginArray().endArray().endObject();

        assertEquals("{\"o\":{},\"a\":[]}", writer.toString());
    }

    @Test
    void escapesControlCharactersAndQuotes() {
        JsonTextWriter writer = new JsonTextWriter();
        writer.beginObject().name("k").value("q\"b\\s\n\t\r\b\f\u0001\u001f").endObject();

        assertEquals("{\"k\":\"q\\\"b\\\\s\\n\\t\\r\\b\\f\\u0001\\u001f\"}", writer.toString());
    }

    @Test
    void keepsNonAsciiReadable() {
        JsonTextWriter writer = new JsonTextWriter();
        writer.beginObject().name("k").value("caf\u00e9 \u2615 \ud83d\ude80").endObject();

        assertEquals("{\"k\":\"caf\u00e9 \u2615 \ud83d\ude80\"}", writer.toString());
    }

    @Test
    void escapesMemberNames() {
        JsonTextWriter writer = new JsonTextWriter();
        writer.beginObject().name("we\"ird").value(1L).endObject();

        assertEquals("{\"we\\\"ird\":1}", writer.toString());
    }
}
