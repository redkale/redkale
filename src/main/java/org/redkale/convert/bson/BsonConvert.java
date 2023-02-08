/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.bson;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.function.*;
import org.redkale.convert.*;
import org.redkale.util.*;

/**
 * <blockquote><pre>
 * BSON协议格式:
 *  1) 基本数据类型: 直接转换成byte[]
 *  2) SmallString(无特殊字符且长度小于256的字符串): length(1 byte) + byte[](utf8); 通常用于类名、字段名、枚举。
 *  3) String: length(4 bytes) + byte[](utf8);
 *  4) 数组: length(4 bytes) + byte[]...
 *  5) Object:
 *      1、 realclass (SmallString) (如果指定格式化的class与实体对象的class不一致才会有该值, 该值可以使用@ConvertEntity给其取个别名)
 *      2、 空字符串(SmallString)
 *      3、 SIGN_OBJECTB 标记位，值固定为0xBB (short)
 *      4、 循环字段值:
 *          4.1 SIGN_HASNEXT 标记位，值固定为1 (byte)
 *          4.2 字段类型; 1-9为基本类型和字符串; 101-109为基本类型和字符串的数组; 127为Object
 *          4.3 字段名 (SmallString)
 *          4.4 字段的值Object
 *      5、 SIGN_NONEXT 标记位，值固定为0 (byte)
 *      6、 SIGN_OBJECTE 标记位，值固定为0xEE (short)
 *
 * </pre></blockquote>
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class BsonConvert extends BinaryConvert<BsonReader, BsonWriter> {

    private final ThreadLocal<BsonWriter> writerPool = ThreadLocal.withInitial(BsonWriter::new);

    private final Consumer<BsonWriter> writerConsumer = w -> offerWriter(w);

    private final ThreadLocal<BsonReader> readerPool = ThreadLocal.withInitial(BsonReader::new);

    private final boolean tiny;

    protected BsonConvert(ConvertFactory<BsonReader, BsonWriter> factory, boolean tiny) {
        super(factory);
        this.tiny = tiny;
    }

    @Override
    public BsonFactory getFactory() {
        return (BsonFactory) factory;
    }

    public static BsonConvert root() {
        return BsonFactory.root().getConvert();
    }

    @Override
    public BsonConvert newConvert(final BiFunction<Attribute, Object, Object> fieldFunc) {
        return newConvert(fieldFunc, null);
    }

    @Override
    public BsonConvert newConvert(final BiFunction<Attribute, Object, Object> fieldFunc, Function<Object, ConvertField[]> objExtFunc) {
        return new BsonConvert(getFactory(), tiny) {
            @Override
            protected <S extends BsonWriter> S configWrite(S writer) {
                return fieldFunc(writer, fieldFunc, objExtFunc);
            }
        };
    }

    //------------------------------ reader -----------------------------------------------------------
    public BsonReader pollReader(final ByteBuffer... buffers) {
        return new BsonByteBufferReader((ConvertMask) null, buffers);
    }

    public BsonReader pollReader(final InputStream in) {
        return new BsonStreamReader(in);
    }

    @Override
    public BsonReader pollReader() {
        BsonReader reader = readerPool.get();
        if (reader == null) {
            reader = new BsonReader();
        } else {
            readerPool.set(null);
        }
        return reader;
    }

    @Override
    public void offerReader(final BsonReader in) {
        if (in != null) {
            in.recycle();
            readerPool.set(in);
        }
    }

    //------------------------------ writer -----------------------------------------------------------
    public BsonByteBufferWriter pollWriter(final Supplier<ByteBuffer> supplier) {
        return configWrite(new BsonByteBufferWriter(tiny, supplier));
    }

    protected BsonWriter pollWriter(final OutputStream out) {
        return configWrite(new BsonStreamWriter(tiny, out));
    }

    @Override
    public BsonWriter pollWriter() {
        BsonWriter writer = writerPool.get();
        if (writer == null) {
            writer = new BsonWriter();
        } else {
            writerPool.set(null);
        }
        return configWrite(writer.tiny(tiny));
    }

    @Override
    public void offerWriter(final BsonWriter out) {
        if (out != null) {
            out.recycle();
            writerPool.set(out);
        }
    }

    //------------------------------ convertFrom -----------------------------------------------------------
    @Override
    public <T> T convertFrom(final Type type, final byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return convertFrom(type, bytes, 0, bytes.length);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T convertFrom(final Type type, final byte[] bytes, final int offset, final int len) {
        if (type == null) {
            return null;
        }
        final BsonReader in = new BsonReader(bytes, offset, len);
        @SuppressWarnings("unchecked")
        T rs = (T) factory.loadDecoder(type).convertFrom(in);
        return rs;
    }

    @SuppressWarnings("unchecked")
    public <T> T convertFrom(final Type type, final InputStream in) {
        if (type == null || in == null) {
            return null;
        }
        return (T) factory.loadDecoder(type).convertFrom(new BsonStreamReader(in));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T convertFrom(final Type type, final ByteBuffer... buffers) {
        if (type == null || buffers.length < 1) {
            return null;
        }
        return (T) factory.loadDecoder(type).convertFrom(new BsonByteBufferReader((ConvertMask) null, buffers));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T convertFrom(final Type type, final ConvertMask mask, final ByteBuffer... buffers) {
        if (type == null || buffers.length < 1) {
            return null;
        }
        return (T) factory.loadDecoder(type).convertFrom(new BsonByteBufferReader(mask, buffers));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T convertFrom(final Type type, final BsonReader reader) {
        if (type == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        T rs = (T) factory.loadDecoder(type).convertFrom(reader);
        return rs;
    }

    //------------------------------ convertTo -----------------------------------------------------------
    @Override
    public byte[] convertTo(final Object value) {
        if (value == null) {
            final BsonWriter out = pollWriter();
            out.writeNull();
            byte[] result = out.toArray();
            offerWriter(out);
            return result;
        }
        return convertTo(value.getClass(), value);
    }

    @Override
    public byte[] convertTo(final Type type, final Object value) {
        if (type == null) {
            return null;
        }
        final BsonWriter writer = pollWriter();
        factory.loadEncoder(type).convertTo(writer, value);
        byte[] result = writer.toArray();
        offerWriter(writer);
        return result;
    }

    @Override
    public byte[] convertToBytes(final Object value) {
        return convertTo(value);
    }

    @Override
    public byte[] convertToBytes(final Type type, final Object value) {
        return convertTo(type, value);
    }

    @Override
    public void convertToBytes(final Object value, final ConvertBytesHandler handler) {
        convertToBytes(value == null ? null : value.getClass(), value, handler);
    }

    @Override
    public void convertToBytes(final Type type, final Object value, final ConvertBytesHandler handler) {
        final BsonWriter writer = pollWriter();
        if (type == null) {
            writer.writeNull();
        } else {
            factory.loadEncoder(type).convertTo(writer, value);
        }
        writer.completed(handler, writerConsumer);
    }

    @Override
    public void convertToBytes(final ByteArray array, final Object value) {
        convertToBytes(array, value == null ? null : value.getClass(), value);
    }

    @Override
    public void convertToBytes(final ByteArray array, final Type type, final Object value) {
        final BsonWriter writer = configWrite(new BsonWriter(array).tiny(tiny));
        if (type == null) {
            writer.writeNull();
        } else {
            factory.loadEncoder(type).convertTo(writer, value);
        }
        writer.directTo(array);
    }

    public void convertTo(final OutputStream out, final Object value) {
        if (value == null) {
            pollWriter(out).writeNull();
        } else {
            factory.loadEncoder(value.getClass()).convertTo(pollWriter(out), value);
        }
    }

    public void convertTo(final OutputStream out, final Type type, final Object value) {
        if (type == null) {
            return;
        }
        if (value == null) {
            pollWriter(out).writeNull();
        } else {
            factory.loadEncoder(type).convertTo(pollWriter(out), value);
        }
    }

    @Override
    public ByteBuffer[] convertTo(final Supplier<ByteBuffer> supplier, final Object value) {
        if (supplier == null) {
            return null;
        }
        BsonByteBufferWriter out = pollWriter(supplier);
        if (value == null) {
            out.writeNull();
        } else {
            factory.loadEncoder(value.getClass()).convertTo(out, value);
        }
        return out.toBuffers();
    }

    @Override
    public ByteBuffer[] convertTo(final Supplier<ByteBuffer> supplier, final Type type, final Object value) {
        if (supplier == null || type == null) {
            return null;
        }
        BsonByteBufferWriter writer = pollWriter(supplier);
        if (value == null) {
            writer.writeNull();
        } else {
            factory.loadEncoder(type).convertTo(writer, value);
        }
        return writer.toBuffers();
    }

    @Override
    public void convertTo(final BsonWriter writer, final Object value) {
        if (value == null) {
            writer.writeNull();
        } else {
            factory.loadEncoder(value.getClass()).convertTo(writer, value);
        }
    }

    @Override
    public void convertTo(final BsonWriter writer, final Type type, final Object value) {
        if (type == null) {
            return;
        }
        factory.loadEncoder(type).convertTo(writer, value);
    }

    public BsonWriter convertToWriter(final Object value) {
        if (value == null) {
            return null;
        }
        return convertToWriter(value.getClass(), value);
    }

    public BsonWriter convertToWriter(final Type type, final Object value) {
        if (type == null) {
            return null;
        }
        final BsonWriter writer = writerPool.get().tiny(tiny);
        factory.loadEncoder(type).convertTo(writer, value);
        return writer;
    }
}
