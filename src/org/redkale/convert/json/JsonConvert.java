/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.json;

import java.lang.reflect.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.function.*;
import org.redkale.convert.*;
import org.redkale.util.*;

/**
 *
 * <p> 详情见: http://www.redkale.org
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

    public JsonByteBufferWriter pollJsonWriter(final Supplier<ByteBuffer> supplier) {
        return new JsonByteBufferWriter(supplier).setTiny(tiny);
    }

    public JsonByteBufferWriter pollJsonWriter(final Charset charset, final Supplier<ByteBuffer> supplier) {
        return new JsonByteBufferWriter(charset, supplier).setTiny(tiny);
    }

    public JsonWriter pollJsonWriter() {
        return writerPool.get().setTiny(tiny);
    }

    public void offerJsonWriter(JsonWriter out) {
        if (out != null) writerPool.offer(out);
    }

    public JsonReader pollJsonReader() {
        return readerPool.get();
    }

    public void offerJsonReader(JsonReader in) {
        if (in != null) readerPool.offer(in);
    }

    @Override
    public JsonFactory getFactory() {
        return (JsonFactory) factory;
    }

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

    public String convertTo(final Type type, Object value) {
        if (type == null) return null;
        if (value == null) return "null";
        final JsonWriter out = writerPool.get().setTiny(tiny);
        factory.loadEncoder(type).convertTo(out, value);
        String result = out.toString();
        writerPool.offer(out);
        return result;
    }

    public String convertTo(Object value) {
        if (value == null) return "null";
        return convertTo(value.getClass(), value);
    }

    public void convertTo(final JsonWriter out, final Type type, Object value) {
        if (type == null) return;
        if (value == null) {
            out.writeNull();
        } else {
            factory.loadEncoder(type).convertTo(out, value);
        }
    }

    public void convertTo(final JsonWriter out, Object value) {
        if (value == null) {
            out.writeNull();
        } else {
            factory.loadEncoder(value.getClass()).convertTo(out, value);
        }
    }

    public ByteBuffer[] convertTo(final Supplier<ByteBuffer> supplier, final Type type, Object value) {
        return convertTo(null, supplier, type, value);
    }

    public ByteBuffer[] convertTo(final Charset charset, final Supplier<ByteBuffer> supplier, final Type type, Object value) {
        if (supplier == null || type == null) return null;
        JsonByteBufferWriter out = new JsonByteBufferWriter(charset, supplier);
        if (value == null) {
            out.writeNull();
        } else {
            factory.loadEncoder(type).convertTo(out, value);
        }
        return out.toBuffers();
    }

    public ByteBuffer[] convertTo(final Supplier<ByteBuffer> supplier, Object value) {
        return convertTo(null, supplier, value);
    }

    public ByteBuffer[] convertTo(final Charset charset, final Supplier<ByteBuffer> supplier, Object value) {
        if (supplier == null) return null;
        JsonByteBufferWriter out = new JsonByteBufferWriter(charset, supplier);
        if (value == null) {
            out.writeNull();
        } else {
            factory.loadEncoder(value.getClass()).convertTo(out, value);
        }
        return out.toBuffers();
    }
}
