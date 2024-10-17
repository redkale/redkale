/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.convert.json;

import java.lang.reflect.Type;
import org.redkale.annotation.ClassDepends;
import org.redkale.convert.*;

/**
 * writeTo系列的方法输出的字符不能含特殊字符
 *
 * <p>详情见: https://redkale.org
 *
 * @author zhangjx
 */
public abstract class JsonWriter extends Writer {

    protected static final int DEFAULT_SIZE = Integer.getInteger(
            "redkale.convert.json.writer.buffer.defsize",
            Integer.getInteger("redkale.convert.writer.buffer.defsize", 1024));

    protected static final byte BYTE_NEGATIVE = '-';

    protected static final byte BYTE_COMMA = ',';

    protected static final byte BYTE_COLON = ':';

    protected static final byte BYTE_LBRACE = '{';

    protected static final byte BYTE_RBRACE = '}';

    protected static final byte BYTE_LBRACKET = '[';

    protected static final byte BYTE_RBRACKET = ']';

    protected static final byte BYTE_DQUOTE = '"';

    protected JsonWriter() {
        this.features = JsonFactory.root().getFeatures();
    }

    @Override
    public JsonWriter withFeatures(int features) {
        return (JsonWriter) super.withFeatures(features);
    }

    @ClassDepends
    public boolean isExtFuncEmpty() {
        return this.objExtFunc == null && this.objFieldFunc == null;
    }

    public boolean charsMode() {
        return false;
    }

    // -----------------------------------------------------------------------
    public abstract void writeTo(final char ch); // 只能是 0 - 127 的字符

    public abstract void writeTo(final char[] cs, final int start, final int len); // 只能是 0 - 127 的字符

    public abstract void writeTo(final byte ch); // 只能是 0 - 127 的字符

    public abstract void writeTo(final byte[] bs, final int start, final int len); // 只能是 0 - 127 的字符
    /**
     * <b>注意：</b> 该String值不能为null且不会进行转义， 只用于不含需要转义字符的字符串，例如enum、double、BigInteger、BigDecimal转换的String
     *
     * @param quote 是否加双引号
     * @param value 非null且不含需要转义的字符的String值
     */
    @ClassDepends
    public abstract void writeLatin1To(final boolean quote, final String value);

    @Override
    @ClassDepends
    public abstract void writeNull();

    // ---------------------------- writeFieldXXXValue 调用前不需要判断值是否为null ----------------------------
    @ClassDepends
    public abstract boolean writeFieldBooleanValue(Object fieldArray, boolean comma, boolean value);

    @ClassDepends
    public abstract boolean writeFieldByteValue(Object fieldArray, boolean comma, byte value);

    @ClassDepends
    public abstract boolean writeFieldShortValue(Object fieldArray, boolean comma, short value);

    @ClassDepends
    public final boolean writeFieldCharValue(Object fieldArray, boolean comma, char value) {
        return writeFieldIntValue(fieldArray, comma, value);
    }

    @ClassDepends
    public abstract boolean writeFieldIntValue(Object fieldArray, boolean comma, int value);

    @ClassDepends
    public final boolean writeFieldFloatValue(Object fieldArray, boolean comma, float value) {
        return writeFieldLatin1Value(fieldArray, comma, false, String.valueOf(value));
    }

    @ClassDepends
    public abstract boolean writeFieldLongValue(Object fieldArray, boolean comma, long value);

    @ClassDepends
    public final boolean writeFieldDoubleValue(Object fieldArray, boolean comma, double value) {
        return writeFieldLatin1Value(fieldArray, comma, false, String.valueOf(value));
    }

    @ClassDepends
    public final boolean writeFieldBooleanValue(Object fieldArray, boolean comma, Boolean value) {
        if (value == null) {
            if (nullable()) {
                writeFieldNull(fieldArray, comma);
                return true;
            } else {
                return comma;
            }
        }
        return writeFieldBooleanValue(fieldArray, comma, value.booleanValue());
    }

