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
import java.util.function.*;
import org.redkale.convert.*;
import org.redkale.service.RetResult;
import org.redkale.util.*;

/**
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public class JsonConvert extends TextConvert<JsonReader, JsonWriter> {

    public static final Type TYPE_MAP_STRING_STRING = new TypeToken<java.util.HashMap<String, String>>() {
    }.getType();

    public static final Type TYPE_LIST_STRING = new TypeToken<java.util.List<String>>() {
    }.getType();

    public static final Type TYPE_RETRESULT_STRING = new TypeToken<RetResult<String>>() {
    }.getType();

    private final ThreadLocal<JsonBytesWriter> bytesWriterPool = ThreadLocal.withInitial(JsonBytesWriter::new);

    private final Consumer<JsonBytesWriter> offerBytesConsumer = w -> offerJsonBytesWriter(w);

    private final boolean tiny;

    private Encodeable lastConvertEncodeable;

    private Decodeable lastConvertDecodeable;

    protected JsonConvert(JsonFactory factory, boolean tiny) {
        super(factory);
        this.tiny = tiny;
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
        return newConvert(objFieldFunc, null);
    }

    @Override
    public JsonConvert newConvert(final BiFunction<Attribute, Object, Object> objFieldFunc, Function<Object, ConvertField[]> objExtFunc) {
        return new JsonConvert(getFactory(), tiny) {
            @Override
            protected <S extends JsonWriter> S configWrite(S writer) {
                return fieldFunc(writer, objFieldFunc, objExtFunc);
            }
        };
    }

    public JsonConvert newConvert(BiFunction<Object, Object, Object> mapFieldFunc, final BiFunction<Attribute, Object, Object> objFieldFunc, Function<Object, ConvertField[]> objExtFunc) {
        return new JsonConvert(getFactory(), tiny) {
            @Override
            protected <S extends JsonWriter> S configWrite(S writer) {
                return fieldFunc(writer, mapFieldFunc, objFieldFunc, objExtFunc);
            }
        };
    }

    //------------------------------ writer -----------------------------------------------------------
    private JsonBytesWriter pollJsonBytesWriter() {
        JsonBytesWriter writer = bytesWriterPool.get();
        if (writer == null) {
            writer = new JsonBytesWriter();
        } else {
            bytesWriterPool.set(null);
        }
        return configWrite((JsonBytesWriter) writer.tiny(tiny));
    }

    private void offerJsonBytesWriter(final JsonBytesWriter writer) {
        if (writer != null) {
            writer.recycle();
            bytesWriterPool.set(writer);
        }
    }

    //------------------------------ convertFrom -----------------------------------------------------------
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
        Decodeable decoder = this.lastConvertDecodeable;
        if (decoder == null || decoder.getType() != type) {
            decoder = factory.loadDecoder(type);
            this.lastConvertDecodeable = decoder;
        }
        T rs = (T) decoder.convertFrom(new JsonReader(text, offset, length));
        return rs;
    }

    public <T> T convertFrom(final Type type, final InputStream in) {
        if (type == null || in == null) {
            return null;
        }
        Decodeable decoder = this.lastConvertDecodeable;
        if (decoder == null || decoder.getType() != type) {
            decoder = factory.loadDecoder(type);
            this.lastConvertDecodeable = decoder;
        }
        return (T) decoder.convertFrom(new JsonStreamReader(in));
    }

    @Override
    public <T> T convertFrom(final Type type, final ByteBuffer... buffers) {
        if (type == null || buffers == null || buffers.length == 0) {
            return null;
        }
        Decodeable decoder = this.lastConvertDecodeable;
        if (decoder == null || decoder.getType() != type) {
            decoder = factory.loadDecoder(type);
            this.lastConvertDecodeable = decoder;
        }
        return (T) decoder.convertFrom(new JsonByteBufferReader((ConvertMask) null, buffers));
    }

    @Override
    public <T> T convertFrom(final Type type, final ConvertMask mask, final ByteBuffer... buffers) {
        if (type == null || buffers == null || buffers.length == 0) {
            return null;
        }
        Decodeable decoder = this.lastConvertDecodeable;
        if (decoder == null || decoder.getType() != type) {
            decoder = factory.loadDecoder(type);
            this.lastConvertDecodeable = decoder;
        }
        return (T) decoder.convertFrom(new JsonByteBufferReader(mask, buffers));
    }

    public <T> T convertFrom(final Type type, final JsonReader reader) {
        if (type == null) {
            return null;
        }
        Decodeable decoder = this.lastConvertDecodeable;
        if (decoder == null || decoder.getType() != type) {
            decoder = factory.loadDecoder(type);
            this.lastConvertDecodeable = decoder;
        }
        @SuppressWarnings("unchecked")
        T rs = (T) decoder.convertFrom(reader);
        return rs;
    }

    //返回非null的值是由String、ArrayList、HashMap任意组合的对象
    public <V> V convertFrom(final String text) {
        if (text == null) {
            return null;
        }
        return (V) convertFrom(Utility.charArray(text));
    }

    //返回非null的值是由String、ArrayList、HashMap任意组合的对象
    public <V> V convertFrom(final char[] text) {
        if (text == null) {
            return null;
        }
        return (V) convertFrom(text, 0, text.length);
    }

    //返回非null的值是由String、ArrayList、HashMap任意组合的对象
    public <V> V convertFrom(final char[] text, final int offset, final int length) {
        if (text == null) {
            return null;
        }
        //final JsonReader in = readerPool.get();
        //in.setText(text, offset, length);
        Object rs = new AnyDecoder(factory).convertFrom(new JsonReader(text, offset, length));
        //readerPool.accept(in);
        return (V) rs;
    }

    //返回非null的值是由String、ArrayList、HashMap任意组合的对象
    public <V> V convertFrom(final InputStream in) {
        if (in == null) {
            return null;
        }
        return (V) new AnyDecoder(factory).convertFrom(new JsonStreamReader(in));
    }

    //返回非null的值是由String、ArrayList、HashMap任意组合的对象
    public <V> V convertFrom(final ByteBuffer... buffers) {
        if (buffers == null || buffers.length == 0) {
            return null;
        }
        return (V) new AnyDecoder(factory).convertFrom(new JsonByteBufferReader((ConvertMask) null, buffers));
    }

    //返回非null的值是由String、ArrayList、HashMap任意组合的对象
    public <V> V convertFrom(final ConvertMask mask, final ByteBuffer... buffers) {
        if (buffers == null || buffers.length == 0) {
            return null;
        }
        return (V) new AnyDecoder(factory).convertFrom(new JsonByteBufferReader(mask, buffers));
    }

    //返回非null的值是由String、ArrayList、HashMap任意组合的对象
    public <V> V convertFrom(final JsonReader reader) {
        if (reader == null) {
            return null;
        }
        return (V) new AnyDecoder(factory).convertFrom(reader);
    }

    //json数据的数组长度必须和types个数相同
    public Object[] convertFrom(final Type[] types, final String text) {
        if (text == null) {
            return null;
        }
        return new JsonMultiArrayDecoder(getFactory(), types).convertFrom(new JsonReader(text));
    }

    //json数据的数组长度必须和types个数相同
    public Object[] convertFrom(final Type[] types, final byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return convertFrom(types, new String(bytes, StandardCharsets.UTF_8));
    }

    //json数据的数组长度必须和types个数相同
    public Object[] convertFrom(final Type[] types, final byte[] bytes, final int offset, final int length) {
        if (bytes == null) {
            return null;
        }
        return convertFrom(types, new String(bytes, offset, length, StandardCharsets.UTF_8));
    }

    //------------------------------ convertTo -----------------------------------------------------------
    @Override
    public String convertTo(final Object value) {
        if (value == null) {
            return "null";
        }
        return convertTo(value.getClass(), value);
    }

    @Override
    public String convertTo(final Type type, final Object value) {
        if (type == null) {
            return null;
        }
        if (value == null) {
            return "null";
        }
        JsonBytesWriter writer = pollJsonBytesWriter();
        Encodeable encoder = this.lastConvertEncodeable;
        if (encoder == null || encoder.getType() != type) {
            encoder = factory.loadEncoder(type);
            this.lastConvertEncodeable = encoder;
        }
        if (encoder.specifyable()) {
            writer.specificObjectType(type);
        }
        encoder.convertTo(writer, value);

        String result = writer.toString();
        offerJsonBytesWriter(writer);
        return result;
    }

    @Override
    public byte[] convertToBytes(final Object value) {
        if (value == null) {
            return null;
        }
        return convertToBytes(value.getClass(), value);
    }

    @Override
    public byte[] convertToBytes(final Type type, final Object value) {
        if (type == null) {
            return null;
        }
        if (value == null) {
            return null;
        }
        JsonBytesWriter writer = pollJsonBytesWriter();
        Encodeable encoder = this.lastConvertEncodeable;
        if (encoder == null || encoder.getType() != type) {
            encoder = factory.loadEncoder(type);
            this.lastConvertEncodeable = encoder;
        }
        if (encoder.specifyable()) {
            writer.specificObjectType(type);
        }
        encoder.convertTo(writer, value);

        byte[] result = writer.toBytes();
        offerJsonBytesWriter(writer);
        return result;
    }

    @Override
    public void convertToBytes(final Object value, final ConvertBytesHandler handler) {
        convertToBytes(value == null ? null : value.getClass(), value, handler);
    }

    @Override
    public void convertToBytes(final Type type, final Object value, final ConvertBytesHandler handler) {
        JsonBytesWriter writer = pollJsonBytesWriter();
        if (type == null) {
            writer.writeNull();
        } else {
            Encodeable encoder = this.lastConvertEncodeable;
            if (encoder == null || encoder.getType() != type) {
                encoder = factory.loadEncoder(type);
                this.lastConvertEncodeable = encoder;
            }
            if (encoder.specifyable()) {
                writer.specificObjectType(type);
            }
            encoder.convertTo(writer, value);
        }
        writer.completed(handler, offerBytesConsumer);
    }

    @Override
    public void convertToBytes(final ByteArray array, final Object value) {
        convertToBytes(array, value == null ? null : value.getClass(), value);
    }

    @Override
    public void convertToBytes(final ByteArray array, final Type type, final Object value) {
        JsonBytesWriter writer = configWrite(new JsonBytesWriter(tiny, array));
        if (type == null) {
            writer.writeNull();
        } else {
            Encodeable encoder = this.lastConvertEncodeable;
            if (encoder == null || encoder.getType() != type) {
                encoder = factory.loadEncoder(type);
                this.lastConvertEncodeable = encoder;
            }
            if (encoder.specifyable()) {
                writer.specificObjectType(type);
            }
            encoder.convertTo(writer, value);
        }
        writer.directTo(array);
    }

    public void convertTo(final OutputStream out, final Object value) {
        if (value == null) {
            configWrite(new JsonStreamWriter(tiny, out)).writeNull();
        } else {
            convertTo(out, value.getClass(), value);
        }
    }

    public void convertTo(final OutputStream out, final Type type, final Object value) {
        if (type == null) {
            return;
        }
        if (value == null) {
            configWrite(new JsonStreamWriter(tiny, out)).writeNull();
        } else {
            JsonStreamWriter writer = configWrite(new JsonStreamWriter(tiny, out));
            Encodeable encoder = this.lastConvertEncodeable;
            if (encoder == null || encoder.getType() != type) {
                encoder = factory.loadEncoder(type);
                this.lastConvertEncodeable = encoder;
            }
            if (encoder.specifyable()) {
                writer.specificObjectType(type);
            }
            encoder.convertTo(writer, value);
        }
    }

    @Override
    public ByteBuffer[] convertTo(final Supplier<ByteBuffer> supplier, final Object value) {
        if (supplier == null) {
            return null;
        }
        JsonByteBufferWriter out = configWrite(new JsonByteBufferWriter(tiny, supplier));
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
        JsonByteBufferWriter out = configWrite(new JsonByteBufferWriter(tiny, supplier));
        if (value == null) {
            out.writeNull();
        } else {
            out.specificObjectType(type);
            factory.loadEncoder(type).convertTo(out, value);
        }
        return out.toBuffers();
    }

    @Override
    public void convertTo(final JsonWriter writer, final Object value) {
        if (value == null) {
            writer.writeNull();
        } else {
            Class type = value.getClass();
            Encodeable encoder = this.lastConvertEncodeable;
            if (encoder == null || encoder.getType() != type) {
                encoder = factory.loadEncoder(type);
                this.lastConvertEncodeable = encoder;
            }
            if (encoder.specifyable()) {
                writer.specificObjectType(type);
            }
            encoder.convertTo(writer, value);
        }
    }

    @Override
    public void convertTo(final JsonWriter writer, final Type type, final Object value) {
        if (type == null) {
            return;
        }
        if (value == null) {
            writer.writeNull();
        } else {
            Encodeable encoder = this.lastConvertEncodeable;
            if (encoder == null || encoder.getType() != type) {
                encoder = factory.loadEncoder(type);
                this.lastConvertEncodeable = encoder;
            }
            if (encoder.specifyable()) {
                writer.specificObjectType(type);
            }
            encoder.convertTo(writer, value);
        }
    }

}
