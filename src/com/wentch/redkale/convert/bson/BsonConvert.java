/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wentch.redkale.convert.bson;

import com.wentch.redkale.convert.*;
import com.wentch.redkale.util.*;
import java.lang.reflect.*;

/**
 *
 * @author zhangjx
 */
public final class BsonConvert extends Convert<BsonReader, BsonWriter> {

    private static final ObjectPool<BsonReader> readerPool = BsonReader.createPool(Integer.getInteger("convert.bson.pool.size", 16));

    private static final ObjectPool<BsonWriter> writerPool = BsonWriter.createPool(Integer.getInteger("convert.bson.pool.size", 16));

    private final boolean tiny;

    protected BsonConvert(Factory<BsonReader, BsonWriter> factory, boolean tiny) {
        super(factory);
        this.tiny = tiny;
    }

    public <T> T convertFrom(final Type type, final byte[] bytes) {
        if (bytes == null) return null;
        return convertFrom(type, bytes, 0, bytes.length);
    }

    public <T> T convertFrom(final Type type, final byte[] bytes, int start, int len) {
        if (type == null) return null;
        final BsonReader in = readerPool.poll();
        in.setBytes(bytes, start, len);
        @SuppressWarnings("unchecked")
        T rs = (T) factory.loadDecoder(type).convertFrom(in);
        readerPool.offer(in);
        return rs;
    }

    public byte[] convertTo(final Type type, Object value) {
        if (type == null) return null;
        final BsonWriter out = writerPool.poll();
        out.setTiny(tiny);
        factory.loadEncoder(type).convertTo(out, value);
        byte[] result = out.toArray();
        writerPool.offer(out);
        return result;
    }

    public byte[] convertTo(Object value) {
        if (value == null) {
            final BsonWriter out = writerPool.poll();
            out.setTiny(tiny);
            out.writeNull();
            byte[] result = out.toArray();
            writerPool.offer(out);
            return result;
        }
        return convertTo(value.getClass(), value);
    }
}