    @ClassDepends
    public final boolean writeFieldByteValue(Object fieldArray, boolean comma, Byte value) {
        if (value == null) {
            if (nullable()) {
                writeFieldNull(fieldArray, comma);
                return true;
            } else {
                return comma;
            }
        }
        return writeFieldByteValue(fieldArray, comma, value.byteValue());
    }

    @ClassDepends
    public final boolean writeFieldShortValue(Object fieldArray, boolean comma, Short value) {
        if (value == null) {
            if (nullable()) {
                writeFieldNull(fieldArray, comma);
                return true;
            } else {
                return comma;
            }
        }
        return writeFieldShortValue(fieldArray, comma, value.shortValue());
    }

    @ClassDepends
    public final boolean writeFieldCharValue(Object fieldArray, boolean comma, Character value) {
        if (value == null) {
            if (nullable()) {
                writeFieldNull(fieldArray, comma);
                return true;
            } else {
                return comma;
            }
        }
        return writeFieldIntValue(fieldArray, comma, value.charValue());
    }

    @ClassDepends
    public final boolean writeFieldIntValue(Object fieldArray, boolean comma, Integer value) {
        if (value == null) {
            if (nullable()) {
                writeFieldNull(fieldArray, comma);
                return true;
            } else {
                return comma;
            }
        }
        return writeFieldIntValue(fieldArray, comma, value.intValue());
    }

    @ClassDepends
    public final boolean writeFieldFloatValue(Object fieldArray, boolean comma, Float value) {
        if (value == null) {
            if (nullable()) {
                writeFieldNull(fieldArray, comma);
                return true;
            } else {
                return comma;
            }
        }
        return writeFieldLatin1Value(fieldArray, comma, false, String.valueOf(value));
    }

    @ClassDepends
    public final boolean writeFieldLongValue(Object fieldArray, boolean comma, Long value) {
        if (value == null) {
            if (nullable()) {
                writeFieldNull(fieldArray, comma);
                return true;
            } else {
                return comma;
            }
        }
        return writeFieldLongValue(fieldArray, comma, value.longValue());
    }

    @ClassDepends
    public final boolean writeFieldDoubleValue(Object fieldArray, boolean comma, Double value) {
        if (value == null) {
            if (nullable()) {
                writeFieldNull(fieldArray, comma);
                return true;
            } else {
                return comma;
            }
        }
        return writeFieldLatin1Value(fieldArray, comma, false, String.valueOf(value));
    }

    @ClassDepends
    public final boolean writeFieldStandardStringValue(Object fieldArray, boolean comma, String value) {
        return writeFieldLatin1Value(fieldArray, comma, true, value);
    }

    @ClassDepends
    public abstract boolean writeFieldStringValue(Object fieldArray, boolean comma, String value);

    @ClassDepends
    public abstract boolean writeFieldObjectValue(
            Object fieldArray, boolean comma, Encodeable encodeable, Object value);

    protected abstract void writeFieldNull(Object fieldArray, boolean comma);

    protected abstract boolean writeFieldLatin1Value(Object fieldArray, boolean comma, boolean quote, String value);

    // ---------------------------- writeFieldXXXValue 主要供JsonDynEncoder使用 ----------------------------

    @Override
    @ClassDepends
    public abstract void writeBoolean(boolean value);

    @Override
    @ClassDepends
    public abstract void writeInt(int value);

    @Override
    @ClassDepends
    public abstract void writeLong(long value);

    @Override
    @ClassDepends
    public abstract void writeString(String value);

    @Override
    @ClassDepends
    public final void writeStandardString(String value) {
        writeLatin1To(true, value);
    }

