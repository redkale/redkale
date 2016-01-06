/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.json;

import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.function.*;
import org.redkale.convert.*;
import org.redkale.util.*;

/**
 *
 * <p>
 * 详情见: http://www.redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public final class JsonConvert extends Convert<JsonReader, JsonWriter> {

    public static final Type TYPE_MAP_STRING_STRING = new TypeToken<java.util.LinkedHashMap<String, String>>() {
    }.getType();

    private static final ObjectPool<JsonReader> readerPool = JsonReader.createPool(Integer.getInteger("convert.json.pool.size", 16));

    private static final ObjectPool<JsonWriter> writerPool = JsonWriter.createPool(Integer.getInteger("convert.json.pool.size", 16));

    private final boolean tiny;

    protected JsonConvert(JsonFactory factory, boolean tiny) {
        super(factory);
        this.tiny = tiny;
    }

    @Override
    public JsonFactory getFactory() {
        return (JsonFactory) factory;
    }

    //------------------------------ reader -----------------------------------------------------------
    public JsonReader pollJsonReader(final ByteBuffer... buffers) {
        return new JsonByteBufferReader(buffers);
    }

    public JsonReader pollJsonReader(final InputStream in) {
        return new JsonStreamReader(in);
    }

    public JsonReader pollJsonReader() {
        return readerPool.get();
    }

    public void offerJsonReader(JsonReader in) {
        if (in != null) readerPool.offer(in);
    }

    //------------------------------ writer -----------------------------------------------------------
    public JsonByteBufferWriter pollJsonWriter(final Supplier<ByteBuffer> supplier) {
        return new JsonByteBufferWriter(tiny, supplier);
    }

    public JsonWriter pollJsonWriter(final OutputStream out) {
        return new JsonStreamWriter(tiny, out);
    }

    public JsonWriter pollJsonWriter(final Charset charset, final OutputStream out) {
        return new JsonStreamWriter(tiny, charset, out);
    }

    public JsonWriter pollJsonWriter() {
        return writerPool.get().tiny(tiny);
    }

    public void offerJsonWriter(JsonWriter out) {
        if (out != null) writerPool.offer(out);
    }

    //------------------------------ convertFrom -----------------------------------------------------------
    public <T> T convertFrom(final Type type, final String text) {
        if (text == null) return null;
        return convertFrom(type, Utility.charArray(text));
    }

    public <T> T convertFrom(final Type type, final char[] text) {
        if (text == null) return null;
        return convertFrom(type, text, 0, text.length);
    }

    public <T> T convertFrom(final Type type, final char[] text, int start, int len) {
        if (text == null || type == null) return null;
        final JsonReader in = readerPool.get();
        in.setText(text, start, len);
        T rs = (T) factory.loadDecoder(type).convertFrom(in);
        readerPool.offer(in);
        return rs;
    }

    public <T> T convertFrom(final Type type, final ByteBuffer... buffers) {
        if (type == null || buffers == null || buffers.length == 0) return null;
        return (T) factory.loadDecoder(type).convertFrom(new JsonByteBufferReader(buffers));
    }

    public <T> T convertFrom(final Type type, final InputStream in) {
        if (type == null || in == null) return null;
        return (T) factory.loadDecoder(type).convertFrom(new JsonStreamReader(in));
    }

    public <T> T convertFrom(final Type type, final JsonReader reader) {
        if (type == null) return null;
        @SuppressWarnings("unchecked")
        T rs = (T) factory.loadDecoder(type).convertFrom(reader);
        return rs;
    }

    //------------------------------ convertTo -----------------------------------------------------------
    public String convertTo(Object value) {
        if (value == null) return "null";
        return convertTo(value.getClass(), value);
    }

    public String convertTo(final Type type, Object value) {
        if (type == null) return null;
        if (value == null) return "null";
        final JsonWriter out = writerPool.get().tiny(tiny);
        factory.loadEncoder(type).convertTo(out, value);
        String result = out.toString();
        writerPool.offer(out);
        return result;
    }

    public void convertTo(final OutputStream out, Object value) {
        if (value == null) {
            new JsonStreamWriter(tiny, out).writeNull();
        } else {
            factory.loadEncoder(value.getClass()).convertTo(new JsonStreamWriter(tiny, out), value);
        }
    }

    public void convertTo(final OutputStream out, final Type type, Object value) {
        if (type == null) return;
        if (value == null) {
            new JsonStreamWriter(tiny, out).writeNull();
        } else {
            factory.loadEncoder(type).convertTo(new JsonStreamWriter(tiny, out), value);
        }
    }

    public ByteBuffer[] convertTo(final Supplier<ByteBuffer> supplier, Object value) {
        if (supplier == null) return null;
        JsonByteBufferWriter out = new JsonByteBufferWriter(tiny, null, supplier);
        if (value == null) {
            out.writeNull();
        } else {
            factory.loadEncoder(value.getClass()).convertTo(out, value);
        }
        return out.toBuffers();
    }

    public ByteBuffer[] convertTo(final Supplier<ByteBuffer> supplier, final Type type, Object value) {
        if (supplier == null || type == null) return null;
        JsonByteBufferWriter out = new JsonByteBufferWriter(tiny, null, supplier);
        if (value == null) {
            out.writeNull();
        } else {
            factory.loadEncoder(type).convertTo(out, value);
        }
        return out.toBuffers();
    }

    public void convertTo(final JsonWriter writer, Object value) {
        if (value == null) {
            writer.writeNull();
        } else {
            factory.loadEncoder(value.getClass()).convertTo(writer, value);
        }
    }

    public void convertTo(final JsonWriter writer, final Type type, Object value) {
        if (type == null) return;
        if (value == null) {
            writer.writeNull();
        } else {
            factory.loadEncoder(type).convertTo(writer, value);
        }
    }

    public JsonWriter convertToWriter(Object value) {
        if (value == null) return null;
        return convertToWriter(value.getClass(), value);
    }

    public JsonWriter convertToWriter(final Type type, Object value) {
        if (type == null) return null;
        final JsonWriter out = writerPool.get().tiny(tiny);
        factory.loadEncoder(type).convertTo(out, value);
        return out;
    }
}
