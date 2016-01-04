/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.bson;

import java.lang.reflect.*;
import java.nio.*;
import java.util.function.*;
import org.redkale.convert.*;
import org.redkale.util.*;

/**
 * BSON协议格式:
 *  1). 基本数据类型: 直接转换成byte[]
 *  2). SmallString(无特殊字符且长度小于256的字符串): length(1 byte) + byte[](utf8); 通常用于类名、字段名、枚举。
 *  3). String: length(4 bytes) + byte[](utf8);
 *  4). 数组: length(4 bytes) + byte[]...
 *  5). Object:
 *      1. realclass (SmallString) (如果指定格式化的class与实体对象的class不一致才会有该值, 该值可以使用@ConvertEntity给其取个别名)
 *      2. 空字符串(SmallString)
 *      3. SIGN_OBJECTB 标记位，值固定为0xBB (short)
 *      4. 循环字段值:
 *          4.1 SIGN_HASNEXT 标记位，值固定为1 (byte)
 *          4.2 字段类型; 1-9为基本类型&字符串; 101-109为基本类型&字符串的数组; 127为Object
 *          4.3 字段名 (SmallString)
 *          4.4 字段的值Object
 *      5. SIGN_NONEXT 标记位，值固定为0 (byte)
 *      6. SIGN_OBJECTE 标记位，值固定为0xEE (short)
 *
 * <p> 详情见: http://www.redkale.org
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

    public BsonByteBufferWriter pollBsonWriter(final Supplier<ByteBuffer> supplier) {
        return new BsonByteBufferWriter(supplier).setTiny(tiny);
    }

    public BsonWriter pollBsonWriter() {
        return writerPool.get().setTiny(tiny);
    }

    public void offerBsonWriter(BsonWriter out) {
        if (out != null) writerPool.offer(out);
    }

    public BsonReader pollBsonReader() {
        return readerPool.get();
    }

    public void offerBsonReader(BsonReader in) {
        if (in != null) readerPool.offer(in);
    }

    public <T> T convertFrom(final Type type, final byte[] bytes) {
        if (bytes == null) return null;
        return convertFrom(type, bytes, 0, bytes.length);
    }

    public <T> T convertFrom(final Type type, final byte[] bytes, int start, int len) {
        if (type == null) return null;
        final BsonReader in = readerPool.get();
        in.setBytes(bytes, start, len);
        @SuppressWarnings("unchecked")
        T rs = (T) factory.loadDecoder(type).convertFrom(in);
        readerPool.offer(in);
        return rs;
    }

    public <T> T convertFrom(final Type type, final ByteBuffer... buffers) {
        if (type == null || buffers.length < 1) return null;
        return (T) factory.loadDecoder(type).convertFrom(new BsonByteBufferReader(buffers));
    }

    public <T> T convertFrom(final BsonReader in, final Type type) {
        if (type == null) return null;
        @SuppressWarnings("unchecked")
        T rs = (T) factory.loadDecoder(type).convertFrom(in);
        return rs;
    }

    public byte[] convertTo(final Type type, Object value) {
        if (type == null) return null;
        final BsonWriter out = writerPool.get().setTiny(tiny);
        factory.loadEncoder(type).convertTo(out, value);
        byte[] result = out.toArray();
        writerPool.offer(out);
        return result;
    }

    public void convertTo(final BsonWriter out, final Type type, Object value) {
        if (type == null) return;
        factory.loadEncoder(type).convertTo(out, value);
    }

    public ByteBuffer[] convertTo(final Supplier<ByteBuffer> supplier, final Type type, Object value) {
        if (supplier == null || type == null) return null;
        BsonByteBufferWriter out = new BsonByteBufferWriter(supplier);
        if (value == null) {
            out.writeNull();
        } else {
            factory.loadEncoder(type).convertTo(out, value);
        }
        return out.toBuffers();
    }

    public ByteBuffer[] convertTo(final Supplier<ByteBuffer> supplier, Object value) {
        if (supplier == null) return null;
        BsonByteBufferWriter out = new BsonByteBufferWriter(supplier);
        if (value == null) {
            out.writeNull();
        } else {
            factory.loadEncoder(value.getClass()).convertTo(out, value);
        }
        return out.toBuffers();
    }

    public void convertTo(final BsonWriter out, Object value) {
        if (value == null) {
            out.writeNull();
        } else {
            factory.loadEncoder(value.getClass()).convertTo(out, value);
        }
    }

    public byte[] convertTo(Object value) {
        if (value == null) {
            final BsonWriter out = writerPool.get().setTiny(tiny);
            out.writeNull();
            byte[] result = out.toArray();
            writerPool.offer(out);
            return result;
        }
        return convertTo(value.getClass(), value);
    }

    public BsonWriter convertToWriter(final Type type, Object value) {
        if (type == null) return null;
        final BsonWriter out = writerPool.get().setTiny(tiny);
        factory.loadEncoder(type).convertTo(out, value);
        return out;
    }

    public BsonWriter convertToWriter(Object value) {
        if (value == null) return null;
        return convertToWriter(value.getClass(), value);
    }
}
