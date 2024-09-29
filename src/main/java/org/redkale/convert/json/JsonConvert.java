/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.json;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.*;
import org.redkale.annotation.Nullable;
import org.redkale.convert.*;
import org.redkale.service.RetResult;
import org.redkale.util.*;

/**
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public class JsonConvert extends TextConvert<JsonReader, JsonWriter> {

    public static final Type TYPE_MAP_STRING_STRING = new TypeToken<java.util.Map<String, String>>() {}.getType();

    public static final Type TYPE_LIST_STRING = new TypeToken<java.util.List<String>>() {}.getType();

    public static final Type TYPE_RETRESULT_STRING = new TypeToken<RetResult<String>>() {}.getType();

    private final ThreadLocal<JsonBytesWriter> bytesWriterPool = Utility.withInitialThreadLocal(JsonBytesWriter::new);

    private final ThreadLocal<JsonCharsWriter> charsWriterPool = Utility.withInitialThreadLocal(JsonCharsWriter::new);

    private final Consumer<JsonBytesWriter> offerBytesConsumer = this::offerJsonBytesWriter;

    private final ThreadLocal<JsonReader> readerPool = Utility.withInitialThreadLocal(JsonReader::new);

    @Nullable
    private Encodeable lastEncodeable;

    @Nullable
    private Decodeable lastDecodeable;

    protected JsonConvert(JsonFactory factory, int features) {
        super(factory, features);
    }

    @Override
    public JsonFactory getFactory() {
        return (JsonFactory) factory;
    }

    public static JsonConvert root() {
        return JsonFactory.root().getConvert();
    }

    @Override
    public JsonConvert newConvert(final BiFunction<Attribute, Object, Object> objFieldFunc) {
        return newConvert(objFieldFunc, null, null);
    }

    @Override
    public JsonConvert newConvert(
            final BiFunction<Attribute, Object, Object> objFieldFunc, Function<Object, ConvertField[]> objExtFunc) {
        return newConvert(objFieldFunc, null, objExtFunc);
    }

    @Override
    public JsonConvert newConvert(final BiFunction<Attribute, Object, Object> objFieldFunc, BiFunction mapFieldFunc) {
        return newConvert(objFieldFunc, mapFieldFunc, null);
    }

    @Override
    public JsonConvert newConvert(
            final BiFunction<Attribute, Object, Object> objFieldFunc,
            BiFunction mapFieldFunc,
            Function<Object, ConvertField[]> objExtFunc) {
        return new JsonConvert(getFactory(), features) {
            @Override
            protected <S extends JsonWriter> S configWrite(S writer) {
                return fieldFunc(writer, objFieldFunc, mapFieldFunc, objExtFunc);
            }
        };
    }

    @Override
    public JsonReader pollReader() {
        JsonReader reader = readerPool.get();
        if (reader == null) {
            reader = new JsonReader();
        } else {
            readerPool.set(null);
        }
        return reader;
    }

    @Override
    public void offerReader(final JsonReader in) {
        if (in != null) {
            in.recycle();
            readerPool.set(in);
        }
    }

    @Override
    public JsonWriter pollWriter() {
        JsonBytesWriter writer = bytesWriterPool.get();
        if (writer == null) {
            writer = new JsonBytesWriter();
        } else {
            bytesWriterPool.set(null);
        }
        return configWrite((JsonBytesWriter) writer.withFeatures(features));
    }

    @Override
    public void offerWriter(final JsonWriter writer) {
        if (writer instanceof JsonBytesWriter) {
            JsonBytesWriter bw = (JsonBytesWriter) writer;
            bw.recycle();
            bytesWriterPool.set(bw);
        }
    }

    // ------------------------------ writer -----------------------------------------------------------
    private JsonBytesWriter pollJsonBytesWriter() {
        JsonBytesWriter writer = bytesWriterPool.get();
        if (writer == null) {
            writer = new JsonBytesWriter();
        } else {
            bytesWriterPool.set(null);
        }
        return configWrite((JsonBytesWriter) writer.withFeatures(features));
    }

    private void offerJsonBytesWriter(final JsonBytesWriter writer) {
        if (writer != null) {
            writer.recycle();
            bytesWriterPool.set(writer);
        }
    }

    private JsonCharsWriter pollJsonCharsWriter() {
        JsonCharsWriter writer = charsWriterPool.get();
        if (writer == null) {
            writer = new JsonCharsWriter();
        } else {
            charsWriterPool.set(null);
        }
        return configWrite((JsonCharsWriter) writer.withFeatures(features));
    }

    private void offerJsonCharsWriter(final JsonCharsWriter writer) {
        if (writer != null) {
            writer.recycle();
            charsWriterPool.set(writer);
        }
    }
    // ------------------------------ convertFrom -----------------------------------------------------------
    @Override
    public <T> T convertFrom(final Type type, final byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return convertFrom(type, new String(bytes, StandardCharsets.UTF_8));
    }

    @Override
    public <T> T convertFrom(final Type type, final byte[] bytes, final int offset, final int length) {
        if (bytes == null) {
            return null;
        }
        return convertFrom(type, new String(bytes, offset, length, StandardCharsets.UTF_8));
    }

    @Override
    public <T> T convertFrom(final Type type, final String text) {
        if (text == null) {
            return null;
        }
        return convertFrom(type, Utility.charArray(text));
    }

    public <T> T convertFrom(final Type type, final char[] text) {
        if (text == null) {
            return null;
        }
        return convertFrom(type, text, 0, text.length);
    }

    public <T> T convertFrom(final Type type, final char[] text, final int offset, final int length) {
        if (text == null || type == null) {
            return null;
        }
        Decodeable decoder = this.lastDecodeable;
        if (decoder == null || decoder.getType() != type) {
            decoder = factory.loadDecoder(type);
            this.lastDecodeable = decoder;
        }
        JsonReader reader = pollReader().setText(text, offset, length);
        T rs = (T) decoder.convertFrom(reader);
        offerReader(reader);
        return rs;
    }

    public <T> T convertFrom(final Type type, final InputStream in) {
        if (type == null || in == null) {
            return null;
        }
        Decodeable decoder = this.lastDecodeable;
        if (decoder == null || decoder.getType() != type) {
            decoder = factory.loadDecoder(type);
            this.lastDecodeable = decoder;
        }
        return (T) decoder.convertFrom(new JsonStreamReader(in));
    }

    @Override
    public <T> T convertFrom(final Type type, final ByteBuffer... buffers) {
        if (type == null || buffers == null || buffers.length == 0) {
            return null;
        }
        Decodeable decoder = this.lastDecodeable;
        if (decoder == null || decoder.getType() != type) {
            decoder = factory.loadDecoder(type);
            this.lastDecodeable = decoder;
        }
        return (T) decoder.convertFrom(new JsonByteBufferReader(buffers));
    }

    @Override
    public <T> T convertFrom(final Type type, final JsonReader reader) {
        if (type == null) {
            return null;
        }
        Decodeable decoder = this.lastDecodeable;
        if (decoder == null || decoder.getType() != type) {
            decoder = factory.loadDecoder(type);
            this.lastDecodeable = decoder;
        }
        @SuppressWarnings("unchecked")
        T rs = (T) decoder.convertFrom(reader);
        return rs;
    }

    // 返回非null的值是由String、ArrayList、HashMap任意组合的对象
    public <V> V convertFrom(final String text) {
        if (text == null) {
            return null;
        }
        return (V) convertFrom(Utility.charArray(text));
    }

    // 返回非null的值是由String、ArrayList、HashMap任意组合的对象
    public <V> V convertFrom(final char[] text) {
        if (text == null) {
            return null;
        }
        return (V) convertFrom(text, 0, text.length);
    }

    // 返回非null的值是由String、ArrayList、HashMap任意组合的对象
    public <V> V convertFrom(final char[] text, final int offset, final int length) {
        if (text == null) {
            return null;
        }
        // final JsonReader in = readerPool.get();
        // in.setText(text, offset, length);
        Object rs = new AnyDecoder(factory).convertFrom(new JsonReader(text, offset, length));
        // readerPool.accept(in);
        return (V) rs;
    }

    // 返回非null的值是由String、ArrayList、HashMap任意组合的对象
    public <V> V convertFrom(final InputStream in) {
        if (in == null) {
            return null;
        }
        return (V) new AnyDecoder(factory).convertFrom(new JsonStreamReader(in));
    }

    // 返回非null的值是由String、ArrayList、HashMap任意组合的对象
    public <V> V convertFrom(final ByteBuffer... buffers) {
        if (buffers == null || buffers.length == 0) {
            return null;
        }
        return (V) new AnyDecoder(factory).convertFrom(new JsonByteBufferReader(buffers));
    }

    // 返回非null的值是由String、ArrayList、HashMap任意组合的对象
    public <V> V convertFrom(final JsonReader reader) {
        if (reader == null) {
            return null;
        }
        return (V) new AnyDecoder(factory).convertFrom(reader);
    }

    // json数据的数组长度必须和types个数相同
    public Object[] convertFrom(final Type[] types, final String text) {
        if (text == null) {
            return null;
        }
        return new JsonMultiArrayDecoder(getFactory(), types).convertFrom(new JsonReader(text));
    }

    // json数据的数组长度必须和types个数相同
    public Object[] convertFrom(final Type[] types, final byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return convertFrom(types, new String(bytes, StandardCharsets.UTF_8));
    }

    // json数据的数组长度必须和types个数相同
    public Object[] convertFrom(final Type[] types, final byte[] bytes, final int offset, final int length) {
        if (bytes == null) {
            return null;
        }
        return convertFrom(types, new String(bytes, offset, length, StandardCharsets.UTF_8));
    }

    // ------------------------------ convertTo -----------------------------------------------------------
    @Override
    public String convertTo(final Type type, final Object value) {
        if (value == null) {
            return "null";
        }
        JsonCharsWriter writer = pollJsonCharsWriter();
        final Type t = type == null ? value.getClass() : type;
        Encodeable encoder = this.lastEncodeable;
        if (encoder == null || encoder.getType() != t) {
            encoder = factory.loadEncoder(t);
            this.lastEncodeable = encoder;
        }
        if (encoder.specifyable()) {
            writer.specificObjectType(t);
        }
        encoder.convertTo(writer, value);

        String result = writer.toString();
        offerJsonCharsWriter(writer);
        return result;
    }

    @Override
    public byte[] convertToBytes(final Type type, final Object value) {
        if (value == null) {
            return null;
        }
        JsonBytesWriter writer = pollJsonBytesWriter();
        final Type t = type == null ? value.getClass() : type;
        Encodeable encoder = this.lastEncodeable;
        if (encoder == null || encoder.getType() != t) {
            encoder = factory.loadEncoder(t);
            this.lastEncodeable = encoder;
        }
        if (encoder.specifyable()) {
            writer.specificObjectType(t);
        }
        encoder.convertTo(writer, value);

        byte[] result = writer.toBytes();
        offerJsonBytesWriter(writer);
        return result;
    }

    @Override
    public void convertToBytes(final Type type, final Object value, final ConvertBytesHandler handler) {
        JsonBytesWriter writer = pollJsonBytesWriter();
        if (value == null) {
            writer.writeNull();
        } else {
            final Type t = type == null ? value.getClass() : type;
            Encodeable encoder = this.lastEncodeable;
            if (encoder == null || encoder.getType() != t) {
                encoder = factory.loadEncoder(t);
                this.lastEncodeable = encoder;
            }
            if (encoder.specifyable()) {
                writer.specificObjectType(t);
            }
            encoder.convertTo(writer, value);
        }
        writer.completed(handler, offerBytesConsumer);
    }

    @Override
    public void convertToBytes(final ByteArray array, final Type type, final Object value) {
        Objects.requireNonNull(array);
        JsonBytesWriter writer = configWrite(new JsonBytesWriter(features, array));
        if (value == null) {
            writer.writeNull();
        } else {
            final Type t = type == null ? value.getClass() : type;
            Encodeable encoder = this.lastEncodeable;
            if (encoder == null || encoder.getType() != t) {
                encoder = factory.loadEncoder(t);
                this.lastEncodeable = encoder;
            }
            if (encoder.specifyable()) {
                writer.specificObjectType(t);
            }
            encoder.convertTo(writer, value);
        }
        writer.directTo(array);
    }

    public void convertTo(final OutputStream out, final Type type, final Object value) {
        if (value == null) {
            configWrite(new JsonStreamWriter(features, out)).writeNull();
        } else {
            final Type t = type == null ? value.getClass() : type;
            JsonStreamWriter writer = configWrite(new JsonStreamWriter(features, out));
            Encodeable encoder = this.lastEncodeable;
            if (encoder == null || encoder.getType() != t) {
                encoder = factory.loadEncoder(t);
                this.lastEncodeable = encoder;
            }
            if (encoder.specifyable()) {
                writer.specificObjectType(t);
            }
            encoder.convertTo(writer, value);
        }
    }

    @Override
    public ByteBuffer[] convertTo(final Supplier<ByteBuffer> supplier, final Type type, final Object value) {
        Objects.requireNonNull(supplier);
        JsonByteBufferWriter out = configWrite(new JsonByteBufferWriter(features, supplier));
        if (value == null) {
            out.writeNull();
        } else {
            final Type t = type == null ? value.getClass() : type;
            out.specificObjectType(t);
            factory.loadEncoder(t).convertTo(out, value);
        }
        return out.toBuffers();
    }

    @Override
    public void convertTo(final JsonWriter writer, final Type type, final Object value) {
        if (value == null) {
            writer.writeNull();
        } else {
            final Type t = type == null ? value.getClass() : type;
            Encodeable encoder = this.lastEncodeable;
            if (encoder == null || encoder.getType() != t) {
                encoder = factory.loadEncoder(t);
                this.lastEncodeable = encoder;
            }
            if (encoder.specifyable()) {
                writer.specificObjectType(t);
            }
            encoder.convertTo(writer, value);
        }
    }
}
