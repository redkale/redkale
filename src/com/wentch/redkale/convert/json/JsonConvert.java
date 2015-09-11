/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.convert.json;

import com.wentch.redkale.convert.*;
import com.wentch.redkale.util.*;
import java.lang.reflect.*;

/**
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public final class JsonConvert extends Convert<JsonReader, JsonWriter> {

    public static final Type TYPE_MAP_STRING_STRING = new TypeToken<java.util.Map<String, String>>() {
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
        final JsonReader in = readerPool.poll();
        in.setText(text, start, len);
        T rs = (T) factory.loadDecoder(type).convertFrom(in);
        readerPool.offer(in);
        return rs;
    }

    public String convertTo(final Type type, Object value) {
        if (type == null) return null;
        if (value == null) return "null";
        final JsonWriter out = writerPool.poll();
        out.setTiny(tiny);
        factory.loadEncoder(type).convertTo(out, value);
        String result = out.toString();
        writerPool.offer(out);
        return result;
    }

    public String convertTo(Object value) {
        if (value == null) return "null";
        return convertTo(value.getClass(), value);
    }

    public byte[] convertToUTF8Bytes(Object value) {
        if (value == null) return new byte[]{110, 117, 108, 108};
        return convertToUTF8Bytes(value.getClass(), value);
    }

    public byte[] convertToUTF8Bytes(final Type type, Object value) {
        if (type == null) return null;
        if (value == null) return new byte[]{110, 117, 108, 108};
        final JsonWriter out = writerPool.poll();
        out.setTiny(tiny);
        factory.loadEncoder(type).convertTo(out, value);
        byte[] result = out.toUTF8Bytes();
        writerPool.offer(out);
        return result;
    }
}
