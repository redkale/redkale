/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.bson;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.*;
import org.redkale.annotation.Nullable;
import org.redkale.convert.*;
import org.redkale.util.*;

/**
 *
 *
 * <blockquote>
 *
 * <pre>
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
 * </pre>
 *
 * </blockquote>
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public class BsonConvert extends BinaryConvert<BsonReader, BsonWriter> {

    private final ThreadLocal<BsonWriter> writerPool = Utility.withInitialThreadLocal(BsonWriter::new);

    private final Consumer<BsonWriter> writerConsumer = this::offerWriter;

    private final ThreadLocal<BsonReader> readerPool = Utility.withInitialThreadLocal(BsonReader::new);

    @Nullable
    private Encodeable lastEncodeable;

    @Nullable
    private Decodeable lastDecodeable;

    protected BsonConvert(ConvertFactory<BsonReader, BsonWriter> factory, int features) {
        super(factory, features);
    }

    @Override
    public BsonFactory getFactory() {
        return (BsonFactory) factory;
    }

    public static BsonConvert root() {
        return BsonFactory.root().getConvert();
    }

    @Override
    public BsonConvert newConvert(final BiFunction<Attribute, Object, Object> objFieldFunc) {
        return newConvert(objFieldFunc, null, null);
    }

    @Override
    public BsonConvert newConvert(final BiFunction<Attribute, Object, Object> objFieldFunc, BiFunction mapFieldFunc) {
        return newConvert(objFieldFunc, mapFieldFunc, null);
    }

    @Override
    public BsonConvert newConvert(
            final BiFunction<Attribute, Object, Object> objFieldFunc, Function<Object, ConvertField[]> objExtFunc) {
        return newConvert(objFieldFunc, null, objExtFunc);
    }

    @Override
    public BsonConvert newConvert(
            final BiFunction<Attribute, Object, Object> fieldFunc,
            BiFunction mapFieldFunc,
            Function<Object, ConvertField[]> objExtFunc) {
        return new BsonConvert(getFactory(), features) {
            @Override
            protected <S extends BsonWriter> S configWrite(S writer) {
                return fieldFunc(writer, fieldFunc, mapFieldFunc, objExtFunc);
            }
        };
    }

    // ------------------------------ reader -----------------------------------------------------------
    public BsonReader pollReader(final ByteBuffer... buffers) {
        return new BsonByteBufferReader(buffers);
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

    // ------------------------------ writer -----------------------------------------------------------
    public BsonByteBufferWriter pollWriter(final Supplier<ByteBuffer> supplier) {
        return configWrite(new BsonByteBufferWriter(features, supplier));
    }

    protected BsonWriter pollWriter(final OutputStream out) {
        return configWrite(new BsonStreamWriter(features, out));
    }

    @Override
    public BsonWriter pollWriter() {
        BsonWriter writer = writerPool.get();
        if (writer == null) {
            writer = new BsonWriter();
        } else {
            writerPool.set(null);
        }
        return configWrite(writer.withFeatures(features));
    }

    @Override
    public void offerWriter(final BsonWriter out) {
        if (out != null) {
            out.recycle();
            writerPool.set(out);
        }
    }

    // ------------------------------ convertFrom -----------------------------------------------------------
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
        Decodeable decoder = this.lastDecodeable;
        if (decoder == null || decoder.getType() != type) {
            decoder = factory.loadDecoder(type);
            this.lastDecodeable = decoder;
        }
        T rs = (T) decoder.convertFrom(in);
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
        if (type == null || Utility.isEmpty(buffers)) {
            return null;
        }
        Decodeable decoder = this.lastDecodeable;
        if (decoder == null || decoder.getType() != type) {
            decoder = factory.loadDecoder(type);
            this.lastDecodeable = decoder;
        }
        return (T) decoder.convertFrom(new BsonByteBufferReader(buffers));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T convertFrom(final Type type, final BsonReader reader) {
        if (type == null) {
            return null;
        }
        Decodeable decoder = this.lastDecodeable;
        if (decoder == null || decoder.getType() != type) {
            decoder = factory.loadDecoder(type);
            this.lastDecodeable = decoder;
        }
        T rs = (T) decoder.convertFrom(reader);
        return rs;
    }

    // ------------------------------ convertTo -----------------------------------------------------------
    @Override
    public byte[] convertTo(final Type type, final Object value) {
        if (type == null && value == null) {
            final BsonWriter out = pollWriter();
            out.writeNull();
            byte[] result = out.toArray();
            offerWriter(out);
            return result;
        }
        final Type t = type == null ? value.getClass() : type;
        Encodeable encoder = this.lastEncodeable;
        if (encoder == null || encoder.getType() != t) {
            encoder = factory.loadEncoder(t);
            this.lastEncodeable = encoder;
        }
        final BsonWriter writer = pollWriter();
        encoder.convertTo(writer, value);
        byte[] result = writer.toArray();
        offerWriter(writer);
        return result;
    }

    @Override
    public byte[] convertToBytes(final Type type, final Object value) {
        return convertTo(type, value);
    }

    @Override
    public void convertToBytes(final Type type, final Object value, final ConvertBytesHandler handler) {
        final BsonWriter writer = pollWriter();
        if (type == null && value == null) {
            writer.writeNull();
        } else {
            Encodeable encoder = this.lastEncodeable;
            if (encoder == null || encoder.getType() != type) {
                encoder = factory.loadEncoder(type);
                this.lastEncodeable = encoder;
            }
            encoder.convertTo(writer, value);
        }
        writer.completed(handler, writerConsumer);
    }

    @Override
    public void convertToBytes(final ByteArray array, final Type type, final Object value) {
        Objects.requireNonNull(array);
        final BsonWriter writer = configWrite(new BsonWriter(array).withFeatures(features));
        if (type == null && value == null) {
            writer.writeNull();
        } else {
            Encodeable encoder = this.lastEncodeable;
            if (encoder == null || encoder.getType() != type) {
                encoder = factory.loadEncoder(type);
                this.lastEncodeable = encoder;
            }
            factory.loadEncoder(type == null ? value.getClass() : type).convertTo(writer, value);
        }
        writer.directTo(array);
    }

    public void convertTo(final OutputStream out, final Object value) {
        convertTo(out, (Type) null, value);
    }

    public void convertTo(final OutputStream out, final Type type, final Object value) {
        if (type == null && value == null) {
            pollWriter(out).writeNull();
        } else {
            factory.loadEncoder(type == null ? value.getClass() : type).convertTo(pollWriter(out), value);
        }
    }

    @Override
    public ByteBuffer[] convertTo(final Supplier<ByteBuffer> supplier, final Type type, final Object value) {
        Objects.requireNonNull(supplier);
        BsonByteBufferWriter writer = pollWriter(supplier);
        if (type == null && value == null) {
            writer.writeNull();
        } else {
            factory.loadEncoder(type == null ? value.getClass() : type).convertTo(writer, value);
        }
        return writer.toBuffers();
    }

    @Override
    public void convertTo(final BsonWriter writer, final Type type, final Object value) {
        if (type == null && value == null) { // 必须判断type==null
            writer.writeNull();
        } else {
            final Type t = type == null ? value.getClass() : type;
            Encodeable encoder = this.lastEncodeable;
            if (encoder == null || encoder.getType() != t) {
                encoder = factory.loadEncoder(t);
                this.lastEncodeable = encoder;
            }
            encoder.convertTo(writer, value);
        }
    }

    public BsonWriter convertToWriter(final Type type, final Object value) {
        if (value == null) {
            return null;
        }
        final BsonWriter writer = writerPool.get().withFeatures(features);
        final Type t = type == null ? value.getClass() : type;
        Encodeable encoder = this.lastEncodeable;
        if (encoder == null || encoder.getType() != t) {
            encoder = factory.loadEncoder(t);
            this.lastEncodeable = encoder;
        }
        encoder.convertTo(writer, value);
        return writer;
    }
}