    @Override // 只容许JsonBytesWriter重写此方法
    public void writeField(EnMember member, String fieldName, Type fieldType, int fieldPos) {
        if (this.comma) {
            writeTo(BYTE_COMMA);
        }
        if (member != null) {
            if (charsMode()) {
                char[] chs = member.getJsonFieldNameChars();
                writeTo(chs, 0, chs.length);
            } else {
                writeTo(member.getJsonFieldNameBytes());
            }
        } else {
            writeLatin1To(true, fieldName);
            writeTo(BYTE_COLON);
        }
    }

    // ----------------------------------------------------------------------------------------------
    public final void writeTo(final char... cs) { // 只能是 0 - 127 的字符
        writeTo(cs, 0, cs.length);
    }

    public final void writeTo(final byte[] bs) { // 只能是 0 - 127 的字符
        writeTo(bs, 0, bs.length);
    }

    @Override
    public final void writeByteArray(byte[] values) {
        if (values == null) {
            writeNull();
            return;
        }
        writeArrayB(values.length, null, values);
        boolean flag = false;
        for (byte v : values) {
            if (flag) {
                writeArrayMark();
            }
            writeByte(v);
            flag = true;
        }
        writeArrayE();
    }

    @Override
    public final void writeByte(byte value) {
        writeInt(value);
    }

    @Override
    public final void writeChar(char value) {
        writeInt(value);
    }

    @Override
    public final void writeShort(short value) {
        writeInt(value);
    }

    @Override
    public final void writeFloat(float value) {
        writeLatin1To(false, String.valueOf(value));
    }

    @Override
    public final void writeDouble(double value) {
        writeLatin1To(false, String.valueOf(value));
    }

    @Override
    public final void writeObjectB(Object obj) {
        super.writeObjectB(obj);
        writeTo(BYTE_LBRACE);
    }

    @Override
    public final void writeObjectE(Object obj) {
        writeTo(BYTE_RBRACE);
    }

    @Override
    public final void writeArrayB(int size, Encodeable componentEncoder, Object obj) {
        writeTo(BYTE_LBRACKET);
    }

    @Override
    public final void writeArrayMark() {
        writeTo(BYTE_COMMA);
    }

    @Override
    public final void writeArrayE() {
        writeTo(BYTE_RBRACKET);
    }

    @Override
    public final void writeMapB(int size, Encodeable keyEncoder, Encodeable valueEncoder, Object obj) {
        writeTo(BYTE_LBRACE);
    }

    @Override
    public final void writeMapMark() {
        writeTo(BYTE_COLON);
    }

    @Override
    public final void writeMapE() {
        writeTo(BYTE_RBRACE);
    }

    static final char[] DigitTens = {
        '0', '0', '0', '0', '0', '0', '0', '0', '0', '0',
        '1', '1', '1', '1', '1', '1', '1', '1', '1', '1',
        '2', '2', '2', '2', '2', '2', '2', '2', '2', '2',
        '3', '3', '3', '3', '3', '3', '3', '3', '3', '3',
        '4', '4', '4', '4', '4', '4', '4', '4', '4', '4',
        '5', '5', '5', '5', '5', '5', '5', '5', '5', '5',
        '6', '6', '6', '6', '6', '6', '6', '6', '6', '6',
        '7', '7', '7', '7', '7', '7', '7', '7', '7', '7',
        '8', '8', '8', '8', '8', '8', '8', '8', '8', '8',
        '9', '9', '9', '9', '9', '9', '9', '9', '9', '9'
    };

    static final char[] DigitOnes = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'
    };

    protected static int stringSize(int x) {
        int d = 1;
        if (x >= 0) {
            d = 0;
            x = -x;
        }
        int p = -10;
        for (int i = 1; i < 10; i++) {
            if (x > p) return i + d;
            p = 10 * p;
        }
        return 10 + d;
    }

    protected static int stringSize(long x) {
        int d = 1;
        if (x >= 0) {
            d = 0;
            x = -x;
        }
        long p = -10;
        for (int i = 1; i < 19; i++) {
            if (x > p) return i + d;
            p = 10 * p;
        }
        return 19 + d;
    }
}
